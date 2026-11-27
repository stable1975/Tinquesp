package com.music.bitchord.feature.localmusic.ui.components

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.music.bitchord.R
import com.music.bitchord.data.model.CARD_ART_PX
import com.music.bitchord.data.model.HEADER_ART_PX
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.artworkAt
import com.music.bitchord.data.model.isSameTrackAs
import com.music.bitchord.data.stats.HeatStore
import com.music.bitchord.data.stats.heatKey
import com.music.bitchord.feature.artistimage.model.ArtistImage
import com.music.bitchord.feature.library.data.LibraryEntryStore
import com.music.bitchord.feature.library.data.LibraryEntryType
import com.music.bitchord.feature.library.data.albumKey
import com.music.bitchord.feature.library.data.artistKey
import com.music.bitchord.ui.components.PAGE_GUTTER
import com.music.bitchord.ui.components.thumbnailBorder
import com.music.bitchord.ui.icons.BitChordIcons
import com.music.bitchord.ui.theme.rememberArtworkPaletteOf

/** An album as the artist page shows it (the screen's own entry type is private). */
data class ArtistHeroAlbum(
    val key: String,
    val title: String,
    val subtitle: String,
    val artworkUrl: String?,
    val songs: List<Song>,
)

private val TOP_SONG_ROW_HEIGHT = 72.dp
private const val TOP_SONGS_PER_PAGE = 3
private const val TOP_SONGS_MAX = 12

/**
 * The artist page: a full-bleed portrait that fades into the page, the artist's
 * name, shuffle / play / favourite, the latest album, the top songs in pages of
 * three, and the albums. Artist portraits are reused wherever an album has no
 * cover of its own.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArtistHeroPage(
    artist: String,
    songs: List<Song>,
    albums: List<ArtistHeroAlbum>,
    currentSong: Song?,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onSongClick: (List<Song>, Int) -> Unit,
    onSongLongPress: (Song) -> Unit,
    onSongMore: (Song) -> Unit,
    onAlbumClick: (ArtistHeroAlbum) -> Unit,
    onOpenFolders: (() -> Unit)?,
    contentPadding: PaddingValues,
    onArtworkPaletteChanged: ((com.music.bitchord.ui.theme.ArtworkPalette?) -> Unit)?,
    allowRemoteArtwork: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val fallbackArt = remember(songs) { songs.firstNotNullOfOrNull { it.thumbnailUrl } }
    val artistModel = remember(artist, fallbackArt, allowRemoteArtwork) {
        ArtistImage(name = artist, fallbackUrl = fallbackArt, isLarge = true, allowOnlineLookup = allowRemoteArtwork)
    }
    // The page takes its colours from the portrait it actually shows (not from
    // an album sleeve), so every artist gets a tint that matches their own photo.
    val palette = rememberArtworkPaletteOf(
        key = "artist-hero::${artist.trim().lowercase()}|${fallbackArt.orEmpty()}|$allowRemoteArtwork",
        model = artistModel,
        tintFromTone = true,
    )
    // The app bar floats over the portrait in the artwork's colour, as it does
    // on an album page.
    LaunchedEffect(palette.background) { onArtworkPaletteChanged?.invoke(palette) }
    DisposableEffect(Unit) { onDispose { onArtworkPaletteChanged?.invoke(null) } }

    val heat by HeatStore.info.collectAsStateWithLifecycle()
    val topSongs = remember(songs, heat) {
        songs.sortedByDescending { heat[it.heatKey()]?.plays ?: 0 }.take(TOP_SONGS_MAX)
    }
    // The Top songs title opens the whole catalogue in place, in the same order.
    var topExpanded by rememberSaveable(artist) { mutableStateOf(false) }
    val allSongsByPlays = remember(songs, heat) {
        songs.sortedByDescending { heat[it.heatKey()]?.plays ?: 0 }
    }
    // "Show all" on Albums opens the whole list in place, in two columns.
    var albumsExpanded by rememberSaveable(artist) { mutableStateOf(false) }
    val latestAlbum = remember(albums) {
        albums.maxByOrNull { album ->
            album.songs.maxOfOrNull { it.localDateAddedSeconds ?: 0L } ?: 0L
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().background(palette.wash)) {
        val heroHeight = (maxWidth * 1.1f).coerceAtMost(maxHeight * 0.52f)
        val pageWidth = maxWidth - 64.dp

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 24.dp),
        ) {
            // ── Portrait + name ─────────────────────────────────────────
            item(key = "hero") {
                Box(Modifier.fillMaxWidth().height(heroHeight)) {
                    AsyncImage(
                        model = artistModel,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().background(palette.elevated),
                    )
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.verticalGradient(
                                0.00f to Color.Black.copy(alpha = 0.30f),
                                0.22f to Color.Transparent,
                                0.50f to Color.Transparent,
                                0.78f to palette.wash.copy(alpha = 0.75f),
                                1.00f to palette.wash,
                            ),
                        ),
                    )
                    Text(
                        text = artist,
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = palette.onBackground,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 24.dp, vertical = 6.dp),
                    )
                }
            }

            // ── Shuffle / Play / Favourite ──────────────────────────────
            item(key = "actions") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(36.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HeroCircleButton(
                        icon = Icons.Rounded.Shuffle,
                        description = stringResource(R.string.shuffle),
                        size = 56.dp,
                        onClick = onShuffle,
                    )
                    HeroCircleButton(
                        icon = Icons.Rounded.PlayArrow,
                        description = stringResource(R.string.play),
                        size = 76.dp,
                        solid = true,
                        onClick = onPlay,
                    )
                    ArtistFavouriteButton(artist = artist, artworkUrl = fallbackArt)
                }
            }

            // ── Latest album ────────────────────────────────────────────
            if (latestAlbum != null) {
                item(key = "latest") {
                    LatestAlbumCard(
                        album = latestAlbum,
                        artist = artist,
                        container = palette.elevated.copy(alpha = 0.55f),
                        textColor = palette.onBackground,
                        subtextColor = palette.onBackgroundVariant,
                        onClick = { onAlbumClick(latestAlbum) },
                        modifier = Modifier.padding(horizontal = PAGE_GUTTER + 6.dp),
                    )
                }
            }

            // ── Top songs ───────────────────────────────────────────────
            if (topSongs.isNotEmpty()) {
                item(key = "top_title") {
                    val canExpand = songs.size > TOP_SONGS_PER_PAGE
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .then(if (canExpand) Modifier.clickable { topExpanded = !topExpanded } else Modifier)
                            .padding(end = PAGE_GUTTER),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.weight(1f)) {
                            SectionTitle(stringResource(R.string.top_songs), palette.onBackground)
                        }
                        if (canExpand) {
                            Text(
                                text = stringResource(if (topExpanded) R.string.artist_show_less else R.string.show_all),
                                style = MaterialTheme.typography.labelLarge,
                                color = palette.accent,
                                modifier = Modifier.padding(top = 14.dp),
                            )
                        }
                    }
                }
                if (!topExpanded) {
                    item(key = "top_songs") {
                        val pages = remember(topSongs) { topSongs.chunked(TOP_SONGS_PER_PAGE) }
                        HorizontalPager(
                            state = rememberPagerState { pages.size },
                            pageSize = if (pages.size > 1) PageSize.Fixed(pageWidth) else PageSize.Fill,
                            pageSpacing = 12.dp,
                            contentPadding = PaddingValues(horizontal = PAGE_GUTTER + 6.dp),
                            verticalAlignment = Alignment.Top,
                        ) { page ->
                            Column(Modifier.fillMaxWidth()) {
                                pages[page].forEach { song ->
                                    TopSongRow(
                                        song = song,
                                        isCurrent = song.isSameTrackAs(currentSong),
                                        titleColor = palette.onBackground,
                                        subtitleColor = palette.onBackgroundVariant,
                                        accent = palette.accent,
                                        onClick = {
                                            val index = songs.indexOfFirst { it.isSameTrackAs(song) }.coerceAtLeast(0)
                                            onSongClick(songs, index)
                                        },
                                        onLongPress = { onSongLongPress(song) },
                                        onMore = { onSongMore(song) },
                                    )
                                }
                            }
                        }
                    }
                } else {
                    items(allSongsByPlays, key = { "top_${it.localUri ?: it.videoId}" }) { song ->
                        Box(Modifier.padding(horizontal = PAGE_GUTTER + 6.dp)) {
                            TopSongRow(
                                song = song,
                                isCurrent = song.isSameTrackAs(currentSong),
                                titleColor = palette.onBackground,
                                subtitleColor = palette.onBackgroundVariant,
                                accent = palette.accent,
                                onClick = {
                                    val index = songs.indexOfFirst { it.isSameTrackAs(song) }.coerceAtLeast(0)
                                    onSongClick(songs, index)
                                },
                                onLongPress = { onSongLongPress(song) },
                                onMore = { onSongMore(song) },
                            )
                        }
                    }
                }
            }

            // ── Albums ──────────────────────────────────────────────────
            if (albums.isNotEmpty()) {
                item(key = "albums_title") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { albumsExpanded = !albumsExpanded }
                            .padding(end = PAGE_GUTTER),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.weight(1f)) {
                            SectionTitle(stringResource(R.string.albums), palette.onBackground)
                        }
                        Text(
                            text = stringResource(if (albumsExpanded) R.string.artist_show_less else R.string.show_all),
                            style = MaterialTheme.typography.labelLarge,
                            color = palette.accent,
                            modifier = Modifier.padding(top = 14.dp),
                        )
                    }
                }
                if (!albumsExpanded) {
                    item(key = "albums_row") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = PAGE_GUTTER + 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            items(albums, key = { it.key }) { album ->
                                AlbumCard(
                                    album = album,
                                    textColor = palette.onBackground,
                                    subtextColor = palette.onBackgroundVariant,
                                    onClick = { onAlbumClick(album) },
                                )
                            }
                        }
                    }
                } else {
                    val albumRows = albums.chunked(2)
                    items(albumRows.size, key = { "albums_grid_${albumRows[it].first().key}" }) { rowIndex ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = PAGE_GUTTER + 6.dp)
                                .padding(bottom = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            albumRows[rowIndex].forEach { album ->
                                AlbumGridCard(
                                    album = album,
                                    textColor = palette.onBackground,
                                    subtextColor = palette.onBackgroundVariant,
                                    onClick = { onAlbumClick(album) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            // Keep a lone last album at column width.
                            if (albumRows[rowIndex].size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }

            // ── Folders (kept from the old Songs / Albums / Folders switch) ──
            if (onOpenFolders != null) {
                item(key = "folders") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = PAGE_GUTTER + 6.dp, vertical = 20.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(palette.elevated.copy(alpha = 0.45f))
                            .clickable(onClick = onOpenFolders)
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Folder, null, tint = palette.onBackgroundVariant)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            stringResource(R.string.folders),
                            style = MaterialTheme.typography.titleMedium,
                            color = palette.onBackground,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = palette.onBackgroundVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        color = color,
        modifier = Modifier.padding(start = PAGE_GUTTER + 6.dp, top = 26.dp, bottom = 12.dp),
    )
}

/** A round translucent button; [solid] is the white Play. */
@Composable
private fun HeroCircleButton(
    icon: ImageVector,
    description: String,
    size: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    solid: Boolean = false,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (solid) Color.White else Color.White.copy(alpha = 0.14f))
            .then(
                if (solid) Modifier else Modifier.border(1.dp, Color.White.copy(alpha = 0.28f), CircleShape),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (solid) Color.Black else Color.White,
            modifier = Modifier.size(if (solid) 40.dp else 26.dp),
        )
    }
}

/** The star: adds the artist to the library, and takes it back out. */
@Composable
private fun ArtistFavouriteButton(artist: String, artworkUrl: String?) {
    val context = LocalContext.current
    val entries by LibraryEntryStore.entries.collectAsStateWithLifecycle()
    val key = artistKey(artist)
    val inLibrary = remember(entries, key) {
        entries.any { it.type == LibraryEntryType.ARTIST && it.key == key }
    }
    HeroCircleButton(
        icon = if (inLibrary) BitChordIcons.StarFilled else BitChordIcons.Star,
        description = stringResource(if (inLibrary) R.string.remove_from_library else R.string.add_to_library),
        size = 56.dp,
        onClick = {
            if (inLibrary) {
                LibraryEntryStore.remove(LibraryEntryType.ARTIST, key)
                Toast.makeText(context, R.string.removed_from_library, Toast.LENGTH_SHORT).show()
            } else {
                LibraryEntryStore.addArtist(artist, artworkUrl)
                Toast.makeText(context, R.string.added_to_library, Toast.LENGTH_SHORT).show()
            }
        },
    )
}

/** An album cover; with none of its own, the artist's portrait stands in. */
@Composable
private fun AlbumCover(
    album: ArtistHeroAlbum,
    modifier: Modifier,
    shape: RoundedCornerShape,
) {
    Box(
        modifier = modifier.clip(shape).thumbnailBorder(shape).background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.Album,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(36.dp),
        )
        album.artworkUrl?.takeIf { it.isNotBlank() }?.artworkAt(CARD_ART_PX)?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun LatestAlbumCard(
    album: ArtistHeroAlbum,
    artist: String,
    container: Color,
    textColor: Color,
    subtextColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val entries by LibraryEntryStore.entries.collectAsStateWithLifecycle()
    val key = albumKey(album.title, artist)
    val inLibrary = remember(entries, key) {
        entries.any { it.type == LibraryEntryType.ALBUM && it.key == key }
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(container)
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(32.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AlbumCover(album, Modifier.size(96.dp), RoundedCornerShape(18.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.artist_latest_album),
                style = MaterialTheme.typography.bodyMedium,
                color = subtextColor,
                maxLines = 1,
            )
            Text(
                text = album.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = textColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = album.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = subtextColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .border(1.dp, Color.White.copy(alpha = 0.28f), CircleShape)
                .clickable {
                    if (inLibrary) {
                        LibraryEntryStore.remove(LibraryEntryType.ALBUM, key)
                        Toast.makeText(context, R.string.removed_from_library, Toast.LENGTH_SHORT).show()
                    } else {
                        LibraryEntryStore.addAlbum(album.title, artist, album.artworkUrl)
                        Toast.makeText(context, R.string.added_to_library, Toast.LENGTH_SHORT).show()
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (inLibrary) Icons.Rounded.Check else Icons.Rounded.Add,
                contentDescription = stringResource(
                    if (inLibrary) R.string.remove_from_library else R.string.add_to_library,
                ),
                tint = Color.White,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TopSongRow(
    song: Song,
    isCurrent: Boolean,
    titleColor: Color,
    subtitleColor: Color,
    accent: Color,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onMore: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(TOP_SONG_ROW_HEIGHT)
            .combinedClickable(onClick = onClick, onLongClick = onLongPress),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(62.dp).clip(shape).thumbnailBorder(shape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.MusicNote, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            AsyncImage(
                model = song.thumbnailUrl?.takeIf { it.isNotBlank() }?.artworkAt(CARD_ART_PX),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (isCurrent) accent else titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = song.artist.ifBlank { stringResource(R.string.unknown_artist) },
                style = MaterialTheme.typography.bodyMedium,
                color = subtitleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onMore),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.more), tint = subtitleColor)
        }
    }
}

@Composable
private fun AlbumCard(
    album: ArtistHeroAlbum,
    textColor: Color,
    subtextColor: Color,
    onClick: () -> Unit,
) {
    Column(Modifier.width(150.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick)) {
        Box {
            AlbumCover(album, Modifier.size(150.dp), RoundedCornerShape(16.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = album.title,
            style = MaterialTheme.typography.titleSmall,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = album.subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = subtextColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** An album in the expanded two-column grid: the cover fills its column. */
@Composable
private fun AlbumGridCard(
    album: ArtistHeroAlbum,
    textColor: Color,
    subtextColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick)) {
        AlbumCover(album, Modifier.fillMaxWidth().aspectRatio(1f), RoundedCornerShape(16.dp))
        Spacer(Modifier.height(8.dp))
        Text(
            text = album.title,
            style = MaterialTheme.typography.titleSmall,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = album.subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = subtextColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
