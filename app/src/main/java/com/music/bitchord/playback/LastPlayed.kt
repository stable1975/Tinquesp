package com.music.bitchord.playback

import android.content.Context
import android.content.SharedPreferences
import java.io.File
import java.util.concurrent.Executors
import com.music.bitchord.data.model.Song
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A queue snapshot for restoring playback after process death.
 *
 * Queue contents are serialized only when Media3 reports that the playlist
 * changed, and written to their own file off the main thread: a whole-library
 * queue is a few megabytes of JSON, which must never be rewritten along with
 * the position. The frequently changing index and position are stored
 * separately as primitives in preferences, so progress sampling never walks or
 * serializes the song list.
 */
object LastPlayed {

    class Snapshot(val songs: List<Song>, val index: Int, val positionMs: Long)

    private lateinit var prefs: SharedPreferences
    private lateinit var queueFile: File
    private val json = Json { ignoreUnknownKeys = true }

    /** One writer, so a queue saved later can never be overtaken by an earlier one. */
    private val writer = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "last-played-writer").apply { isDaemon = true }
    }

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        queueFile = File(context.filesDir, QUEUE_FILE_NAME)
    }

    /**
     * What is kept for a cold start: up to [PERSIST_BEHIND] tracks before the
     * current one and [PERSIST_AHEAD] after it — in practice the whole queue,
     * including a queue made of an entire library.
     */
    internal fun persistedWindow(size: Int, index: Int): IntRange {
        if (size <= 0) return IntRange.EMPTY
        val safeIndex = index.coerceIn(0, size - 1)
        val start = (safeIndex - PERSIST_BEHIND).coerceAtLeast(0)
        return start until (safeIndex + 1 + PERSIST_AHEAD).coerceAtMost(size)
    }

    private fun writeQueueFile(encoded: String) {
        runCatching {
            val temp = File(queueFile.parentFile, "$QUEUE_FILE_NAME.tmp")
            temp.writeText(encoded)
            if (!temp.renameTo(queueFile)) {
                queueFile.writeText(encoded)
                temp.delete()
            }
        }
    }

    internal fun window(size: Int, index: Int): IntRange {
        if (size <= 0) return IntRange.EMPTY
        val safeIndex = index.coerceIn(0, size - 1)
        val start = (safeIndex - MAX_QUEUE_HISTORY).coerceAtLeast(0)
        return start until (safeIndex + 1 + KEEP_AHEAD).coerceAtMost(size)
    }

    /** Persist the queue around the current track (see [persistedWindow]). */
    fun saveQueue(songs: List<Song>, index: Int) {
        if (songs.isEmpty()) {
            clear()
            return
        }
        val safeIndex = index.coerceIn(songs.indices)
        val bounds = persistedWindow(songs.size, safeIndex)
        val tracks = songs.subList(bounds.first, bounds.last + 1)
        val storedIndex = safeIndex - bounds.first
        prefs.edit()
            .putBoolean(KEY_HAS_QUEUE, true)
            .remove(KEY_QUEUE)
            .putInt(KEY_INDEX, storedIndex)
            .apply()
        writer.execute {
            val encoded = runCatching {
                json.encodeToString(StoredQueue.serializer(), StoredQueue(tracks.map { StoredTrack.from(it) }))
            }.getOrNull() ?: return@execute
            writeQueueFile(encoded)
        }
    }

    /**
     * Atomically replaces the restart snapshot and waits until it reaches disk.
     * Used at an explicit queue boundary, where restoring the queue from before
     * that boundary would be worse than the small cost of a synchronous write.
     */
    fun saveQueueImmediately(songs: List<Song>, index: Int, positionMs: Long) {
        if (songs.isEmpty()) {
            clearImmediately()
            return
        }
        val safeIndex = index.coerceIn(songs.indices)
        val bounds = persistedWindow(songs.size, safeIndex)
        val tracks = songs.subList(bounds.first, bounds.last + 1)
        val encoded = runCatching {
            json.encodeToString(StoredQueue.serializer(), StoredQueue(tracks.map { StoredTrack.from(it) }))
        }.getOrNull() ?: return
        runCatching { writer.submit { writeQueueFile(encoded) }.get() }
        prefs.edit()
            .putBoolean(KEY_HAS_QUEUE, true)
            .remove(KEY_QUEUE)
            .putInt(KEY_INDEX, safeIndex - bounds.first)
            .putLong(KEY_POSITION, positionMs.coerceAtLeast(0L))
            .commit()
    }

    /** Update only cheap scalar state; this performs no queue conversion or JSON work. */
    fun savePlaybackState(index: Int, positionMs: Long) {
        if (!prefs.contains(KEY_HAS_QUEUE) && !prefs.contains(KEY_QUEUE)) return
        prefs.edit()
            .putInt(KEY_INDEX, index.coerceAtLeast(0))
            .putLong(KEY_POSITION, positionMs.coerceAtLeast(0L))
            .apply()
    }

    fun load(): Snapshot? {
        // The queue's own file, or the preferences string an older version wrote.
        val raw = runCatching { queueFile.takeIf { it.isFile }?.readText() }.getOrNull()
            ?: prefs.getString(KEY_QUEUE, null)
            ?: return null
        val stored = runCatching { json.decodeFromString<StoredQueue>(raw) }.getOrNull()
            ?: return null
        if (stored.tracks.isEmpty()) return null
        val songs = stored.tracks.map(StoredTrack::toSong)
        val index = if (prefs.contains(KEY_INDEX)) {
            prefs.getInt(KEY_INDEX, 0)
        } else {
            stored.legacyIndex ?: 0
        }
        val positionMs = if (prefs.contains(KEY_POSITION)) {
            prefs.getLong(KEY_POSITION, 0L)
        } else {
            stored.legacyPositionMs ?: 0L
        }
        return Snapshot(
            songs = songs,
            index = index.coerceIn(songs.indices),
            positionMs = positionMs.coerceAtLeast(0L),
        )
    }

    fun clear() {
        prefs.edit().clear().apply()
        writer.execute { runCatching { queueFile.delete() } }
    }

    /** Remove a stale queue before another one is installed. */
    fun clearImmediately() {
        runCatching { writer.submit { runCatching { queueFile.delete() } }.get() }
        prefs.edit().clear().commit()
    }

    @Serializable
    private data class StoredQueue(
        val tracks: List<StoredTrack>,
        /** Compatibility with queue snapshots written before scalar state was separated. */
        val index: Int? = null,
        /** Compatibility with queue snapshots written before scalar state was separated. */
        val positionMs: Long? = null,
    ) {
        val legacyIndex: Int? get() = index
        val legacyPositionMs: Long? get() = positionMs
    }

    @Serializable
    private data class StoredTrack(
        val id: String,
        val title: String,
        val artist: String,
        val artwork: String? = null,
        val auto: Boolean = false,
        val local: String? = null,
        val path: String? = null,
        val duration: String? = null,
        val album: String? = null,
        val explicit: Boolean? = null,
        val video: Boolean = false,
    ) {
        fun toSong() = Song(
            videoId = id,
            title = title,
            artist = artist,
            thumbnailUrl = artwork,
            durationText = duration,
            albumName = album,
            isExplicit = explicit,
            isVideo = video,
            fromAutoplay = auto,
            localUri = local,
            localPath = path,
        )

        companion object {
            fun from(song: Song) = StoredTrack(
                id = song.videoId,
                title = song.title,
                artist = song.artist,
                artwork = song.thumbnailUrl,
                auto = song.fromAutoplay,
                local = song.localUri,
                path = song.localPath,
                duration = song.durationText,
                album = song.albumName,
                explicit = song.isExplicit,
                video = song.isVideo,
            )
        }
    }

    private const val KEEP_AHEAD = 50
    private const val PERSIST_BEHIND = 5_000
    private const val PERSIST_AHEAD = 5_000
    private const val PREFS_NAME = "bitchord_last_played"
    private const val QUEUE_FILE_NAME = "last_played_queue.json"
    private const val KEY_HAS_QUEUE = "has_queue"
    private const val KEY_QUEUE = "queue"
    private const val KEY_INDEX = "index"
    private const val KEY_POSITION = "position"
}
