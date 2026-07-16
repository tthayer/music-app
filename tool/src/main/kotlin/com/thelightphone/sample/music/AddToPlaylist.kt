package com.thelightphone.sample.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.delete
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextOverflow
import com.thelightphone.lp3Keyboard.ui.CapsLockedLayout
import com.thelightphone.lp3Keyboard.ui.DefaultLp3KeyboardViewModel
import com.thelightphone.lp3Keyboard.ui.EmojiLayout
import com.thelightphone.lp3Keyboard.ui.ExtendedCharKeyboard
import com.thelightphone.lp3Keyboard.ui.LayoutOptions
import com.thelightphone.lp3Keyboard.ui.LowerCaseLayout
import com.thelightphone.lp3Keyboard.ui.Lp3RepeatableKeyboardCallback
import com.thelightphone.lp3Keyboard.ui.NumberLayout
import com.thelightphone.lp3Keyboard.ui.SpecialKey
import com.thelightphone.lp3Keyboard.ui.SymbolsLayout
import com.thelightphone.lp3Keyboard.ui.UpperCaseLayout
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.defaultKeyboardOptions
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.keyboard.LightEmbeddedLp3Keyboard
import com.thelightphone.sdk.ui.lightClickable
import kotlinx.coroutines.flow.MutableStateFlow

/** The tracks a long-press wants to add, plus a human label for what was picked. */
data class AddRequest(val tracks: List<Track>, val label: String)

private enum class AddStep { Menu, NewName, Existing }

/**
 * Wraps a browsing screen so a long-press can raise the add-to-playlist flow as
 * an overlay drawn on top of it. Every exit (X / Create / picking an existing
 * playlist) dismisses the overlay and returns to the same screen, so the user
 * always lands back where they long-pressed. [content] receives an `open`
 * callback to invoke from a row's long-press.
 */
@Composable
fun AddToPlaylistHost(content: @Composable (open: (List<Track>, String) -> Unit) -> Unit) {
    var request by remember { mutableStateOf<AddRequest?>(null) }
    Box(modifier = Modifier.fillMaxSize()) {
        content { tracks, label ->
            if (tracks.isNotEmpty()) request = AddRequest(tracks, label)
        }
        request?.let { req ->
            AddToPlaylistOverlay(request = req, onDismiss = { request = null })
        }
    }
}

@Composable
private fun AddToPlaylistOverlay(request: AddRequest, onDismiss: () -> Unit) {
    val themeColors by LightThemeController.colors.collectAsState()
    val playlists by PlaylistStore.playlists.collectAsState()
    var step by remember { mutableStateOf(AddStep.Menu) }

    LightTheme(colors = themeColors) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(LightThemeTokens.colors.background)
                // Swallow taps on empty areas so nothing falls through to the
                // list underneath while the overlay is up.
                .lightClickable(onClick = {}),
        ) {
            when (step) {
                AddStep.Menu -> AddMenu(
                    label = request.label,
                    hasExisting = playlists.isNotEmpty(),
                    onNew = { step = AddStep.NewName },
                    onExisting = { step = AddStep.Existing },
                    onClose = onDismiss,
                )

                AddStep.NewName -> PlaylistNameEntry(
                    onCancel = onDismiss,
                    onCreate = { name ->
                        PlaylistStore.createPlaylist(name, request.tracks)
                        onDismiss()
                    },
                )

                AddStep.Existing -> ExistingPicker(
                    playlists = playlists,
                    onPick = { id ->
                        PlaylistStore.addToPlaylist(id, request.tracks)
                        onDismiss()
                    },
                    onClose = onDismiss,
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.AddMenu(
    label: String,
    hasExisting: Boolean,
    onNew: () -> Unit,
    onExisting: () -> Unit,
    onClose: () -> Unit,
) {
    LightTopBar(
        leftButton = LightBarButton.LightIcon(
            icon = LightIcons.CLOSE,
            onClick = onClose,
            contentDescription = "Cancel",
        ),
        center = LightTopBarCenter.Text("Add to Playlist"),
    )
    LightText(
        text = label,
        variant = LightTextVariant.Detail,
        lighten = true,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 1f.gridUnitsAsDp(), vertical = 0.5f.gridUnitsAsDp()),
    )
    LightScrollView(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .padding(horizontal = 1f.gridUnitsAsDp()),
    ) {
        MenuRow(label = "New Playlist", leadingIcon = LightIcons.ADD, onClick = onNew)
        if (hasExisting) {
            MenuRow(label = "Existing Playlist", leadingIcon = LightIcons.LIST, onClick = onExisting)
        }
    }
}

@Composable
private fun ColumnScope.ExistingPicker(
    playlists: List<Playlist>,
    onPick: (String) -> Unit,
    onClose: () -> Unit,
) {
    LightTopBar(
        leftButton = LightBarButton.LightIcon(
            icon = LightIcons.CLOSE,
            onClick = onClose,
            contentDescription = "Cancel",
        ),
        center = LightTopBarCenter.Text("Choose Playlist"),
    )
    LightScrollView(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .padding(horizontal = 1f.gridUnitsAsDp()),
    ) {
        playlists.forEach { playlist ->
            MenuRow(label = playlist.name, onClick = { onPick(playlist.id) })
        }
    }
}

/**
 * Full-screen playlist-name entry with the standard LP3 keyboard. Mirrors the
 * SDK's `LightTextInputEditor` but cancels with an 'X' ([LightIcons.CLOSE])
 * rather than a back arrow, per the requested flow. "Create" is disabled (a
 * no-op) until the name is non-blank.
 */
@Composable
private fun PlaylistNameEntry(onCreate: (String) -> Unit, onCancel: () -> Unit) {
    val state = rememberTextFieldState()
    val keyboardCallback = remember(state) { PlaylistKeyboardCallback(state) }
    val optionsFlow = remember { MutableStateFlow(defaultKeyboardOptions()) }
    val keyboardViewModel = remember(keyboardCallback) {
        DefaultLp3KeyboardViewModel(
            keyboardCallback,
            keyboardOptionsFlow = optionsFlow,
            optionsForLayout = { layout ->
                val showCloseButton = when (layout) {
                    EmojiLayout, is ExtendedCharKeyboard -> true
                    CapsLockedLayout, LowerCaseLayout, NumberLayout, SymbolsLayout, UpperCaseLayout -> false
                }
                LayoutOptions(showCloseButton)
            },
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LightTopBar(
            leftButton = LightBarButton.LightIcon(
                icon = LightIcons.CLOSE,
                onClick = onCancel,
                contentDescription = "Cancel",
            ),
            center = LightTopBarCenter.Text("New Playlist"),
            modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
        )

        val name = state.text.toString()
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 2f.gridUnitsAsDp()),
            contentAlignment = Alignment.TopStart,
        ) {
            LightText(
                text = name.ifEmpty { "Playlist name" },
                variant = LightTextVariant.Heading,
                lighten = name.isEmpty(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        LightEmbeddedLp3Keyboard(viewModel = keyboardViewModel)

        LightBottomBar(
            items = listOf(
                LightBarButton.Text(
                    text = "Create",
                    onClick = {
                        val trimmed = state.text.toString().trim()
                        if (trimmed.isNotEmpty()) onCreate(trimmed)
                    },
                ),
            ),
        )
    }
}

/**
 * Bridges the LP3 keyboard's key events onto a Compose [TextFieldState]. The
 * SDK's own `TextInputKeyboardCallback` is `internal`, so this mirrors its
 * behaviour (insert at cursor, surrogate-aware and word-wise backspace) over
 * the public [Lp3RepeatableKeyboardCallback] interface.
 */
private class PlaylistKeyboardCallback(
    private val state: TextFieldState,
) : Lp3RepeatableKeyboardCallback {

    override fun onKeyPressed(code: Int) = Unit

    override fun onSpecialKeyPressed(key: SpecialKey) {
        if (key == SpecialKey.Space) insertAtCursor(" ")
    }

    override fun onKeyReleased(code: Int) = insertCodePoint(code)

    override fun onSpecialKeyReleased(key: SpecialKey) {
        when (key) {
            SpecialKey.Backspace -> {
                val before = state.text.subSequence(0, state.selection.min)
                deleteBeforeCursor(surrogateAwareDeleteCount(before, 1))
            }
            // A playlist name is a single line, so Return submits nothing here.
            else -> Unit
        }
    }

    override fun onKeyLongPressed(code: Int) = Unit

    override fun onSpecialKeyLongPressed(key: SpecialKey) {
        if (key == SpecialKey.Backspace) {
            val before = state.text.subSequence(0, state.selection.min)
            deleteBeforeCursor(deleteWordCount(before))
        }
    }

    override fun onKeyRepeated(code: Int) = insertCodePoint(code)

    override fun onSpecialKeyRepeated(key: SpecialKey) {
        if (key == SpecialKey.Space) insertAtCursor(" ")
    }

    private fun insertCodePoint(code: Int) = insertAtCursor(buildString { appendCodePoint(code) })

    private fun insertAtCursor(text: String) {
        state.edit {
            val start = selection.min
            val end = selection.max
            replace(start, end, text)
            selection = TextRange(start + text.length)
        }
    }

    private fun deleteBeforeCursor(count: Int) {
        if (count <= 0) return
        state.edit {
            val end = selection.min
            if (end == 0) return@edit
            val start = (end - count).coerceAtLeast(0)
            delete(start, end)
            selection = TextRange(start)
        }
    }

    private fun surrogateAwareDeleteCount(value: CharSequence, defaultCount: Int): Int {
        if (value.isEmpty()) return 0
        val last = value[value.length - 1]
        return if (Character.isLowSurrogate(last)) 2 else defaultCount
    }

    private fun deleteWordCount(value: CharSequence): Int {
        val trimmed = value.trimEnd()
        val lastSpace = trimmed.indexOfLast { it.isWhitespace() }
        return value.length - if (lastSpace >= 0) lastSpace + 1 else 0
    }
}
