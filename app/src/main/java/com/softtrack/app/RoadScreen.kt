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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
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
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * «Путь»: дорога уходит к горизонту, привычки на сегодня лежат на ней по порядку как планеты.
 * Камеру можно двигать пальцем в любую сторону (с лёгкой инерцией) и масштабировать щипком.
 * Мир: wy — вперёд по дороге, wx — вбок. Перспектива считается вручную.
 */
private const val STEP = 230f          // расстояние между узлами по дороге
private const val CAM_K = 420f         // «высота» камеры: чем больше, тем плавнее перспектива
private const val ROAD_HALF = 70f      // половина ширины дороги
private const val FAR = 3600f          // как далеко рисуем дорогу
private const val TAU = 6.2831855f

private fun roadX(wy: Float): Float = 150f * sin(wy / 330f) + 60f * sin(wy / 137f + 1.3f)

private class Star(val x: Float, val y: Float, val size: Float, val layer: Float, val tw: Float)
private class Body(val wx: Float, val wy: Float, val r: Float, val kind: Int, val ring: Boolean)

private class Motion {
    var vx = 0f
    var vy = 0f
    var touching = false
    var lastMove = 0L
    var hasTarget = false
    var tx = 0f
    var ty = 0f
}

private val bodyColors = listOf(
    Color(0xFFC77B54), Color(0xFF4F9BC9), Color(0xFF9B7BD0), Color(0xFF6FC9A8), Color(0xFFD8B36A), Color(0xFF8E96A8)
)

@Composable
fun RoadScreen(
    snap: Snapshot, today: LocalDate, p: Palette, a: Actions, fx: Fx,
    selected: Long?, onSelect: (Long?) -> Unit
) {
    val density = LocalDensity.current.density
    val habits = remember(snap, today) {
        snap.habits
            .filter { !it.archived && it.isGood && snap.isScheduled(it, today) }
            .sortedWith(compareBy<Habit>({ snap.reminderMinutes(it, today) ?: 1440 }, { it.title }))
    }
    val doneFlags = habits.map { snap.isDone(it, today) }
    val doneCount = doneFlags.count { it }
    val firstOpen = doneFlags.indexOfFirst { !it }.let { if (it < 0) habits.size else it }

    var w by remember { mutableFloatStateOf(1f) }
    var h by remember { mutableFloatStateOf(1f) }
    var camX by remember { mutableFloatStateOf(0f) }
    var camY by remember { mutableFloatStateOf(0f) }
    var zoom by remember { mutableFloatStateOf(1f) }
    val m = remember { Motion() }

    val endY = (habits.size + 1) * STEP

    fun focusOn(index: Int) {
        camY = (index * STEP + STEP * 0.4f).coerceIn(-120f, endY)
        camX = roadX(camY) * 0.6f
    }
    LaunchedEffect(habits.size) { focusOn(firstOpen) }

    // выбор планеты: камера плавно подъезжает к ней
    LaunchedEffect(selected) {
        val idx = habits.indexOfFirst { it.id == selected }
        if (idx >= 0) {
            val wy = (idx + 1) * STEP
            m.tx = roadX(wy) * 0.85f
            m.ty = (wy - 150f).coerceIn(-120f, endY)
            m.hasTarget = true
        }
    }

    // физика камеры: небольшая инерция после броска и плавный доезд до цели
    LaunchedEffect(endY) {
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
                if (abs(m.tx - camX) < 1f && abs(m.ty - camY) < 1f) m.hasTarget = false
            } else if (abs(m.vx) + abs(m.vy) > 12f) {
                camX = (camX - m.vx * 0.6f * dt / zoom).coerceIn(-520f, 520f)
                camY = (camY + m.vy * 0.6f * dt * CAM_K / (h * 0.57f)).coerceIn(-120f, endY)
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
        List(240) { Star(r.nextFloat(), r.nextFloat(), r.nextFloat(), 0.15f + r.nextFloat() * 0.85f, r.nextFloat()) }
    }
    // планеты и астероиды вокруг дороги, часть уходит за рамки экрана
    val bodies = remember(endY) {
        val r = java.util.Random(5L)
        val list = ArrayList<Body>()
        var y = -200f
        var side = 1
        while (y < endY + 2600f) {
            list.add(Body(side * (420f + r.nextFloat() * 1100f), y + r.nextFloat() * 200f, 70f + r.nextFloat() * 140f, r.nextInt(5), r.nextFloat() < 0.4f))
            if (r.nextFloat() < 0.4f) list.add(Body(-side * (500f + r.nextFloat() * 1200f), y + 100f + r.nextFloat() * 250f, 40f + r.nextFloat() * 70f, r.nextInt(5), r.nextFloat() < 0.3f))
            side = -side
            y += 300f + r.nextFloat() * 200f
        }
        for (i in 0 until 110) {
            val sg = if (r.nextBoolean()) 1 else -1
            list.add(Body(sg * (200f + r.nextFloat() * 900f), r.nextFloat() * (endY + 2600f) - 200f, 5f + r.nextFloat() * 11f, 5, false))
        }
        list.sortedByDescending { it.wy }
    }

    // перспектива: s = 1 на дистанции 0 от камеры, дальше уменьшается к горизонту
    fun scaleAt(wy: Float): Float {
        val den = (wy - camY) + CAM_K
        return if (den < 40f) -1f else CAM_K / den
    }
    fun sx(wx: Float, s: Float): Float = w / 2f + (wx - camX) * s * zoom
    fun sy(s: Float): Float {
        val horizon = h * 0.13f
        val base = h * 0.70f
        return horizon + (base - horizon) * s
    }

    val shown = remember { mutableStateOf<Habit?>(null) }
    val sel = habits.firstOrNull { it.id == selected }
    if (sel != null) shown.value = sel

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
            .pointerInput(endY) {
                detectTransformGestures { _, pan, zoomChange, _ ->
                    val nowMs = System.nanoTime() / 1_000_000L
                    val dt = (nowMs - m.lastMove).coerceIn(8L, 100L) / 1000f
                    m.lastMove = nowMs
                    m.vx = m.vx * 0.55f + (pan.x / dt) * 0.45f
                    m.vy = m.vy * 0.55f + (pan.y / dt) * 0.45f
                    camX = (camX - pan.x / zoom).coerceIn(-520f, 520f)
                    camY = (camY + pan.y * CAM_K / (h * 0.57f)).coerceIn(-120f, endY)
                    zoom = (zoom * zoomChange).coerceIn(0.7f, 1.7f)
                }
            }
            .pointerInput(Unit) { detectTapGestures { onSelect(null) } }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val ph = phaseState.value
            drawSpace(stars, camX, camY, ph, p, size.width, size.height, density)
            drawBodies(bodies, p, ::scaleAt, ::sx, ::sy, zoom, size.width, size.height, density)
            drawRoad(p, camY, endY, ::scaleAt, ::sx, ::sy, size.width, size.height, density, firstOpen, doneCount, habits.size, ph, fx.level > 0)
        }

        // узлы дороги: дальние рисуем первыми, чтобы ближние их перекрывали
        habits.withIndex().reversed().forEach { (i, hb) ->
            key(hb.id) {
                val wy = (i + 1) * STEP
                val wx = roadX(wy)
                val done = doneFlags[i]
                val isSel = selected == hb.id
                Box(
                    Modifier
                        .offset {
                            val s = scaleAt(wy)
                            if (s <= 0f) IntOffset(-9000, -9000)
                            else IntOffset((sx(wx, s) - 60f * density).roundToInt(), (sy(s) - 42f * density).roundToInt())
                        }
                        .graphicsLayer {
                            val raw = scaleAt(wy)
                            val k = raw.coerceIn(0.3f, 1.7f) * zoom.coerceIn(0.8f, 1.3f)
                            scaleX = k
                            scaleY = k
                            alpha = if (raw <= 0.1f) 0.15f else 0.4f + 0.6f * (raw * 1.4f).coerceIn(0f, 1f)
                            transformOrigin = TransformOrigin(0.5f, 0.37f)
                        }
                ) {
                    Planet(hb, done, isSel, i == firstOpen, p, phaseState, i) { onSelect(if (isSel) null else hb.id) }
                }
            }
        }

        // верхняя панель прогресса
        Row(
            Modifier.align(Alignment.TopCenter).padding(top = 6.dp).clip(CircleShape)
                .background(p.panel2.copy(alpha = 0.75f)).padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StarGlyph(14.dp, p.accent)
            Text(
                if (habits.isEmpty()) "На сегодня пути нет" else "Пройдено $doneCount из ${habits.size}",
                color = p.ink, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 6.dp)
            )
        }
        IconButton(
            onClick = { zoom = 1f; m.hasTarget = false; focusOn(firstOpen) },
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 12.dp)
        ) { Icon(Icons.Default.MyLocation, "К текущей точке", tint = p.accent) }

        if (habits.isEmpty()) {
            Column(Modifier.align(Alignment.Center).padding(horizontal = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Дорога пуста", color = p.ink, fontSize = 20.sp, fontFamily = p.font)
                Text("Добавь привычки или выбери готовые из шаблонов, и они выстроятся на пути.", color = p.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                Button(
                    onClick = a.openTemplates, modifier = Modifier.padding(top = 14.dp), shape = p.shape(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = p.accent, contentColor = p.accentInk)
                ) { Text("Выбрать из шаблонов") }
            }
        }

        // карточка выбранной точки выезжает снизу
        AnimatedVisibility(
            visible = sel != null, modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(spring(dampingRatio = 0.8f, stiffness = 350f)) { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut()
        ) {
            val cur = shown.value
            if (cur != null) {
                val done = snap.isDone(cur, today)
                val time = snap.reminderMinutes(cur, today)
                Panel(p, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp), pad = 14.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        HabitAvatar(cur, p, 42.dp, Modifier.padding(end = 12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(cur.title, color = p.ink, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            Text(
                                listOfNotNull(cur.category, time?.let { fmtTime(it) }).joinToString(" · "),
                                color = p.muted, fontSize = 12.sp
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { a.setStatus(cur, today, if (done) 0 else 1) }, modifier = Modifier.weight(1f), shape = p.shape(50.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = p.accent, contentColor = p.accentInk)
                        ) {
                            if (done) Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp))
                            Text(if (done) "  Снять отметку" else "Отметить")
                        }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = { a.open(cur.id) }, shape = p.shape(50.dp)) { Text("Открыть", color = p.ink) }
                    }
                }
            }
        }
    }
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

/** Планета-узел: шар со светом и тенью, мягко парит; у выполненных кольцо, у текущей спутник. */
@Composable
private fun Planet(hb: Habit, done: Boolean, selected: Boolean, current: Boolean, p: Palette, phase: State<Float>, idx: Int, onClick: () -> Unit) {
    val pop = remember { Animatable(1f) }
    val burst = remember { Animatable(0f) }
    var prev by remember { mutableStateOf(done) }
    LaunchedEffect(done) {
        if (done && !prev) {
            launch { pop.snapTo(0.78f); pop.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 320f)) }
            burst.snapTo(0f)
            burst.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
        }
        prev = done
    }
    val doneColor = p.planetDone ?: p.accent
    val todoColor = p.planetTodo ?: lerp(p.panel2, p.accent, 0.38f)
    val base = if (done) doneColor else todoColor
    Column(
        Modifier.width(120.dp)
            .graphicsLayer { translationY = sin(phase.value * TAU * 2f + idx * 1.7f) * 3.5f * density }
            .bounceClick(onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(84.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value }, contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val ph = phase.value
                val c = Offset(size.width / 2f, size.height / 2f)
                val r = size.minDimension * 0.34f
                val pulse = 0.5f + 0.5f * sin(TAU * ph * 2f + idx)
                // тень на дороге под планетой
                drawOval(Color.Black.copy(alpha = 0.35f), topLeft = Offset(c.x - r * 0.9f, c.y + r * 1.15f), size = Size(r * 1.8f, r * 0.5f))
                val haloA = if (done) 0.34f else if (current) 0.2f + 0.2f * pulse else 0.1f
                drawCircle(Brush.radialGradient(listOf(base.copy(alpha = haloA), Color.Transparent), center = c, radius = r * 2.5f), radius = r * 2.5f, center = c)
                if (done) drawRing(c, r, base, back = true)
                sphere(c, r, base, if (done) 0.5f else 0.3f)
                drawCircle(lerp(base, Color.White, 0.5f).copy(alpha = if (done) 0.6f else 0.35f), radius = r, center = c, style = Stroke(width = 1.4f * density))
                if (done) drawRing(c, r, base, back = false)
                if (current && !done) {
                    val ang = ph * TAU * 3f
                    val mc = Offset(c.x + cos(ang) * r * 1.9f, c.y + sin(ang) * r * 0.6f)
                    drawCircle(Color.White.copy(alpha = 0.85f), radius = r * 0.16f, center = mc)
                }
                val b = burst.value
                if (b > 0f && b < 1f) {
                    for (k in 0 until 8) {
                        val ang = (k * 45f + 10f) * PI.toFloat() / 180f
                        val dist = r * (1.3f + 1.7f * b)
                        drawStar(Offset(c.x + cos(ang) * dist, c.y + sin(ang) * dist), 4.5f * density * (1f - 0.5f * b), 1f - b, base)
                    }
                }
                if (selected) drawCircle(p.ink.copy(alpha = 0.9f), radius = r + 5f * density, center = c, style = Stroke(width = 1.5f * density))
            }
            if (hb.icon.isNotEmpty()) HabitAvatar(hb, p, 34.dp) else if (done) Icon(Icons.Default.Check, null, tint = p.accentInk, modifier = Modifier.size(22.dp))
        }
        Text(
            hb.title, color = if (done) p.muted else p.ink, fontSize = 11.sp, maxLines = 2, lineHeight = 13.sp,
            textAlign = TextAlign.Center, fontWeight = FontWeight.Medium,
            modifier = Modifier.background(p.bgTop.copy(alpha = 0.55f), RoundedCornerShape(8.dp)).padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

private fun DrawScope.drawRing(c: Offset, r: Float, color: Color, back: Boolean) {
    // эллипс кольца виден как половинки: задняя уходит за шар, передняя перед ним
    val rx = r * 1.75f
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

private fun DrawScope.drawSpace(stars: List<Star>, camX: Float, camY: Float, phase: Float, p: Palette, w: Float, h: Float, density: Float) {
    // туманности: большие мягкие облака с параллаксом
    val nebula = p.bgPlanet ?: p.accent
    val n1 = Offset(w * (0.78f - camX * 0.00012f), h * (0.22f + camY * 0.00004f))
    drawCircle(Brush.radialGradient(listOf(nebula.copy(alpha = 0.20f), Color.Transparent), center = n1, radius = w * 0.65f), radius = w * 0.65f, center = n1)
    val n2 = Offset(w * (0.12f - camX * 0.0002f), h * (0.55f + camY * 0.00007f))
    drawCircle(Brush.radialGradient(listOf(lerp(nebula, Color(0xFFB03F90), 0.5f).copy(alpha = 0.12f), Color.Transparent), center = n2, radius = w * 0.55f), radius = w * 0.55f, center = n2)
    // далёкая планета с кольцом
    val pr = w * 0.12f
    val pc = Offset(w * (0.18f - camX * 0.0002f), h * (0.12f + camY * 0.00006f))
    sphere(pc, pr, lerp(nebula, Color.Black, 0.4f), 0.4f)
    drawRing(pc, pr * 0.9f, nebula.copy(alpha = 0.7f), back = false)
    // звёзды в несколько слоёв
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

/** Планеты и астероиды вокруг дороги. Дальние первыми; часть заходит за край экрана. */
private fun DrawScope.drawBodies(
    bodies: List<Body>, p: Palette, scaleAt: (Float) -> Float, sx: (Float, Float) -> Float, sy: (Float) -> Float,
    zoom: Float, w: Float, h: Float, density: Float
) {
    for (b in bodies) {
        val s = scaleAt(b.wy)
        if (s <= 0.03f || s > 2.4f) continue
        val rad = b.r * s * zoom
        val cx = sx(b.wx, s)
        var cy = sy(s)
        if (b.kind < 5) cy -= rad * 1.15f + 36f * s * density
        if (cx + rad * 2.2f < 0f || cx - rad * 2.2f > w || cy + rad * 2.2f < 0f || cy - rad * 2.2f > h) continue
        val fade = (s * 3f).coerceIn(0.25f, 1f)
        val col = if (b.kind == 0) lerp(p.accent, Color(0xFFC77B54), 0.35f) else bodyColors[b.kind.coerceIn(0, bodyColors.size - 1)]
        val c = Offset(cx, cy)
        if (b.kind < 5) {
            drawCircle(Brush.radialGradient(listOf(col.copy(alpha = 0.16f * fade), Color.Transparent), center = c, radius = rad * 2.1f), radius = rad * 2.1f, center = c)
            if (b.ring) drawRing(c, rad, col, back = true)
            sphere(c, rad, col.copy(alpha = fade), 0.35f)
            if (b.ring) drawRing(c, rad, col, back = false)
        } else {
            drawCircle(col.copy(alpha = 0.55f * fade), radius = maxOf(rad, 0.8f * density), center = c)
        }
    }
}

private fun DrawScope.drawRoad(
    p: Palette, camY: Float, endY: Float, scaleAt: (Float) -> Float, sx: (Float, Float) -> Float, sy: (Float) -> Float,
    w: Float, h: Float, density: Float, firstOpen: Int, doneCount: Int, total: Int, phase: Float, animated: Boolean
) {
    val edge = p.road ?: p.accent
    val horizonY = h * 0.13f
    val span = h * 0.57f
    val startWy = maxOf(camY - CAM_K * 0.8f, -STEP * 0.4f)
    val stop = minOf(camY + FAR, endY)
    val litTo = if (total == 0) 0f else firstOpen.coerceAtMost(total) * STEP

    val xs = ArrayList<Float>(); val ls = ArrayList<Float>(); val rs = ArrayList<Float>()
    val ys = ArrayList<Float>(); val ws = ArrayList<Float>(); val ss = ArrayList<Float>()
    var wy = startWy
    while (wy < stop) {
        val s = scaleAt(wy)
        if (s > 0f) {
            val cx = roadX(wy)
            ws.add(wy); ys.add(sy(s)); xs.add(sx(cx, s)); ss.add(s)
            ls.add(sx(cx - ROAD_HALF, s)); rs.add(sx(cx + ROAD_HALF, s))
        }
        wy += 22f
    }
    if (ws.size < 2) return

    fun pt(wyv: Float, dx: Float): Offset? {
        val s = scaleAt(wyv)
        return if (s <= 0f) null else Offset(sx(roadX(wyv) + dx, s), sy(s))
    }
    fun depthAt(y: Float) = ((y - horizonY) / span).coerceIn(0f, 1f)

    // «толщина» плиты: тёмная полоса под дорогой даёт объём
    val slab = Path()
    slab.moveTo(ls[0], ys[0] + 16f * density * ss[0].coerceAtMost(1.6f))
    for (i in 1 until ws.size) slab.lineTo(ls[i], ys[i] + 16f * density * ss[i].coerceAtMost(1.6f))
    for (i in ws.size - 1 downTo 0) slab.lineTo(rs[i], ys[i] + 16f * density * ss[i].coerceAtMost(1.6f))
    slab.close()
    drawPath(slab, Color.Black.copy(alpha = 0.5f))

    val ribbon = Path()
    ribbon.moveTo(ls[0], ys[0])
    for (i in 1 until ws.size) ribbon.lineTo(ls[i], ys[i])
    for (i in ws.size - 1 downTo 0) ribbon.lineTo(rs[i], ys[i])
    ribbon.close()
    val surface = lerp(p.panel2, edge, 0.1f)
    drawPath(ribbon, Brush.verticalGradient(listOf(Color.Transparent, surface.copy(alpha = 0.6f), surface.copy(alpha = 0.95f)), startY = horizonY, endY = h))

    // поперечные огни: сильно подчёркивают перспективу
    var k = ceil(startWy / 110f).toInt()
    while (k * 110f < stop) {
        val wyk = k * 110f
        val a0 = pt(wyk, -ROAD_HALF)
        val b0 = pt(wyk, ROAD_HALF)
        if (a0 != null && b0 != null) {
            val d = depthAt(a0.y)
            val lit = wyk <= litTo
            val strong = k % 4 == 0
            drawLine(edge.copy(alpha = (if (lit) 0.5f else 0.2f) * (0.1f + 0.9f * d) * (if (strong) 1f else 0.55f)), a0, b0, strokeWidth = (if (strong) 2.4f else 1.3f) * density * (0.35f + 0.65f * d))
        }
        k++
    }

    // боковые кромки: освещённая часть ярче
    for (side in 0..1) {
        var prev: Offset? = null
        for (i in ws.indices) {
            val pt0 = Offset(if (side == 0) ls[i] else rs[i], ys[i])
            val pv = prev
            if (pv != null) {
                val lit = ws[i] <= litTo
                val d = depthAt(ys[i])
                drawLine(edge.copy(alpha = (if (lit) 0.95f else 0.4f) * (0.25f + 0.75f * d)), pv, pt0, strokeWidth = (if (lit) 3.2f else 2f) * density * (0.4f + 0.6f * d), cap = StrokeCap.Round)
            }
            prev = pt0
        }
    }

    // осевой пунктир
    k = floor(startWy / 100f).toInt()
    while (k * 100f < stop) {
        val a0 = pt(k * 100f, 0f)
        val b0 = pt(k * 100f + 40f, 0f)
        if (a0 != null && b0 != null) {
            val d = depthAt(a0.y)
            drawLine(p.ink.copy(alpha = 0.5f * (0.15f + 0.85f * d)), a0, b0, strokeWidth = 2.4f * density * (0.35f + 0.65f * d), cap = StrokeCap.Round)
        }
        k++
    }

    // световые столбы по краям: стоят вертикально, дают глубину
    k = ceil(startWy / 300f).toInt()
    while (k * 300f < stop) {
        val wyk = k * 300f
        val lit = wyk <= litTo
        for (side in intArrayOf(-1, 1)) {
            val base = pt(wyk, side * (ROAD_HALF + 38f))
            val s = scaleAt(wyk)
            if (base != null && s > 0f) {
                val ppu = abs((pt(wyk, side * (ROAD_HALF + 39f))?.x ?: base.x) - base.x).coerceAtLeast(0.2f)
                val top = Offset(base.x, base.y - 70f * ppu)
                val d = depthAt(base.y)
                val col = if (lit) edge else edge.copy(alpha = 0.5f)
                drawLine(col.copy(alpha = 0.6f * (0.2f + 0.8f * d)), base, top, strokeWidth = (2f * ppu).coerceIn(0.6f, 6f * density))
                drawCircle(Brush.radialGradient(listOf(col.copy(alpha = 0.7f * (0.2f + 0.8f * d)), Color.Transparent), center = top, radius = 16f * ppu), radius = 16f * ppu, center = top)
                drawCircle(Color.White.copy(alpha = 0.85f * (0.2f + 0.8f * d)), radius = (3f * ppu).coerceAtLeast(0.6f), center = top)
            }
        }
        k++
    }

    // бегущие по дороге огоньки
    if (animated) {
        for (n in 0 until 6) {
            val t = (phase * 2f + n / 6f) % 1f
            val wyp = camY - CAM_K * 0.5f + t * (stop - camY + CAM_K * 0.5f)
            val c = pt(wyp, 0f)
            if (c != null && wyp < stop && wyp > startWy) {
                val d = depthAt(c.y)
                drawStar(c, (2f + 5f * d) * density, 0.35f + 0.5f * d, edge)
            }
        }
    }

    // портал в конце пути: ярко горит, когда всё пройдено
    val se = scaleAt(endY)
    if (se > 0f) {
        val ex = roadX(endY)
        val c = Offset(sx(ex, se), sy(se))
        val rr = (sx(ex + ROAD_HALF, se) - c.x) * 1.5f
        val lit = total > 0 && doneCount >= total
        val a0 = if (lit) 0.95f else 0.35f
        val spin = if (animated) 0.85f + 0.15f * sin(TAU * phase * 4f) else 1f
        drawCircle(Brush.radialGradient(listOf(edge.copy(alpha = a0 * 0.5f), Color.Transparent), center = c, radius = rr * 2.2f), radius = rr * 2.2f, center = c)
        drawCircle(edge.copy(alpha = a0), radius = rr * spin, center = c, style = Stroke(width = (2.2f + 1.5f * se) * density))
        drawCircle(p.ink.copy(alpha = a0 * 0.6f), radius = rr * 0.72f, center = c, style = Stroke(width = 1.2f * density))
    }
}
