package com.softtrack.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

class MainActivity : ComponentActivity() {
    private var resumeTick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Reminders.ensureChannel(this)
        setContent { SoftTrackApp(HabitDb.get(this), getSharedPreferences("settings", MODE_PRIVATE), resumeTick) }
    }

    override fun onResume() {
        super.onResume()
        resumeTick++
        Reminders.rescheduleAll(this)
    }
}

private val tabNames = listOf("Сегодня", "Привычки", "Календарь", "Статистика", "Настройки")
private val tabSubtitles = listOf(
    "Маленькие шаги, большие перемены", "Твоя система", "История по дням", "Последовательность важнее идеала", "Параметры приложения"
)
private val tabIcons: List<ImageVector> = listOf(Icons.Default.Today, Icons.Default.FormatListBulleted, Icons.Default.CalendarMonth, Icons.Default.BarChart, Icons.Default.Tune)

@Composable
fun SoftTrackApp(db: HabitDb, prefs: SharedPreferences, resumeTick: Int) {
    val ctx = LocalContext.current
    var version by remember { mutableStateOf(0) }
    var today by remember { mutableStateOf(LocalDate.now()) }
    var tab by rememberSaveable { mutableStateOf(0) }
    var detailId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var editId by rememberSaveable { mutableStateOf<Long?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var permTick by remember { mutableStateOf(0) }

    var look by remember { mutableStateOf(runCatching { Look.valueOf(prefs.getString("look2", Look.AURORA.name) ?: Look.AURORA.name) }.getOrDefault(Look.AURORA)) }
    var accentName by remember(look) { mutableStateOf(prefs.getString(accentPrefKey(look), "") ?: "") }
    var fxLevel by remember { mutableStateOf(prefs.getInt("fx_level", 2)) }
    var particles by remember { mutableStateOf(prefs.getBoolean("fx_particles", true)) }
    var notify by remember { mutableStateOf(prefs.getBoolean("notify", true)) }

    LaunchedEffect(resumeTick) {
        today = LocalDate.now()
        version++
        permTick++
    }
    LaunchedEffect(Unit) {
        while (true) {
            val next = LocalDate.now().plusDays(1).atStartOfDay()
            delay(Duration.between(LocalDateTime.now(), next).toMillis().coerceAtLeast(1000L) + 500L)
            today = LocalDate.now()
            version++
        }
    }

    val snap = remember(version) { db.loadSnapshot() }
    val p = buildPalette(look, accentName)
    val fx = Fx(fxLevel, particles)

    fun commit() {
        version++
        Reminders.rescheduleAll(ctx)
    }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permTick++
        Reminders.rescheduleAll(ctx)
    }
    fun askNotif() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri != null) runCatching {
            ctx.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(db.exportJson()) }
            notice = "Резервная копия сохранена"
        }.onFailure { notice = "Не удалось сохранить: ${it.message}" }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) runCatching {
            val s = ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: error("Не удалось прочитать файл")
            db.importJson(s)
            detailId = null
            commit()
            notice = "Данные восстановлены"
        }.onFailure { notice = "Ошибка импорта: ${it.message}" }
    }

    val actions = Actions(
        setStatus = { h, d, s ->
            db.setStatus(h.id, d.toString(), s)
            if (s == 1 && d == today) NotificationManagerCompat.from(ctx).cancel(h.id.toInt())
            version++
        },
        open = { detailId = it },
        saveNote = { h, d, t -> db.setNote(h.id, d.toString(), t); version++ },
        toggleFavorite = { h -> db.setFavorite(h.id, !h.favorite); version++ }
    )

    val settingsState = SettingsState(
        look, accentName, fxLevel, particles, notify,
        notifGranted = permTick >= 0 && Reminders.canNotify(ctx),
        exactOk = permTick >= 0 && Reminders.canExact(ctx)
    )
    val settingsActions = SettingsActions(
        onLook = { look = it; prefs.edit().putString("look2", it.name).apply() },
        onAccent = { accentName = it; prefs.edit().putString(accentPrefKey(look), it).apply() },
        onFxLevel = { fxLevel = it; prefs.edit().putInt("fx_level", it).apply() },
        onParticles = { particles = it; prefs.edit().putBoolean("fx_particles", it).apply() },
        onNotify = {
            notify = it
            prefs.edit().putBoolean("notify", it).apply()
            if (it) askNotif()
            Reminders.rescheduleAll(ctx)
        },
        onRequestPermission = {
            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                askNotif()
            } else {
                ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
            }
        },
        onExactAlarms = {
            if (Build.VERSION.SDK_INT >= 31) {
                ctx.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}")))
            }
        },
        onTestNotification = { Reminders.showTest(ctx) },
        onExport = { export.launch("SoftTrack-backup-${LocalDate.now()}.json") },
        onImport = { import.launch(arrayOf("application/json", "text/*")) }
    )

    // экраны поверх основного
    val curDetail = snap.habits.firstOrNull { it.id == detailId }
    val lastDetail = remember { arrayOfNulls<Habit>(1) }
    if (curDetail != null) lastDetail[0] = curDetail
    val editTarget = snap.habits.firstOrNull { it.id == editId }
    val overlayOpen = editing || curDetail != null
    val mainAlpha by animateFloatAsState(if (overlayOpen) 0f else 1f, label = "main-alpha")

    BackHandler(enabled = tab != 0) { tab = 0 }
    BackHandler(enabled = curDetail != null) { detailId = null }
    BackHandler(enabled = editing) { editing = false }

    val view = LocalView.current
    SideEffect {
        val w = (view.context as? Activity)?.window
        if (w != null) {
            val c = WindowCompat.getInsetsController(w, view)
            c.isAppearanceLightStatusBars = p.light
            c.isAppearanceLightNavigationBars = p.light
        }
    }

    val scheme = if (p.light) {
        lightColorScheme(
            primary = p.accent, onPrimary = p.accentInk, background = p.bgTop, surface = p.solid, onSurface = p.ink, onBackground = p.ink,
            surfaceVariant = p.panel2, onSurfaceVariant = p.muted, outline = p.line, surfaceContainer = p.solid, surfaceContainerHigh = p.solid
        )
    } else {
        darkColorScheme(
            primary = p.accent, onPrimary = p.accentInk, background = p.bgTop, surface = p.solid, onSurface = p.ink, onBackground = p.ink,
            surfaceVariant = p.panel2, onSurfaceVariant = p.muted, outline = p.line, surfaceContainer = p.solid, surfaceContainerHigh = p.solid
        )
    }

    MaterialTheme(colorScheme = scheme) {
        Box(Modifier.fillMaxSize()) {
            AuroraBackdrop(p, fx)

            Column(Modifier.fillMaxSize().graphicsLayer { alpha = mainAlpha }.statusBarsPadding()) {
                Header(tab, p)
                Box(Modifier.weight(1f)) {
                    AnimatedContent(
                        targetState = tab, modifier = Modifier.fillMaxSize(),
                        transitionSpec = {
                            if (targetState > initialState) (slideInHorizontally { it / 5 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 5 } + fadeOut())
                            else (slideInHorizontally { -it / 5 } + fadeIn()) togetherWith (slideOutHorizontally { it / 5 } + fadeOut())
                        },
                        label = "tabs"
                    ) { t ->
                        when (t) {
                            0 -> TodayScreen(snap, today, p, actions)
                            1 -> HabitsScreen(snap, today, p, actions)
                            2 -> CalendarScreen(snap, today, p, actions)
                            3 -> StatsScreen(snap, today, p)
                            else -> SettingsScreen(settingsState, settingsActions, p)
                        }
                    }
                    if (tab < 2) {
                        FloatingActionButton(
                            onClick = { editId = null; editing = true },
                            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
                            containerColor = p.accent, contentColor = p.accentInk,
                            shape = if (p.editorial) RoundedCornerShape(5.dp) else CircleShape
                        ) { Icon(Icons.Default.Add, "Добавить привычку") }
                    }
                }
                BottomNav(tab, { tab = it }, p)
            }

            AnimatedVisibility(
                visible = curDetail != null, modifier = Modifier.fillMaxSize(),
                enter = slideInHorizontally { it / 5 } + fadeIn(), exit = slideOutHorizontally { it / 5 } + fadeOut()
            ) {
                Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } }.statusBarsPadding().navigationBarsPadding()) {
                    lastDetail[0]?.let { h ->
                        HabitDetail(
                            h, snap, today, p, actions,
                            onBack = { detailId = null },
                            onEdit = { editId = h.id; editing = true },
                            onArchive = {
                                db.setArchived(h.id, !h.archived, today)
                                commit()
                                if (!h.archived) detailId = null
                            },
                            onDelete = { db.deleteHabit(h.id); detailId = null; commit() }
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = editing, modifier = Modifier.fillMaxSize(),
                enter = slideInVertically { it / 6 } + fadeIn(), exit = slideOutVertically { it / 6 } + fadeOut()
            ) {
                Box(Modifier.fillMaxSize().background(p.bgTop.copy(alpha = if (p.aurora) 0.94f else 1f)).pointerInput(Unit) { detectTapGestures { } }.statusBarsPadding().navigationBarsPadding()) {
                    HabitEditor(
                        existing = editTarget, snap = snap, today = today, p = p,
                        onSave = { h, rules ->
                            db.saveHabit(h, rules, today)
                            editing = false
                            commit()
                            if (h.isGood && h.remindersOn && rules.any { it.minutes >= 0 }) askNotif()
                        },
                        onClose = { editing = false }
                    )
                }
            }

            notice?.let {
                AlertDialog(
                    onDismissRequest = { notice = null }, containerColor = p.solid,
                    confirmButton = { TextButton(onClick = { notice = null }) { Text("Понятно", color = p.accent) } },
                    title = { Text("SoftTrack", color = p.ink) }, text = { Text(it, color = p.muted) }
                )
            }
        }
    }
}

@Composable
private fun Header(tab: Int, p: Palette) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(top = 14.dp, bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StarGlyph(22.dp, p.accent)
            Text("SoftTrack", color = p.ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.padding(start = 6.dp))
        }
        AnimatedContent(targetState = tab, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "title") { t ->
            Column(Modifier.padding(top = 12.dp)) {
                Text(tabNames[t], color = p.ink, fontSize = if (p.editorial) 34.sp else 30.sp, fontWeight = if (p.editorial) FontWeight.Normal else FontWeight.Bold, fontFamily = p.font)
                Text(tabSubtitles[t], color = p.muted, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun BottomNav(selected: Int, onSelect: (Int) -> Unit, p: Palette) {
    val barBg = when {
        p.aurora -> Color(0xE60B0814)
        else -> p.panel
    }
    Row(
        Modifier.fillMaxWidth().background(barBg)
            .then(if (p.editorial) Modifier.border(1.dp, p.line) else Modifier)
            .navigationBarsPadding().padding(horizontal = 6.dp, vertical = 6.dp)
    ) {
        tabNames.forEachIndexed { i, _ ->
            val sel = selected == i
            val tint by animateColorAsState(if (sel) p.accent else p.muted, label = "nav-tint")
            Column(
                Modifier.weight(1f).clip(p.shape(14.dp)).bounceClick { onSelect(i) }.padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(Modifier.height(10.dp), contentAlignment = Alignment.Center) {
                    val starScale by animateFloatAsState(if (sel) 1f else 0f, label = "nav-star")
                    StarGlyph(10.dp, p.accent, modifier = Modifier.graphicsLayer { scaleX = starScale; scaleY = starScale; alpha = starScale })
                }
                Icon(tabIcons[i], tabNames[i], tint = tint, modifier = Modifier.height(22.dp))
                Text(tabNames[i], color = if (sel) p.ink else p.muted, fontSize = 10.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
            }
        }
    }
}
