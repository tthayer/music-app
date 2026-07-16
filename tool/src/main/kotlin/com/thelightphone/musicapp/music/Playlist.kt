package com.thelightphone.musicapp.music

import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * A user-created playlist: an ordered list of track content-URIs (stored as
 * strings). Tracks are resolved back to [Track]s against the live
 * [MusicLibrary] at read time via [resolveTracks], so a playlist survives even
 * if the library hasn't finished scanning yet.
 */
@Serializable
data class Playlist(
    val id: String,
    val name: String,
    val trackUris: List<String>,
)

/** Resolves this playlist's stored URIs to live [Track]s, preserving order. */
fun Playlist.resolveTracks(library: List<Track>): List<Track> {
    val byUri = library.associateBy { it.uri.toString() }
    return trackUris.mapNotNull { byUri[it] }
}

/**
 * Process-level store of the user's playlists, persisted as a JSON blob in the
 * SDK's DataStore. Mirrors [MusicLibrary]: a singleton that survives navigation,
 * exposes observable state, and is lazily bound to a [DataStore] on first use.
 * Mutations are fire-and-forget — they update the in-memory [StateFlow]
 * immediately and persist on a background scope.
 */
object PlaylistStore {

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }
    private val key = stringPreferencesKey("music_playlists_json")

    @Volatile
    private var dataStore: DataStore<Preferences>? = null

    @Volatile
    private var loaded = false

    /** Binds the DataStore and loads persisted playlists once per process. */
    fun ensureLoaded(store: DataStore<Preferences>) {
        dataStore = store
        if (loaded) return
        scope.launch {
            val raw = store.data.first()[key]
            _playlists.value = raw?.let {
                runCatching { json.decodeFromString<List<Playlist>>(it) }.getOrDefault(emptyList())
            } ?: emptyList()
            loaded = true
        }
    }

    /** Creates a new playlist seeded with [tracks] and appends it to the list. */
    fun createPlaylist(name: String, tracks: List<Track>) = mutate { current ->
        current + Playlist(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            trackUris = tracks.map { it.uri.toString() }.distinct(),
        )
    }

    /** Appends [tracks] to the playlist with [playlistId], skipping duplicates. */
    fun addToPlaylist(playlistId: String, tracks: List<Track>) = mutate { current ->
        current.map { playlist ->
            if (playlist.id != playlistId) {
                playlist
            } else {
                val additions = tracks.map { it.uri.toString() }
                playlist.copy(trackUris = (playlist.trackUris + additions).distinct())
            }
        }
    }

    /** Renames the playlist with [playlistId]. A blank name is ignored. */
    fun renamePlaylist(playlistId: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        mutate { current ->
            current.map { if (it.id == playlistId) it.copy(name = trimmed) else it }
        }
    }

    /** Removes [tracks] from the playlist with [playlistId]. */
    fun removeFromPlaylist(playlistId: String, tracks: List<Track>) = mutate { current ->
        val removals = tracks.map { it.uri.toString() }.toSet()
        current.map { playlist ->
            if (playlist.id != playlistId) {
                playlist
            } else {
                playlist.copy(trackUris = playlist.trackUris.filterNot { it in removals })
            }
        }
    }

    /** Deletes the playlist with [playlistId] entirely. */
    fun deletePlaylist(playlistId: String) = mutate { current ->
        current.filterNot { it.id == playlistId }
    }

    private fun mutate(transform: (List<Playlist>) -> List<Playlist>) {
        val next = transform(_playlists.value)
        _playlists.value = next
        scope.launch {
            dataStore?.edit { prefs -> prefs[key] = json.encodeToString(next) }
        }
    }
}
