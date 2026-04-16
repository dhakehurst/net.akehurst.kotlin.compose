package net.akehurst.kotlin.compose.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun FindReplaceBar(state: FindReplaceStateCompose, modifier: Modifier = Modifier) {
    val focusReq = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        snapshotFlow { Triple(state.searchQueryState.text.toString(), state.isCaseSensitive, state.isRegex) }
            .collect { state.updateMatches() }
    }
    LaunchedEffect(state.isVisible) { if (state.isVisible) focusReq.requestFocus() }

    Column(
        modifier
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.onSurfaceVariant)
            .padding(4.dp)
            .widthIn(max = 480.dp)
    ) {
        // Find row
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            val bColor = if (state.isRegexError) Color.Red else MaterialTheme.colorScheme.outline
            BasicTextField(
                state = state.searchQueryState,
                textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                modifier = Modifier.weight(1f).height(28.dp)
                    .border(1.dp, bColor)
                    .padding(horizontal = 4.dp, vertical = 2.dp)
                    .focusRequester(focusReq)
                    .onPreviewKeyEvent { ev ->
                        if (ev.type == KeyEventType.KeyDown) when {
                            ev.isEscape -> { state.close(); true }
                            ev.isCtrlF -> { state.open(showReplace = false); true }
                            ev.isCtrlR -> { state.open(showReplace = true); true }
                            ev.isShiftEnter -> { state.findPrevious(); true }
                            ev.isEnter -> { state.findNext(); true }
                            else -> false
                        } else when {
                            ev.isEscape || ev.isEnter || ev.isShiftEnter || ev.isCtrlF || ev.isCtrlR -> true
                            else -> false
                        }
                    }
            )
            Spacer(Modifier.width(4.dp))
            val mt = when {
                state.query.isEmpty() -> ""
                0 == state.matchCount -> "No results"
                else -> "${state.currentMatchIndex + 1}/${state.matchCount}"
            }
            Text(mt, style = TextStyle(fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant), modifier = Modifier.widthIn(min = 56.dp))
            Spacer(Modifier.width(2.dp))
            // Case-sensitive toggle
            val caseActive = state.isCaseSensitive
            val toggleShape = RoundedCornerShape(4.dp)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(24.dp)
                    .clip(toggleShape)
                    .background(if (caseActive) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                    .border(1.dp, if (caseActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, toggleShape)
                    .clickable { state.toggleCaseSensitive() }
            ) {
                Text("Aa", style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (caseActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant))
            }
            Spacer(Modifier.width(2.dp))
            // Regex toggle
            val regexActive = state.isRegex
            val regexError = state.isRegexError
            val regexBg = when {
                regexError -> MaterialTheme.colorScheme.errorContainer
                regexActive -> MaterialTheme.colorScheme.primaryContainer
                else -> Color.Transparent
            }
            val regexBorder = when {
                regexError -> MaterialTheme.colorScheme.error
                regexActive -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.outline
            }
            val regexFg = when {
                regexError -> MaterialTheme.colorScheme.onErrorContainer
                regexActive -> MaterialTheme.colorScheme.onPrimaryContainer
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(24.dp)
                    .clip(toggleShape)
                    .background(regexBg)
                    .border(1.dp, regexBorder, toggleShape)
                    .clickable { state.toggleRegex() }
            ) {
                Text(".*", style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = regexFg))
            }
            Spacer(Modifier.width(2.dp))
            IconButton(onClick = { state.findPrevious() }, modifier = Modifier.size(28.dp)) {
                Text("\u2191", style = TextStyle(fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant))
            }
            IconButton(onClick = { state.findNext() }, modifier = Modifier.size(28.dp)) {
                Text("\u2193", style = TextStyle(fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant))
            }
            IconButton(onClick = { state.close() }, modifier = Modifier.size(28.dp)) {
                Text("\u2715", style = TextStyle(fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant))
            }
        }
        // Replace row
        if (state.isReplaceVisible) {
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                BasicTextField(
                    state = state.replaceTextState,
                    textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier.weight(1f).height(28.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outline)
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                        .onPreviewKeyEvent { ev ->
                            if (ev.type == KeyEventType.KeyDown) when {
                                ev.isEscape -> { state.close(); true }
                                ev.isCtrlF -> { state.open(showReplace = false); true }
                                ev.isCtrlR -> { state.open(showReplace = true); true }
                                else -> false
                            } else when {
                                ev.isEscape || ev.isCtrlF || ev.isCtrlR -> true
                                else -> false
                            }
                        }
                )
                Spacer(Modifier.width(4.dp))
                Button(
                    onClick = { state.replaceCurrent() },
                    modifier = Modifier.height(28.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                ) { Text("Replace", style = TextStyle(fontSize = 12.sp)) }
                Spacer(Modifier.width(4.dp))
                Button(
                    onClick = { state.replaceAll() },
                    modifier = Modifier.height(28.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                ) { Text("All", style = TextStyle(fontSize = 12.sp)) }
            }
        }
    }
}
