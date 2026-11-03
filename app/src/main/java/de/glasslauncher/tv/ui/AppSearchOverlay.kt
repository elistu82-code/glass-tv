package de.glasslauncher.tv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.glasslauncher.tv.core.InstalledApp

@Composable
internal fun BoxScope.AppSearchOverlay(
    apps: List<InstalledApp>,
    firstFocus: FocusRequester,
    onLaunch: (InstalledApp) -> Unit,
    onClose: () -> Unit,
) {
    val config = LocalGlassConfig.current
    var query by remember { mutableStateOf("") }
    val results = remember(apps, query) {
        val term = query.trim()
        apps.filter { term.isEmpty() || it.label.contains(term, ignoreCase = true) }
    }
    val resultFocus = remember { FocusRequester() }
    val closeFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    fun focusResults() {
        keyboard?.hide()
        if (results.isEmpty()) closeFocus.requestFocus() else resultFocus.requestFocus()
    }
    Column(
        Modifier.align(Alignment.Center).padding(GlassTokens.screenInset)
            .widthIn(max = 620.dp).fillMaxWidth().fillMaxHeight()
            .glassSurface(config, stronger = true).padding(24.dp).testTag("app-search"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Apps suchen", fontSize = 24.sp)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it.take(128) },
            modifier = Modifier.fillMaxWidth().focusRequester(firstFocus).testTag("search-query")
                .onPreviewKeyEvent {
                    if (it.type == KeyEventType.KeyDown && it.key == Key.DirectionDown) {
                        focusResults(); true
                    } else false
                },
            label = { Text("App-Name") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusResults() }),
        )
        Text("${results.size} sichtbare Apps · ↓ Ergebnisse · Zurück schließen", fontSize = 11.sp,
            color = Color.White.copy(alpha = .65f))
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (results.isEmpty()) item { Text("Keine passende sichtbare App", Modifier.padding(12.dp), fontSize = 14.sp) }
            itemsIndexed(results, key = { _, app -> app.id }) { index, app ->
                MenuButton(app.label, Modifier.testTag("search-result:${app.id}")
                    .then(if (index == 0) Modifier.focusRequester(resultFocus) else Modifier)
                    .onPreviewKeyEvent {
                        if (index == 0 && it.type == KeyEventType.KeyDown && it.key == Key.DirectionUp) {
                            firstFocus.requestFocus(); true
                        } else false
                    }) {
                    keyboard?.hide()
                    onLaunch(app)
                }
            }
        }
        MenuButton("Suche schließen", Modifier.focusRequester(closeFocus)) { keyboard?.hide(); onClose() }
    }
}
