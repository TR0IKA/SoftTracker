package com.softtrack.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * «Путь»: бесконечное поле. Вперёд и назад по пути идут дни (назад — прошлые, вперёд — будущие),
 * вбок мир замкнут: уходишь влево или вправо и рано или поздно возвращаешься к задачам.
 * Невыполненная задача — маячок. Когда её отмечаешь, на её месте рождается своя, уникальная планета.
 */
private const val STEP = 210f           // расстояние между задачами дня
private const val GATE = 170f           // от отметки дня до первой задачи
private const val CAM_K = 420f          // «высота» камеры
private const val PERIOD = 1600f        // период замыкания мира по горизонтали
private const val FAR = 3200f
private const val TAU = 6.2831855f
private const val PAST = 60
private const val FUTURE = 21

private fun pathX(wy: Float): Float = 150f * sin(wy / 330f) + 60f * sin(wy / 137f + 1.3f)
private fun wrapX(v: Float): Float = v - PERIOD * floor(v / PERIOD + 0.5f)

private class DayInfo(val date: LocalDate, val offset: Int, val startY: Float, val len: Float)
private class Node(val key: String, val habit: Habit, val day: LocalDate, val wy: Float, val done: Boolean, val kind: Int, val pos: Int)
private class Stop(val wy: Float, val lit: Boolean)
private class World(val days: List<DayInfo>, val nodes: List<Node>, val stops: List<Stop>, val minY: Float, val maxY: Float)

private fun buildWorld(snap: Snapshot, today: LocalDate): World {
    val offs = (-PAST..FUTURE).toList()
    val lists = offs.map { o ->
        val d = today.plusDays(o.toLong())
        snap.habits
            .filter { !it.archived && it.isGood && snap.isScheduled(it, d) }
            .sortedWith(compareBy<Habit>({ snap.reminderMinutes(it, d) ?: 1440 }, { it.title }))
    }
    val lens = lists.map { if (it.isEmpty()) 200f else GATE + it.size * STEP }
    val starts = FloatArray(offs.size)
    for (i in PAST + 1 until offs.size) starts[i] = starts[i - 1] + lens[i - 1]
    for (i in PAST - 1 downTo 0) starts[i] = starts[i + 1] - lens[i]
    val days = ArrayList<DayInfo>()
    val nodes = ArrayList<Node>()
    val stops = ArrayList<Stop>()
    for (i in offs.indices) {
        val o = offs[i]
        val d = today.plusDays(o.toLong())
        days.add(DayInfo(d, o, starts[i], lens[i]))
        stops.add(Stop(starts[i], o <= 0))
        lists[i].forEachIndexed { j, hb ->
            val wy = starts[i] + GATE + j * STEP
            val done = snap.isDone(hb, d)
            nodes.add(Node("${hb.id}:${d.toEpochDay()}", hb, d, wy, done, if (o < 0) -1 else if (o == 0) 0 else 1, j))
            stops.add(Stop(wy, done))
        }
    }
    return World(days, nodes, stops, starts[0] - 160f, starts[offs.size - 1] + lens[offs.size - 1] + 100f)
}

private fun dayLabel(d: LocalDate, today: LocalDate): String = when (ChronoUnit.DAYS.between(today, d)) {
    0L -> "Сегодня"
    -1L -> "Вчера"
    -2L -> "Позавчера"
    1L -> "Завтра"
    else -> d.format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale("ru")))
}

private class Star(val x: Float, val y: Float, val size: Float, val layer: Float, val tw: Float)

private class Motion {
    var vx = 0f
    var vy = 0f
    var touching = false
    var lastMove = 0L
    var hasTarget = false
    var tx = 0f
    var ty = 0f
}

// ---------- уникальные планеты ----------

private class Feat(val lon: Float, val lat: Float, val size: Float, val k: Int)

private class Spec(
    val type: Int, val c1: Color, val c2: Color, val c3: Color, val radius: Float,
    val ring: Boolean, val ringColor: Color, val moons: Int, val rotSpeed: Float, val feats: List<Feat>
)

private fun hsv(h: Float, s: Float, v: Float): Color = Color.hsv(((h % 360f) + 360f) % 360f, s.coerceIn(0f, 1f), v.coerceIn(0f, 1f))

/** Вид планеты определяется только числом seed: у каждой выполненной задачи он свой. */
private fun specFor(seed: Long): Spec {
    val r = java.util.Random(seed * 7919L + 13L)
    val type = r.nextInt(6)
    val hue = r.nextFloat() * 360f
    val colors: Triple<Color, Color, Color> = when (type) {
        0, 5 -> Triple(hsv(hue, 0.45f, 0.85f), hsv(hue + 18f, 0.55f, 0.52f), hsv(hue - 15f, 0.3f, 0.97f))
        1 -> {
            val hh = 15f + r.nextFloat() * 30f
            Triple(hsv(hh, 0.35f, 0.62f), hsv(hh, 0.45f, 0.34f), hsv(hh + 8f, 0.2f, 0.82f))
        }
        2 -> Triple(hsv(195f + r.nextFloat() * 30f, 0.22f, 0.96f), hsv(210f, 0.38f, 0.72f), Color.White)
        3 -> Triple(hsv(205f + r.nextFloat() * 20f, 0.72f, 0.72f), hsv(105f + r.nextFloat() * 35f, 0.5f, 0.6f), Color.White)
        else -> Triple(hsv(8f, 0.6f, 0.26f), hsv(22f + r.nextFloat() * 12f, 0.95f, 1f), hsv(40f, 0.8f, 1f))
    }
    val feats = ArrayList<Feat>()
    when (type) {
        0, 5 -> for (i in 0 until 6 + r.nextInt(3)) feats.add(Feat(0f, -0.85f + i * 0.24f + r.nextFloat() * 0.08f, 0.1f + r.nextFloat() * 0.16f, r.nextInt(2)))
        1 -> for (i in 0 until 9) feats.add(Feat(r.nextFloat() * TAU, -0.7f + r.nextFloat() * 1.4f, 0.09f + r.nextFloat() * 0.15f, 0))
        2 -> for (i in 0 until 6) feats.add(Feat(r.nextFloat() * TAU, -0.7f + r.nextFloat() * 1.4f, 0.25f + r.nextFloat() * 0.3f, r.nextInt(6)))
        3 -> {
            for (i in 0 until 6) feats.add(Feat(r.nextFloat() * TAU, -0.65f + r.nextFloat() * 1.3f, 0.2f + r.nextFloat() * 0.25f, 0))
            for (i in 0 until 4) feats.add(Feat(r.nextFloat() * TAU, -0.75f + r.nextFloat() * 1.5f, 0.28f + r.nextFloat() * 0.2f, 1))
        }
        else -> for (i in 0 until 7) feats.add(Feat(r.nextFloat() * TAU, -0.7f + r.nextFloat() * 1.4f, 0.25f + r.nextFloat() * 0.3f, r.nextInt(8)))
    }
    return Spec(
        type, colors.first, colors.second, colors.third, 0.29f + r.nextFloat() * 0.13f,
        type == 5 || r.nextFloat() < 0.22f, lerp(colors.third, colors.first, 0.3f), r.nextInt(3), 0.4f + r.nextFloat() * 1.3f, feats
    )
}

private fun DrawScope.sphere(c: Offset, r: Float, col: Color, lit: Float) {
    drawCircle(
        Brush.radialGradient(
            listOf(lerp(col, Color.White, lit), col, lerp(col, Color.Black, 0.82f)),
            center = Offset(c.x - r * 0.4f, c.y - r * 0.42f), radius = r * 1.8f
        ),
        radius = r, center = c
    )
}

private fun DrawScope.drawRing(c: Offset, r: Float, color: Color, back: Boolean, wide: Float = 1.75f) {
    // эллипс кольца виден как половинки: задняя уходит за шар, передняя перед ним
    val rx = r * wide
    val ry = r * 0.52f
    val path = Path()
    val from = if (back) 180f else 0f
    var first = true
    var a = from
    while (a <= from + 180f) {
        val rad = a * PI.toFloat() / 180f
        val x = c.x + rx * cos(rad)
        val y = c.y + ry * sin(rad) - r * 0.18f * cos(rad)
        if (first) { path.moveTo(x, y); first = false } else path.lineTo(x, y)
        a += 6f
    }
    drawPath(path, color.copy(alpha = if (back) 0.35f else 0.85f), style = Stroke(width = 2.2f * density, cap = StrokeCap.Round))
}

private fun DrawScope.drawPlanet(c: Offset, r: Float, sp: Spec, ph: Float, glow: Color) {
    val rot = ph * TAU * sp.rotSpeed
    drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = 0.32f), Color.Transparent), center = c, radius = r * 2.6f), radius = r * 2.6f, center = c)
    if (sp.ring) drawRing(c, r, sp.ringColor, back = true, wide = 1.9f)
    sphere(c, r, sp.c1, 0.35f)
    val circle = Path().apply { addOval(Rect(c.x - r, c.y - r, c.x + r, c.y + r)) }
    clipPath(circle) {
        when (sp.type) {
            0, 5 -> for (f in sp.feats) {
                val y = c.y + f.lat * r
                val hh = f.size * r
                drawRect(sp.c2.copy(alpha = if (f.k == 0) 0.5f else 0.3f), Offset(c.x - r, y - hh / 2f), Size(2f * r, hh))
                val bx = c.x + sin(rot * 1.3f + f.lat * 5f) * r * 0.55f
                drawOval(sp.c3.copy(alpha = 0.22f), Offset(bx - r * 0.4f, y - hh * 0.3f), Size(r * 0.8f, hh * 0.6f))
            }
            else -> for (f in sp.feats) {
                val ang = f.lon + rot
                val ca = cos(ang)
                if (ca < -0.15f) continue
                val sc = maxOf(0.25f, ca)
                val x = c.x + sin(ang) * r * sqrt(1f - f.lat * f.lat)
                val y = c.y + f.lat * r
                val fr = f.size * r * sc
                when (sp.type) {
                    1 -> {
                        drawCircle(sp.c2.copy(alpha = 0.55f), fr, Offset(x, y))
                        drawCircle(sp.c3.copy(alpha = 0.3f), fr, Offset(x - fr * 0.1f, y - fr * 0.1f), style = Stroke(1.1f * density))
                    }
                    2 -> {
                        val a2 = f.k * 1.05f
                        val len = fr * 1.8f
                        drawLine(sp.c2.copy(alpha = 0.6f), Offset(x, y), Offset(x + cos(a2) * len, y + sin(a2) * len), strokeWidth = 1.3f * density)
                    }
                    3 -> {
                        if (f.k == 0) {
                            drawOval(sp.c2.copy(alpha = 0.92f), Offset(x - fr, y - fr * 0.7f), Size(fr * 2f, fr * 1.4f))
                        } else {
                            drawOval(Color.White.copy(alpha = 0.4f), Offset(x - fr * 1.3f, y - fr * 0.3f), Size(fr * 2.6f, fr * 0.6f))
                        }
                    }
                    else -> {
                        val a2 = f.k * 0.8f
                        val len = fr * 2f
                        val e = Offset(x + cos(a2) * len, y + sin(a2) * len)
                        drawLine(sp.c2.copy(alpha = 0.3f), Offset(x, y), e, strokeWidth = 4.5f * density, cap = StrokeCap.Round)
                        drawLine(sp.c3.copy(alpha = 0.95f), Offset(x, y), e, strokeWidth = 1.6f * density, cap = StrokeCap.Round)
                    }
                }
            }
        }
        if (sp.type == 2) drawCircle(Color.White.copy(alpha = 0.55f), r * 0.5f, Offset(c.x, c.y - r * 0.98f))
        // тень с обратной стороны даёт объём
        drawCircle(
            Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.62f)), center = Offset(c.x - r * 0.45f, c.y - r * 0.45f), radius = r * 1.9f),
            radius = r, center = c
        )
    }
    drawCircle(lerp(sp.c3, Color.White, 0.3f).copy(alpha = 0.45f), radius = r, center = c, style = Stroke(1.3f * density))
    if (sp.ring) drawRing(c, r, sp.ringColor, back = false, wide = 1.9f)
    for (i in 0 until sp.moons) {
        val ang = ph * TAU * (1.4f + i * 0.7f) + i * 2.1f
        val mc = Offset(c.x + cos(ang) * r * (1.9f + i * 0.4f), c.y + sin(ang) * r * (0.55f + 0.12f * i))
        val front = sin(ang) >= 0f
        drawCircle(Color(0xFFD7DAE4).copy(alpha = if (front) 0.95f else 0.5f), radius = r * (0.13f + 0.03f * i), center = mc)
    }
}

private fun DrawScope.drawBeacon(c: Offset, r: Float, kind: Int, col: Color, ph: Float, alpha: Float, pos: Int) {
    when (kind) {
        0 -> {
            val pulse = 0.5f + 0.5f * sin(TAU * ph * 2f + pos)
            drawCircle(Brush.radialGradient(listOf(col.copy(alpha = (0.25f + 0.25f * pulse) * alpha), Color.Transparent), center = c, radius = r * 4f), radius = r * 4f, center = c)
            sphere(c, r, col, 0.5f)
            for (k in 0..1) {
                val t = (ph * 3f + k * 0.5f) % 1f
                drawCircle(col.copy(alpha = (1f - t) * 0.5f * alpha), radius = r * (1.4f + 2.2f * t), center = c, style = Stroke(1.3f * density))
            }
        }
        -1 -> {
            val g = Color(0xFF8A8FA0)
            drawCircle(g.copy(alpha = 0.3f * alpha), radius = r * 0.95f, center = c)
            drawLine(g.copy(alpha = 0.6f * alpha), Offset(c.x - r * 0.4f, c.y - r * 0.4f), Offset(c.x + r * 0.4f, c.y + r * 0.4f), strokeWidth = 1.4f * density)
            drawLine(g.copy(alpha = 0.6f * alpha), Offset(c.x + r * 0.4f, c.y - r * 0.4f), Offset(c.x - r * 0.4f, c.y + r * 0.4f), strokeWidth = 1.4f * density)
        }
        else -> {
            drawCircle(col.copy(alpha = 0.3f * alpha), radius = r, center = c, style = Stroke(1.2f * density))
            drawCircle(col.copy(alpha = 0.3f * alpha), radius = r * 0.25f, center = c)
        }
    }
}

// ---------- экран ----------

@Composable
fun RoadScreen(
    snap: Snapshot, today: LocalDate, p: Palette, a: Actions, fx: Fx,
    selected: String?, onSelect: (String?) -> Unit
) {
    val density = LocalDensity.current.density
    val world = remember(snap, today) { buildWorld(snap, today) }
    val todayNodes = world.nodes.filter { it.kind == 0 }

    var w by remember { mutableFloatStateOf(1f) }
    var h by remember { mutableFloatStateOf(1f) }
    var camX by remember { mutableFloatStateOf(0f) }
    var camY by remember { mutableFloatStateOf(0f) }
    var zoom by remember { mutableFloatStateOf(1f) }
    val m = remember { Motion() }

    fun normX() {
        if (camX > PERIOD / 2f) camX -= PERIOD else if (camX < -PERIOD / 2f) camX += PERIOD
    }
    fun focusOnToday() {
        val n = todayNodes.firstOrNull { !it.done } ?: todayNodes.lastOrNull()
        val wy = n?.wy ?: 0f
        camY = (wy - 150f).coerceIn(world.minY, world.maxY)
        camX = pathX(wy) * 0.8f
    }
    LaunchedEffect(world.nodes.size, today) { focusOnToday() }

    // выбор планеты: камера плавно подъезжает к ней
    LaunchedEffect(selected) {
        val n = world.nodes.firstOrNull { it.key == selected }
        if (n != null) {
            m.tx = camX + wrapX(pathX(n.wy) * 0.9f - camX)
            m.ty = (n.wy - 150f).coerceIn(world.minY, world.maxY)
            m.hasTarget = true
        }
    }

    // физика камеры: небольшая инерция после броска и плавный доезд до цели
    LaunchedEffect(world) {
        var last = 0L
        while (true) {
            val now = withFrameNanos { it }
            val dt = if (last == 0L) 0f else ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
            last = now
            val nowMs = System.nanoTime() / 1_000_000L
            if (m.touching) {
                if (nowMs - m.lastMove > 90L) { m.vx = 0f; m.vy = 0f }
            } else if (m.hasTarget) {
                val k = 1f - exp(-dt * 7f)
                camX += (m.tx - camX) * k
                camY += (m.ty - camY) * k
                normX()
                if (abs(m.tx - camX) < 1.5f && abs(m.ty - camY) < 1.5f) m.hasTarget = false
            } else if (abs(m.vx) + abs(m.vy) > 12f) {
                camX -= m.vx * 0.6f * dt / zoom
                normX()
                camY = (camY + m.vy * 0.6f * dt * CAM_K / (h * 0.57f)).coerceIn(world.minY, world.maxY)
                val decay = exp(-dt * 4.5f)
                m.vx *= decay
                m.vy *= decay
            } else {
                m.vx = 0f
                m.vy = 0f
            }
        }
    }

    val phaseState: State<Float> = if (fx.level > 0) {
        val t = rememberInfiniteTransition(label = "road")
        t.animateFloat(0f, 1f, infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Restart), label = "road-phase")
    } else {
        remember { mutableStateOf(0.25f) }
    }

    val stars = remember {
        val r = java.util.Random(11L)
        List(220) { Star(r.nextFloat(), r.nextFloat(), r.nextFloat(), 0.15f + r.nextFloat() * 0.85f, r.nextFloat()) }
    }

    // перспектива: s = 1 на дистанции 0 от камеры, дальше уменьшается к горизонту
    fun scaleAt(wy: Float): Float {
        val den = (wy - camY) + CAM_K
        return if (den < 40f) -1f else CAM_K / den
    }
    fun sxRaw(wx: Float, s: Float): Float = w / 2f + (wx - camX) * s * zoom
    fun sxWrap(wx: Float, s: Float): Float = w / 2f + wrapX(wx - camX) * s * zoom
    fun sy(s: Float): Float {
        val horizon = h * 0.13f
        val base = h * 0.70f
        return horizon + (base - horizon) * s
    }

    val visibleNodes by remember(world) {
        derivedStateOf { world.nodes.filter { it.wy > camY - CAM_K * 0.75f && it.wy < camY + 2300f }.sortedByDescending { it.wy } }
    }
    val visibleDays by remember(world) {
        derivedStateOf { world.days.filter { it.startY > camY - CAM_K * 0.75f && it.startY < camY + 2300f }.sortedByDescending { it.startY } }
    }
    val chipText by remember(world) {
        derivedStateOf {
            val d = world.days.lastOrNull { it.startY <= camY + 200f } ?: world.days.first()
            val ns = world.nodes.filter { it.day == d.date }
            val label = dayLabel(d.date, today)
            if (ns.isEmpty()) "$label · задач нет" else "$label · ${ns.count { it.done }} из ${ns.size}"
        }
    }

    val shown = remember { mutableStateOf<Node?>(null) }
    val selNode = world.nodes.firstOrNull { it.key == selected }
    if (selNode != null) shown.value = selNode

    Box(
        Modifier.fillMaxSize().clipToBounds()
            .onSizeChanged { w = it.width.toFloat(); h = it.height.toFloat() }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    m.touching = true
                    m.hasTarget = false
                    do {
                        val ev = awaitPointerEvent()
                    } while (ev.changes.any { it.pressed })
                    m.touching = false
                }
            }
            .pointerInput(world) {
                detectTransformGestures { _, pan, zoomChange, _ ->
                    val nowMs = System.nanoTime() / 1_000_000L
                    val dt = (nowMs - m.lastMove).coerceIn(8L, 100L) / 1000f
                    m.lastMove = nowMs
                    m.vx = m.vx * 0.55f + (pan.x / dt) * 0.45f
                    m.vy = m.vy * 0.55f + (pan.y / dt) * 0.45f
                    camX -= pan.x / zoom
                    normX()
                    camY = (camY + pan.y * CAM_K / (h * 0.57f)).coerceIn(world.minY, world.maxY)
                    zoom = (zoom * zoomChange).coerceIn(0.7f, 1.7f)
                }
            }
            .pointerInput(Unit) { detectTapGestures { onSelect(null) } }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val ph = phaseState.value
            drawSpace(stars, camX, camY, ph, p, size.width, size.height, density)
            drawFloor(p, camX, camY, { v -> scaleAt(v) }, { x, s -> sxRaw(x, s) }, { s -> sy(s) }, size.width, size.height, zoom, density)
            drawTrail(p, world.stops, camY, { v -> scaleAt(v) }, { x, s -> sxWrap(x, s) }, { s -> sy(s) }, size.height, density, ph, fx.level > 0)
            for (d in visibleDays) {
                val s = scaleAt(d.startY)
                if (s > 0.05f) {
                    val c = Offset(sxWrap(pathX(d.startY), s), sy(s))
                    val hw = 90f * s * zoom
                    val al = (0.15f + 0.5f * s.coerceIn(0f, 1f)) * (if (d.offset == 0) 1.4f else 1f)
                    val col = p.road ?: p.accent
                    drawLine(col.copy(alpha = al.coerceAtMost(0.9f)), Offset(c.x - hw, c.y), Offset(c.x + hw, c.y), strokeWidth = 1.6f * density, cap = StrokeCap.Round)
                    drawCircle(col.copy(alpha = al.coerceAtMost(0.9f)), 3f * density * s.coerceIn(0.4f, 1.3f), Offset(c.x - hw, c.y))
                    drawCircle(col.copy(alpha = al.coerceAtMost(0.9f)), 3f * density * s.coerceIn(0.4f, 1.3f), Offset(c.x + hw, c.y))
                }
            }
        }

        // отметки дней
        for (d in visibleDays) {
            key(d.date.toEpochDay()) {
                val isToday = d.offset == 0
                Box(
                    Modifier
                        .offset {
                            val s = scaleAt(d.startY)
                            if (s <= 0.05f) IntOffset(-9000, -9000)
                            else IntOffset((sxWrap(pathX(d.startY), s) - 80f * density).roundToInt(), (sy(s) - 34f * density).roundToInt())
                        }
                        .graphicsLayer {
                            val raw = scaleAt(d.startY)
                            val k = raw.coerceIn(0.5f, 1.2f)
                            scaleX = k; scaleY = k
                            alpha = ((raw - 0.1f) * 3f).coerceIn(0f, 1f)
                        }
                        .width(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        dayLabel(d.date, today), color = if (isToday) p.accentInk else p.ink, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                        modifier = Modifier.clip(CircleShape)
                            .background(if (isToday) p.accent else p.bgTop.copy(alpha = 0.6f))
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // задачи: дальние рисуем первыми, чтобы ближние их перекрывали
        for (n in visibleNodes) {
            key(n.key) {
                val isSel = selected == n.key
                Box(
                    Modifier
                        .offset {
                            val s = scaleAt(n.wy)
                            if (s <= 0f) IntOffset(-9000, -9000)
                            else IntOffset((sxWrap(pathX(n.wy), s) - 60f * density).roundToInt(), (sy(s) - 42f * density).roundToInt())
                        }
                        .graphicsLayer {
                            val raw = scaleAt(n.wy)
                            val k = raw.coerceIn(0.3f, 1.7f) * zoom.coerceIn(0.8f, 1.3f)
                            scaleX = k
                            scaleY = k
                            alpha = if (raw <= 0.1f) 0.15f else 0.4f + 0.6f * (raw * 1.4f).coerceIn(0f, 1f)
                            transformOrigin = TransformOrigin(0.5f, 0.37f)
                        }
                ) {
                    NodeView(n, isSel, p, phaseState, { scaleAt(n.wy) }) { onSelect(if (isSel) null else n.key) }
                }
            }
        }

        // верхняя панель: где сейчас камера
        Row(
            Modifier.align(Alignment.TopCenter).padding(top = 6.dp).clip(CircleShape)
                .background(p.panel2.copy(alpha = 0.75f)).padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StarGlyph(14.dp, p.accent)
            Text(chipText, color = p.ink, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 6.dp))
        }
        IconButton(
            onClick = { zoom = 1f; m.hasTarget = false; focusOnToday() },
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 12.dp)
        ) { Icon(Icons.Default.MyLocation, "К сегодняшнему дню", tint = p.accent) }

        if (snap.habits.isEmpty()) {
            Column(Modifier.align(Alignment.Center).padding(horizontal = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Пока пусто", color = p.ink, fontSize = 20.sp, fontFamily = p.font)
                Text("Добавь привычки или выбери готовые из шаблонов. За каждую выполненную задачу здесь появится планета.", color = p.muted, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
                Button(
                    onClick = a.openTemplates, modifier = Modifier.padding(top = 14.dp), shape = p.shape(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = p.accent, contentColor = p.accentInk)
                ) { Text("Выбрать из шаблонов") }
            }
        }

        // карточка выбранной задачи выезжает снизу
        AnimatedVisibility(
            visible = selNode != null, modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(spring(dampingRatio = 0.8f, stiffness = 350f)) { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut()
        ) {
            val cur = shown.value
            if (cur != null) {
                val time = snap.reminderMinutes(cur.habit, cur.day)
                Panel(p, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp), pad = 14.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        HabitAvatar(cur.habit, p, 42.dp, Modifier.padding(end = 12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(cur.habit.title, color = p.ink, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            Text(
                                listOfNotNull(dayLabel(cur.day, today), cur.habit.category, time?.let { fmtTime(it) }).joinToString(" · "),
                                color = p.muted, fontSize = 12.sp
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth()) {
                        if (cur.kind > 0) {
                            OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.weight(1f), shape = p.shape(50.dp)) { Text("Эта задача ещё впереди", color = p.muted) }
                        } else {
                            Button(
                                onClick = { a.setStatus(cur.habit, cur.day, if (cur.done) 0 else 1) }, modifier = Modifier.weight(1f), shape = p.shape(50.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = p.accent, contentColor = p.accentInk)
                            ) {
                                if (cur.done) Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp))
                                Text(if (cur.done) "  Снять отметку" else "Отметить")
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = { a.open(cur.habit.id) }, shape = p.shape(50.dp)) { Text("Открыть", color = p.ink) }
                    }
                }
            }
        }
    }
}

/** Задача на пути: маячок, который при отметке превращается в свою планету. */
@Composable
private fun NodeView(node: Node, selected: Boolean, p: Palette, phase: State<Float>, scaleOf: () -> Float, onClick: () -> Unit) {
    val done = node.done
    val spawn = remember { Animatable(if (done) 1f else 0f) }
    val burst = remember { Animatable(0f) }
    var prev by remember { mutableStateOf(done) }
    LaunchedEffect(done) {
        if (done && !prev) {
            burst.snapTo(0f)
            launch { burst.animateTo(1f, tween(1000, easing = FastOutSlowInEasing)) }
            spawn.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 140f))
        } else if (!done) {
            spawn.animateTo(0f, tween(260))
        }
        prev = done
    }
    val spec = remember(node.key) { specFor(node.habit.id * 100003L + node.day.toEpochDay()) }
    val beacon = p.planetTodo ?: p.accent
    val glow = p.planetDone ?: lerp(spec.c3, spec.c1, 0.4f)
    Column(
        Modifier.width(120.dp)
            .graphicsLayer { translationY = sin(phase.value * TAU * 2f + node.pos * 1.7f) * 3f * density }
            .bounceClick(onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(84.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val ph = phase.value
                val c = Offset(size.width / 2f, size.height / 2f)
                val box = size.minDimension
                val sp = spawn.value
                if (sp < 0.98f) drawBeacon(c, box * 0.14f, node.kind, beacon, ph, 1f - sp, node.pos)
                if (sp > 0.02f) {
                    val r = box * spec.radius * sp
                    drawOval(Color.Black.copy(alpha = 0.3f * sp), topLeft = Offset(c.x - r * 0.9f, c.y + r * 1.2f), size = Size(r * 1.8f, r * 0.45f))
                    drawPlanet(c, r, spec, ph, glow)
                }
                val b = burst.value
                if (b > 0f && b < 1f) {
                    drawCircle(glow.copy(alpha = (1f - b) * 0.7f), radius = box * (0.2f + 0.55f * b), center = c, style = Stroke(2.2f * density))
                    for (k in 0 until 8) {
                        val ang = (k * 45f + 10f) * PI.toFloat() / 180f
                        val dist = box * (0.28f + 0.5f * b)
                        drawStar(Offset(c.x + cos(ang) * dist, c.y + sin(ang) * dist), 4.5f * density * (1f - 0.5f * b), 1f - b, glow)
                    }
                }
                if (selected) drawCircle(p.ink.copy(alpha = 0.85f), radius = box * 0.4f, center = c, style = Stroke(1.4f * density))
            }
            if (!done && node.habit.icon.isNotEmpty()) HabitAvatar(node.habit, p, 26.dp)
        }
        Text(
            node.habit.title, color = if (done) p.muted else p.ink, fontSize = 11.sp, maxLines = 2, lineHeight = 13.sp,
            textAlign = TextAlign.Center, fontWeight = FontWeight.Medium,
            modifier = Modifier
                .graphicsLayer { alpha = ((scaleOf() - 0.4f) * 4f).coerceIn(0f, 1f) }
                .background(p.bgTop.copy(alpha = 0.55f), RoundedCornerShape(8.dp)).padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

private fun DrawScope.drawSpace(stars: List<Star>, camX: Float, camY: Float, phase: Float, p: Palette, w: Float, h: Float, density: Float) {
    // туманности: большие мягкие облака с параллаксом
    val nebula = p.bgPlanet ?: p.accent
    val n1 = Offset(w * (0.78f - camX * 0.00012f), h * (0.22f + camY * 0.00004f))
    drawCircle(Brush.radialGradient(listOf(nebula.copy(alpha = 0.18f), Color.Transparent), center = n1, radius = w * 0.65f), radius = w * 0.65f, center = n1)
    val n2 = Offset(w * (0.12f - camX * 0.0002f), h * (0.5f + camY * 0.00007f))
    drawCircle(Brush.radialGradient(listOf(lerp(nebula, Color(0xFFB03F90), 0.5f).copy(alpha = 0.1f), Color.Transparent), center = n2, radius = w * 0.55f), radius = w * 0.55f, center = n2)
    // свечение у горизонта
    drawRect(Brush.verticalGradient(listOf(Color.Transparent, nebula.copy(alpha = 0.09f), Color.Transparent), startY = h * 0.05f, endY = h * 0.32f), topLeft = Offset(0f, h * 0.05f), size = Size(w, h * 0.27f))
    val tint = p.stars ?: Color.White
    for (s in stars) {
        var x = (s.x - camX * 0.00018f * s.layer) % 1f
        var y = (s.y + camY * 0.00008f * s.layer) % 1f
        if (x < 0f) x += 1f
        if (y < 0f) y += 1f
        val tw = 0.5f + 0.5f * abs(sin(TAU * (phase * 2f + s.tw)))
        val c = Offset(x * w, y * h * 0.85f)
        if (s.size > 0.9f) drawStar(c, (3f + s.size * 3f) * density, 0.4f + 0.5f * tw, tint)
        else drawCircle(tint.copy(alpha = (0.18f + 0.5f * s.layer) * (0.5f + 0.5f * tw)), radius = (0.5f + s.size * 1.1f) * density, center = c)
    }
    // комета раз в несколько секунд
    val t = (phase * 2f) % 1f
    if (t in 0.6f..0.74f) {
        val u = (t - 0.6f) / 0.14f
        val head = Offset(w * (0.95f - 0.65f * u), h * (0.08f + 0.28f * u))
        val tail = Offset(head.x + w * 0.2f, head.y - h * 0.05f)
        drawLine(Brush.linearGradient(listOf(tint.copy(alpha = 0.9f * (1f - u)), Color.Transparent), start = head, end = tail), head, tail, strokeWidth = 2f * density, cap = StrokeCap.Round)
        drawCircle(tint.copy(alpha = 1f - u), radius = 2.2f * density, center = head)
    }
}

/** Лёгкая «пыль» на плоскости: точки сетки уходят к горизонту и показывают глубину и замыкание мира. */
private fun DrawScope.drawFloor(
    p: Palette, camX: Float, camY: Float, scaleAt: (Float) -> Float, sxRaw: (Float, Float) -> Float, sy: (Float) -> Float,
    w: Float, h: Float, zoom: Float, density: Float
) {
    val g = 160f
    val tint = p.stars ?: Color.White
    val horizon = h * 0.13f
    val span = h * 0.57f
    var wy = floor((camY - CAM_K * 0.7f) / g) * g
    val stop = camY + 2400f
    while (wy < stop) {
        val s = scaleAt(wy)
        if (s > 0.06f) {
            val y = sy(s)
            val depth = ((y - horizon) / span).coerceIn(0f, 1f)
            val hw = (w / 2f) / (s * zoom) + g
            val c0 = floor((camX - hw) / g).toInt()
            val c1 = ceil((camX + hw) / g).toInt()
            val stepCols = (c1 - c0) / 48 + 1
            val stagger = if ((floor(wy / g).toInt() and 1) == 0) 0f else g / 2f
            var col = c0
            while (col <= c1) {
                val x = sxRaw(col * g + stagger, s)
                drawCircle(tint.copy(alpha = 0.2f * depth), radius = (0.6f + 1.3f * depth) * density, center = Offset(x, y))
                col += stepCols
            }
        }
        wy += g
    }
}

/** Путь из светящихся точек от задачи к задаче. Пройденные участки горят, впереди тусклее. */
private fun DrawScope.drawTrail(
    p: Palette, stops: List<Stop>, camY: Float, scaleAt: (Float) -> Float, sxw: (Float, Float) -> Float, sy: (Float) -> Float,
    h: Float, density: Float, ph: Float, animated: Boolean
) {
    val col = p.road ?: p.accent
    val lo = camY - CAM_K * 0.78f
    val hi = camY + FAR
    val horizon = h * 0.13f
    val span = h * 0.57f
    for (i in 0 until stops.size - 1) {
        val a = stops[i]
        val b = stops[i + 1]
        if (b.wy < lo || a.wy > hi) continue
        var t = a.wy + 22f
        while (t < b.wy - 22f) {
            if (t > hi) break
            if (t >= lo) {
                val s = scaleAt(t)
                if (s > 0.04f) {
                    val x = sxw(pathX(t), s)
                    val y = sy(s)
                    val depth = ((y - horizon) / span).coerceIn(0f, 1f)
                    val flow = if (animated && a.lit) 0.75f + 0.25f * sin(TAU * (ph * 3f - t / 200f)) else 1f
                    val al = (if (a.lit) 0.9f else 0.3f) * (0.2f + 0.8f * depth) * flow
                    val r = (1.2f + 2.6f * depth) * density
                    if (a.lit) drawCircle(col.copy(alpha = al * 0.18f), radius = r * 3f, center = Offset(x, y))
                    drawCircle(col.copy(alpha = al), radius = r, center = Offset(x, y))
                }
            }
            t += 34f
        }
    }
}
