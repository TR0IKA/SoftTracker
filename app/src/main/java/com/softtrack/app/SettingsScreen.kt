package com.softtrack.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class SettingsState(
    val look: Look, val accentName: String, val fxLevel: Int, val particles: Boolean,
    val notify: Boolean, val notifGranted: Boolean, val exactOk: Boolean
)

class SettingsActions(
    val onLook: (Look) -> Unit, val onAccent: (String) -> Unit, val onFxLevel: (Int) -> Unit, val onParticles: (Boolean) -> Unit,
    val onNotify: (Boolean) -> Unit, val onRequestPermission: () -> Unit, val onExactAlarms: () -> Unit, val onTestNotification: () -> Unit,
    val onExport: () -> Unit, val onImport: () -> Unit
)

@Composable
private fun switchColors(p: Palette) = SwitchDefaults.colors(
    checkedTrackColor = p.accent, checkedThumbColor = p.accentInk,
    uncheckedTrackColor = p.panel2, uncheckedThumbColor = p.muted, uncheckedBorderColor = p.line
)

@Composable
fun SettingsScreen(s: SettingsState, a: SettingsActions, p: Palette) {
    val btnShape = p.shape(50.dp)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Panel(p) {
            SectionLabel("Оформление", p)
            LookPreview("Aurora", "Чёрный, слива и лаванда, звёзды и свечение", s.look == Look.AURORA, Color(0xFF0B0816), Color(0xFFB9A4FF), p) { a.onLook(Look.AURORA) }
            Spacer(Modifier.height(6.dp))
            LookPreview("Nocturne", "Графит, холодный воздух, спокойный контраст", s.look == Look.NOCTURNE, Color(0xFF101722), Color(0xFF91AFC2), p) { a.onLook(Look.NOCTURNE) }
            Spacer(Modifier.height(6.dp))
            LookPreview("Field Notes", "Бумага, редакционная сетка, тёплый тон", s.look == Look.FIELD, Color(0xFFF0EEE7), Color(0xFFB79E54), p) { a.onLook(Look.FIELD) }
        }
        Panel(p) {
            SectionLabel("Акцентный цвет", p)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                accentOptions(s.look).forEach { (name, color) ->
                    val current = (accentOptions(s.look).firstOrNull { it.first == s.accentName } ?: accentOptions(s.look).first()).first == name
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).clickable { a.onAccent(name) }.padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box2(color, current, p)
                        Text(name, color = p.muted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
        Panel(p) {
            SectionLabel("Эффекты", p)
            Text(
                if (s.look == Look.AURORA) "Свечение, звёзды и частицы на фоне. Для слабых устройств выбери меньше или выключи."
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
            Text("SoftTrack 1.2.0 Aurora", color = p.ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
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
