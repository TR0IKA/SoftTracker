package com.softtrack.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class SettingsState(
    val look: Look, val accentName: String, val fxLevel: Int, val particles: Boolean,
    val notify: Boolean, val notifGranted: Boolean, val exactOk: Boolean,
    val bgOn: Boolean = false, val bgDim: Float = 0.55f
)

class SettingsActions(
    val onLook: (Look) -> Unit, val onAccent: (String) -> Unit, val onFxLevel: (Int) -> Unit, val onParticles: (Boolean) -> Unit,
    val onNotify: (Boolean) -> Unit, val onRequestPermission: () -> Unit, val onExactAlarms: () -> Unit, val onTestNotification: () -> Unit,
    val onExport: () -> Unit, val onImport: () -> Unit,
    val onPickBg: () -> Unit = {}, val onClearBg: () -> Unit = {}, val onBgDim: (Float) -> Unit = {}
)

@Composable
private fun switchColors(p: Palette) = SwitchDefaults.colors(
    checkedTrackColor = p.accent, checkedThumbColor = p.accentInk,
    uncheckedTrackColor = p.panel2, uncheckedThumbColor = p.muted, uncheckedBorderColor = p.line
)

@Composable
fun SettingsScreen(s: SettingsState, a: SettingsActions, p: Palette) {
    val btnShape = p.shape(50.dp)
    var customDialog by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Panel(p) {
            SectionLabel("Оформление", p)
            LookPreview("Aurora", "Графит и слива, лавандовые акценты, тихие звёзды", s.look == Look.AURORA, Color(0xFF0B0816), Color(0xFFB9A4FF), p) { a.onLook(Look.AURORA) }
            Spacer(Modifier.height(6.dp))
            LookPreview("Nocturne", "Графит, холодный воздух, спокойный контраст", s.look == Look.NOCTURNE, Color(0xFF101722), Color(0xFF91AFC2), p) { a.onLook(Look.NOCTURNE) }
            Spacer(Modifier.height(6.dp))
            LookPreview("Путь", "Космос и 3D-дорога: привычки как планеты на пути", s.look == Look.ROAD, Color(0xFF0A1022), Color(0xFF6FA8FF), p) { a.onLook(Look.ROAD) }
        }
        Panel(p) {
            SectionLabel("Акцентный цвет", p)
            val opts = accentOptions(s.look)
            val custom = parseCustomAccent(s.accentName)
            val currentName = if (custom != null) "" else (opts.firstOrNull { it.first == s.accentName } ?: opts.first()).first
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                opts.chunked(4).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { (name, color) ->
                            Column(Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).clickable { a.onAccent(name) }.padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Box2(color, currentName == name, p)
                                Text(name, color = p.muted, fontSize = 10.sp, maxLines = 1, softWrap = false, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(10.dp)).clickable { customDialog = true }.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box2(custom ?: p.accent, custom != null, p)
                Column(Modifier.padding(start = 12.dp)) {
                    Text("Свой цвет", color = p.ink, fontSize = 14.sp)
                    Text(if (custom != null) s.accentName.uppercase() else "Выбрать любой оттенок с предпросмотром", color = p.muted, fontSize = 12.sp)
                }
            }
        }
        if (customDialog) {
            CustomAccentDialog(resolveAccent(s.look, s.accentName), p, onPick = { a.onAccent(it); customDialog = false }, onDismiss = { customDialog = false })
        }
        Panel(p) {
            SectionLabel("Фон приложения", p)
            Text(
                if (s.look == Look.ROAD) "В оформлении «Путь» фон космический, своё фото работает в Aurora и Nocturne."
                else "Поставь своё фото фоном. Оно хранится только на этом устройстве.",
                color = p.muted, fontSize = 13.sp, lineHeight = 18.sp
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = a.onPickBg, shape = btnShape, colors = ButtonDefaults.buttonColors(containerColor = p.accent, contentColor = p.accentInk)) { Text("Выбрать фото") }
                if (s.bgOn) OutlinedButton(onClick = a.onClearBg, shape = btnShape) { Text("Убрать", color = p.ink) }
            }
            if (s.bgOn) {
                var dim by remember { mutableStateOf(s.bgDim) }
                Text("Затемнение", color = p.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
                Slider(
                    value = dim, onValueChange = { dim = it; a.onBgDim(it) }, valueRange = 0.15f..0.9f,
                    colors = SliderDefaults.colors(thumbColor = p.accent, activeTrackColor = p.accent, inactiveTrackColor = p.line)
                )
            }
        }
        Panel(p) {
            SectionLabel("Эффекты", p)
            Text(
                if (s.look != Look.NOCTURNE) "Свечение, звёзды и частицы на фоне. Для слабых устройств выбери меньше или выключи."
                else "Фоновые эффекты работают в оформлении Aurora.",
                color = p.muted, fontSize = 13.sp, lineHeight = 18.sp
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Выкл", "Мягкие", "Средние", "Яркие").forEachIndexed { i, label ->
                    FilterChip(selected = s.fxLevel == i, onClick = { a.onFxLevel(i) }, label = { Text(label, fontSize = 12.sp) }, shape = btnShape, colors = chipColors(p))
                }
            }
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Звёзды и частицы", color = p.ink, fontSize = 15.sp)
                    Text("Двигаются на фоне экрана", color = p.muted, fontSize = 12.sp)
                }
                Switch(checked = s.particles, onCheckedChange = a.onParticles, colors = switchColors(p))
            }
        }
        Panel(p) {
            SectionLabel("Уведомления", p)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Напоминания о привычках", color = p.ink, fontSize = 15.sp)
                    Text("Только в запланированные дни и время", color = p.muted, fontSize = 12.sp)
                }
                Switch(checked = s.notify, onCheckedChange = a.onNotify, colors = switchColors(p))
            }
            if (s.notify && !s.notifGranted) {
                Text("Уведомления для SoftTrack сейчас запрещены в системе.", color = p.danger, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
                TextButton(onClick = a.onRequestPermission) { Text("Разрешить уведомления", color = p.accent) }
            }
            if (s.notify && !s.exactOk) {
                Text("Без разрешения на точные будильники напоминания могут приходить с задержкой в несколько минут.", color = p.muted, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 8.dp))
                TextButton(onClick = a.onExactAlarms) { Text("Включить точные напоминания", color = p.accent) }
            }
            if (s.notify && s.notifGranted) TextButton(onClick = a.onTestNotification) { Text("Проверить уведомление", color = p.accent) }
        }
        Panel(p) {
            SectionLabel("Резервная копия", p)
            Text("Экспорт сохраняет привычки, расписания, историю и заметки в JSON. Копии старых версий тоже читаются. Импорт заменяет текущие данные.", color = p.muted, fontSize = 13.sp, lineHeight = 18.sp)
            Spacer(Modifier.height(12.dp))
            Button(onClick = a.onExport, modifier = Modifier.fillMaxWidth(), shape = btnShape, colors = ButtonDefaults.buttonColors(containerColor = p.accent, contentColor = p.accentInk)) {
                Icon(Icons.Default.FileDownload, null); Spacer(Modifier.width(8.dp)); Text("Экспортировать JSON")
            }
            OutlinedButton(onClick = a.onImport, modifier = Modifier.fillMaxWidth(), shape = btnShape) {
                Icon(Icons.Default.FileUpload, null, tint = p.ink); Spacer(Modifier.width(8.dp)); Text("Импортировать JSON", color = p.ink)
            }
        }
        Panel(p) {
            SectionLabel("О приложении", p)
            Text("SoftTrack 1.3.1", color = p.ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text("Привычки, расписание по дням недели и спокойная статистика. Данные хранятся только на этом устройстве.", color = p.muted, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun Box2(color: Color, selected: Boolean, p: Palette) {
    Spacer(Modifier.size(28.dp).background(color, CircleShape).then(if (selected) Modifier.border(2.dp, p.ink, CircleShape) else Modifier))
}

@Composable
private fun LookPreview(title: String, desc: String, selected: Boolean, bg: Color, accent: Color, p: Palette, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (selected) p.panel2 else Color.Transparent).clickable(onClick = onClick).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, color = p.ink, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(desc, color = p.muted, fontSize = 12.sp)
        }
        Row(Modifier.width(62.dp).height(38.dp).clip(RoundedCornerShape(6.dp)).background(bg).padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Spacer(Modifier.fillMaxWidth(0.8f).height(4.dp).background(accent))
                Spacer(Modifier.fillMaxWidth().height(3.dp).background(accent.copy(alpha = 0.3f)))
            }
            if (selected) Icon(Icons.Default.CheckCircle, null, tint = accent, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun CustomAccentDialog(initial: Color, p: Palette, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var r by remember { mutableStateOf(initial.red * 255f) }
    var g by remember { mutableStateOf(initial.green * 255f) }
    var b by remember { mutableStateOf(initial.blue * 255f) }
    val color = Color(r.toInt(), g.toInt(), b.toInt())
    val hex = String.format("#%02X%02X%02X", r.toInt(), g.toInt(), b.toInt())
    val ink = if (color.luminance() > 0.35f) Color(0xFF14101F) else Color.White
    val sliderColors = SliderDefaults.colors(thumbColor = color, activeTrackColor = color, inactiveTrackColor = p.line)
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = p.solid,
        title = { Text("Свой цвет", color = p.ink, fontFamily = p.font, fontSize = 18.sp) },
        text = {
            Column {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(p.bgTop).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    StarGlyph(30.dp, color)
                    Spacer(Modifier.width(12.dp))
                    Box(Modifier.clip(RoundedCornerShape(50.dp)).background(color).padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text("Пример", color = ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                    Spacer(Modifier.weight(1f))
                    Text(hex, color = p.muted, fontSize = 13.sp)
                }
                Spacer(Modifier.height(8.dp))
                Slider(value = r, onValueChange = { r = it }, valueRange = 0f..255f, colors = sliderColors)
                Slider(value = g, onValueChange = { g = it }, valueRange = 0f..255f, colors = sliderColors)
                Slider(value = b, onValueChange = { b = it }, valueRange = 0f..255f, colors = sliderColors)
            }
        },
        confirmButton = {
            Button(onClick = { onPick(hex) }, shape = p.shape(50.dp), colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = ink)) { Text("Применить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена", color = p.muted) } }
    )
}
