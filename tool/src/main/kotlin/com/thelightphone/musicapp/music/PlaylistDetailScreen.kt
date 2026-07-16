package com.thelightphone.musicapp.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightIcon
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable

/**
 * A single playlist's songs, playable like any other list, with a settings icon
 * in the top bar (matching stock Light apps) that opens playlist management.
 * If the playlist is deleted while this screen is showing, it pops itself.
 */
class PlaylistDetailScreen(
    sealedActivity: SealedLightActivity,
    private val playlistId: String,
) : SimpleLightScreen<Unit>(sealedActivity) {

    @Composable
    override fun Content() {
        val playlists by PlaylistStore.playlists.collectAsState()
        val library by MusicLibrary.tracks.collectAsState()
        val playback by MusicPlayer.state.collectAsState()
        val playlist = playlists.find { it.id == playlistId }

        // Deleted from the settings screen → leave, back to the Playlists list.
        LaunchedEffect(playlist == null) { if (playlist == null) goBack() }
        if (playlist == null) return

        val tracks = playlist.resolveTracks(library)

        MusicScaffold(title = playlist.name, onBack = { goBack() }) {
            if (tracks.isEmpty()) {
                CenteredHint("This playlist is empty.\nLong-press a song or album to add to it.")
            } else {
                LightScrollView(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 1f.gridUnitsAsDp()),
                ) {
                    tracks.forEachIndexed { index, track ->
                        TrackRow(
                            title = track.title,
                            secondary = track.artist,
                            isCurrent = playback.current?.uri == track.uri,
                            onClick = {
                                MusicPlayer.play(tracks, startIndex = index)
                                navigateTo({ NowPlayingScreen(it) })
                            },
                        )
                    }
                }
            }

            // Playlist management: a settings gear in the bottom-left. Rendered
            // with LightTopBar (mirrored to the bottom) rather than LightBottomBar
            // so the gear's left inset and vertical centering exactly match the
            // back arrow in the top bar — LightBottomBar uses a wider inset and a
            // top margin that would skew a lone icon.
            LightTopBar(
                leftButton = LightBarButton.LightIcon(
                    icon = LightIcons.SETTINGS,
                    onClick = { navigateTo({ PlaylistSettingsScreen(it, playlistId) }) },
                    contentDescription = "Playlist settings",
                ),
            )
        }
    }
}

/**
 * Management menu for one playlist: rename, remove songs, or delete. Reached via
 * the settings icon on [PlaylistDetailScreen]. Delete asks for confirmation,
 * then pops back — [PlaylistDetailScreen] notices the playlist is gone and pops
 * on to the Playlists list.
 */
class PlaylistSettingsScreen(
    sealedActivity: SealedLightActivity,
    private val playlistId: String,
) : SimpleLightScreen<Unit>(sealedActivity) {

    @Composable
    override fun Content() {
        val playlists by PlaylistStore.playlists.collectAsState()
        val playlist = playlists.find { it.id == playlistId } ?: return
        var confirmingDelete by remember { mutableStateOf(false) }

        Box(modifier = Modifier.fillMaxSize()) {
            MusicScaffold(title = "Playlist Settings", onBack = { goBack() }) {
                LightScrollView(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 1f.gridUnitsAsDp()),
                ) {
                    MenuRow(
                        label = "Rename",
                        leadingIcon = LightIcons.PENCIL,
                        onClick = {
                            navigateTo({ RenamePlaylistScreen(it, playlistId, playlist.name) })
                        },
                    )
                    MenuRow(
                        label = "Remove Songs",
                        leadingIcon = LightIcons.LIST,
                        onClick = { navigateTo({ RemoveSongsScreen(it, playlistId) }) },
                    )
                    MenuRow(
                        label = "Delete Playlist",
                        leadingIcon = LightIcons.TRASH,
                        onClick = { confirmingDelete = true },
                    )
                }
            }

            if (confirmingDelete) {
                DeleteConfirmOverlay(
                    name = playlist.name,
                    onCancel = { confirmingDelete = false },
                    onConfirm = {
                        PlaylistStore.deletePlaylist(playlistId)
                        goBack()
                    },
                )
            }
        }
    }
}

/** Full-screen rename entry, reusing the keyboard editor with a Save button. */
class RenamePlaylistScreen(
    sealedActivity: SealedLightActivity,
    private val playlistId: String,
    private val currentName: String,
) : SimpleLightScreen<Unit>(sealedActivity) {

    @Composable
    override fun Content() {
        MusicThemed {
            PlaylistNameEntry(
                title = "Rename Playlist",
                submitLabel = "Save",
                initialName = currentName,
                onCancel = { goBack() },
                onSubmit = { name ->
                    PlaylistStore.renamePlaylist(playlistId, name)
                    goBack()
                },
            )
        }
    }
}

/**
 * Lists the playlist's songs with a trash affordance on each; tapping it removes
 * that song from the playlist (the library file is untouched). Updates live.
 */
class RemoveSongsScreen(
    sealedActivity: SealedLightActivity,
    private val playlistId: String,
) : SimpleLightScreen<Unit>(sealedActivity) {

    @Composable
    override fun Content() {
        val playlists by PlaylistStore.playlists.collectAsState()
        val library by MusicLibrary.tracks.collectAsState()
        val playlist = playlists.find { it.id == playlistId }

        LaunchedEffect(playlist == null) { if (playlist == null) goBack() }
        if (playlist == null) return

        val tracks = playlist.resolveTracks(library)

        MusicScaffold(title = "Remove Songs", onBack = { goBack() }) {
            if (tracks.isEmpty()) {
                CenteredHint("No songs to remove.")
            } else {
                LightScrollView(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 1f.gridUnitsAsDp()),
                ) {
                    tracks.forEach { track ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 0.6f.gridUnitsAsDp()),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                LightText(
                                    text = track.title,
                                    variant = LightTextVariant.Copy,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                LightText(
                                    text = track.artist,
                                    variant = LightTextVariant.Detail,
                                    lighten = true,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            LightIcon(
                                icon = LightIcons.TRASH,
                                size = 1.5f,
                                modifier = Modifier
                                    .padding(start = 0.75f.gridUnitsAsDp())
                                    .lightClickable(
                                        onClickLabel = "Remove ${track.title}",
                                        onClick = {
                                            PlaylistStore.removeFromPlaylist(playlistId, listOf(track))
                                        },
                                    ),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Confirmation overlay for a destructive playlist delete. */
@Composable
private fun DeleteConfirmOverlay(
    name: String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    MusicThemed {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(LightThemeTokens.colors.background)
                .lightClickable(onClick = {}),
        ) {
            LightTopBar(
                leftButton = LightBarButton.LightIcon(
                    icon = LightIcons.CLOSE,
                    onClick = onCancel,
                    contentDescription = "Cancel",
                ),
                center = LightTopBarCenter.Text("Delete Playlist"),
            )
            Box(
                modifier = Modifier.weight(1f).fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                LightText(
                    text = "Delete “$name”?\nThis can't be undone.",
                    variant = LightTextVariant.Copy,
                    align = TextAlign.Center,
                    lighten = true,
                    modifier = Modifier.padding(horizontal = 2f.gridUnitsAsDp()),
                )
            }
            LightBottomBar(
                items = listOf(
                    LightBarButton.Text(text = "Delete", onClick = onConfirm),
                ),
            )
        }
    }
}

/** Centered, lightened hint text for empty states. */
@Composable
private fun androidx.compose.foundation.layout.ColumnScope.CenteredHint(message: String) {
    Box(
        modifier = Modifier.weight(1f).fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        LightText(
            text = message,
            variant = LightTextVariant.Copy,
            align = TextAlign.Center,
            lighten = true,
            modifier = Modifier.padding(horizontal = 2f.gridUnitsAsDp()),
        )
    }
}
