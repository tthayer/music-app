package com.thelightphone.musicapp.music

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.gridUnitsAsDp

/**
 * Lists the user's saved playlists. Selecting one resolves its stored tracks
 * against the live library and opens them as a playable [SongListScreen]. When
 * there are no playlists yet, shows a hint pointing at the long-press flow.
 */
class PlaylistsScreen(
    sealedActivity: SealedLightActivity,
) : SimpleLightScreen<Unit>(sealedActivity) {

    @Composable
    override fun Content() {
        val playlists by PlaylistStore.playlists.collectAsState()

        MusicScaffold(title = "Playlists", onBack = { goBack() }) {
            if (playlists.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    LightText(
                        text = "No playlists yet.\nLong-press a song or album to start one.",
                        variant = LightTextVariant.Copy,
                        align = TextAlign.Center,
                        lighten = true,
                        modifier = Modifier.padding(horizontal = 2f.gridUnitsAsDp()),
                    )
                }
            } else {
                LightScrollView(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 1f.gridUnitsAsDp()),
                ) {
                    playlists.forEach { playlist ->
                        MenuRow(
                            label = playlist.name,
                            onClick = { navigateTo({ PlaylistDetailScreen(it, playlist.id) }) },
                        )
                    }
                }
            }
        }
    }
}
