package com.softtrack.app

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

/** Действия экранов над данными. Реализация — в MainActivity. */
class Actions(
    val setStatus: (Habit, LocalDate, Int) -> Unit,
    val open: (Long) -> Unit,
    val saveNote: (Habit, LocalDate, String) -> Unit,
    val toggleFavorite: (Habit) -> Unit
)

@Composable
fun fieldColors(p: Palette) = OutlinedTextFieldDefaults.colors(
    focusedTextColor = p.ink, unfocusedTextColor = p.ink,
    focusedBorderColor = p.accent, unfocusedBorderColor = p.line,
    focusedLabelColor = p.accent, unfocusedLabelColor = p.muted,
    cursorColor = p.accent
)

@Composable
fun chipColors(p: Palette) = FilterChipDefaults.filterChipColors(
    selectedContainerColor = p.accent, selectedLabelColor = p.accentInk,
    containerColor = p.panel2, labelColor = p.muted
)

@Composable
fun EmptyState(title: String, desc: String, p: Palette, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 34.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        StarGlyph(34.dp, p.accent)
        Text(title, color = p.ink, fontSize = 18.sp, fontFamily = p.font, modifier = Modifier.padding(top = 10.dp))
        Text(desc, color = p.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, start = 24.dp, end = 24.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
fun NoteDialog(p: Palette, title: String, initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.solid,
        title = { Text(title, color = p.ink, fontFamily = p.font, fontSize = 18.sp) },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth(),
                label = { Text("Как прошёл день, что помешало") }, minLines = 3, maxLines = 6, colors = fieldColors(p)
            )
        },
        confirmButton = {
            Button(onClick = { onSave(text) }, shape = p.shape(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = p.accent, contentColor = p.accentInk)) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена", color = p.muted) } }
    )
}

fun pickTime(ctx: Context, initial: Int, onPick: (Int) -> Unit) {
    val h = if (initial >= 0) initial / 60 else 9
    val m = if (initial >= 0) initial % 60 else 0
    android.app.TimePickerDialog(ctx, { _, hh, mm -> onPick(hh * 60 + mm) }, h, m, true).show()
}
