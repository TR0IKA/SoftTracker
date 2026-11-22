package com.softtrack.app

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * «Путь»: дорога уходит к горизонту, привычки на сегодня лежат на ней по порядку как планеты.
 * Камеру можно двигать пальцем в любую сторону и масштабировать щипком.
 * Мир: wy — вперёд по дороге, wx — вбок. Проекция с перспективой считается вручную.
 */
private const val STEP = 230f          // расстояние между узлами по дороге
private const val CAM_K = 420f         // «высота» камеры: чем больше, тем плавнее перспектива
private const val ROAD_HALF = 64f      // половина ширины дороги
private const val FAR = 3400f          // как далеко рисуем дорогу

private fun roadX(wy: Float): Float = 150f * sin(wy / 330f) + 60f * sin(wy / 137f + 1.3f)

private class Star(val x: Float, val y: Float, val size: Float, val layer: Float, val tw: Float)

@Composable
fun RoadScreen(snap: Snapshot, today: LocalDate, p: Palette, a: Actions, fx: Fx) {
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
    var selected by remember { mutableStateOf<Long?>(null) }

    val endY = (habits.size + 1) * STEP

    fun focusOn(index: Int) {
        camY = (index * STEP + STEP * 0.4f).coerceIn(-120f, endY)
        camX = roadX(camY) * 0.6f
    }
    LaunchedEffect(habits.size) { focusOn(firstOpen) }

    val phase = if (fx.level > 0) {
        val t = rememberInfiniteTransition(label = "road")
        t.animateFloat(0f, 1f, infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Restart), label = "road-phase").value
    } else 0.25f

    val stars = remember {
        val r = java.util.Random(11L)
        List(110) { Star(r.nextFloat(), r.nextFloat(), r.nextFloat(), 0.15f + r.nextFloat() * 0.85f, r.nextFloat()) }
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

    Box(
        Modifier.fillMaxSize().clipToBounds()
            .onSizeChanged { w = it.width.toFloat(); h = it.height.toFloat() }
            .pointerInput(endY) {
                detectTransformGestures { _, pan, zoomChange, _ ->
                    camX = (camX - pan.x / zoom).coerceIn(-520f, 520f)
                    camY = (camY + pan.y * CAM_K / (h * 0.57f)).coerceIn(-120f, endY)
                    zoom = (zoom * zoomChange).coerceIn(0.7f, 1.7f)
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawBackdropSpace(stars, camX, camY, phase, p, size.width, size.height, density)
            drawRoad(p, camY, endY, ::scaleAt, ::sx, ::sy, size.width, size.height, density, firstOpen, doneCount, habits.size, phase, fx.level > 0)
        }

        // узлы дороги: планеты с названиями (рисуем дальние первыми, чтобы ближние перекрывали)
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
                            val k = raw.coerceIn(0.2f, 1.7f) * zoom.coerceIn(0.8f, 1.3f)
                            scaleX = k; scaleY = k
                            alpha = if (raw <= 0.12f) 0.15f else 0.35f + 0.65f * (raw * 1.4f).coerceIn(0f, 1f)
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.37f)
                        }
                ) {
                    Planet(hb, done, isSel, i == firstOpen, p, phase) { selected = if (isSel) null else hb.id }
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
            onClick = { zoom = 1f; focusOn(firstOpen) },
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

        // карточка выбранной точки
        val sel = habits.firstOrNull { it.id == selected }
        if (sel != null) {
            val done = snap.isDone(sel, today)
            val time = snap.reminderMinutes(sel, today)
            Panel(p, Modifier.align(Alignment.BottomCenter).padding(start = 16.dp, end = 16.dp, bottom = 12.dp), pad = 14.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HabitAvatar(sel, p, 42.dp, Modifier.padding(end = 12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(sel.title, color = p.ink, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Text(
                            listOfNotNull(sel.category, time?.let { fmtTime(it) }).joinToString(" · "),
                            color = p.muted, fontSize = 12.sp
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { a.setStatus(sel, today, if (done) 0 else 1) }, modifier = Modifier.weight(1f), shape = p.shape(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = p.accent, contentColor = p.accentInk)
                    ) {
                        if (done) Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp))
                        Text(if (done) "  Снять отметку" else "Отметить")
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = { a.open(sel.id) }, shape = p.shape(50.dp)) { Text("Открыть", color = p.ink) }
                }
            }
        }
    }
}

/** Планета-узел: шар со светом и тенью, кольцо у выполненных, значок привычки поверх. */
@Composable
private fun Planet(hb: Habit, done: Boolean, selected: Boolean, current: Boolean, p: Palette, phase: Float, onClick: () -> Unit) {
    val pulse = 0.5f + 0.5f * sin(2f * PI.toFloat() * phase * 2f)
    val base = if (done) p.accent else lerp(p.panel2, p.accent, 0.18f)
    Column(Modifier.width(120.dp).bounceClick(onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(84.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val c = Offset(size.width / 2f, size.height / 2f)
                val r = size.minDimension * 0.34f
                // ореол
                val haloA = if (done) 0.34f else if (current) 0.18f + 0.18f * pulse else 0.08f
                drawCircle(Brush.radialGradient(listOf(p.accent.copy(alpha = haloA), Color.Transparent), center = c, radius = r * 2.4f), radius = r * 2.4f, center = c)
                // кольцо позади шара
                if (done) drawRing(c, r, p.accent, back = true)
                // тело: свет слева-сверху, тень справа-снизу
                drawCircle(
                    Brush.radialGradient(
                        listOf(lerp(base, Color.White, if (done) 0.55f else 0.14f), base, lerp(base, Color.Black, 0.78f)),
                        center = Offset(c.x - r * 0.38f, c.y - r * 0.42f), radius = r * 1.75f
                    ),
                    radius = r, center = c
                )
                // атмосфера по краю
                drawCircle(p.accent.copy(alpha = if (done) 0.55f else 0.28f), radius = r, center = c, style = Stroke(width = 1.4f * density))
                if (done) drawRing(c, r, p.accent, back = false)
                if (selected) drawCircle(p.ink.copy(alpha = 0.9f), radius = r + 5f * density, center = c, style = Stroke(width = 1.5f * density))
            }
            if (hb.icon.isNotEmpty()) HabitAvatar(hb, p, 34.dp) else if (done) Icon(Icons.Default.Check, null, tint = p.accentInk, modifier = Modifier.size(22.dp))
        }
        Text(
            hb.title, color = if (done) p.muted else p.ink, fontSize = 11.sp, maxLines = 2, lineHeight = 13.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontWeight = FontWeight.Medium
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

private fun DrawScope.drawBackdropSpace(stars: List<Star>, camX: Float, camY: Float, phase: Float, p: Palette, w: Float, h: Float, density: Float) {
    // далёкая планета и туманность, медленно уходят при движении камеры
    val nx = w * (0.78f - camX * 0.00012f)
    val ny = h * (0.2f + camY * 0.00004f)
    drawCircle(Brush.radialGradient(listOf(p.accent.copy(alpha = 0.20f), Color.Transparent), center = Offset(nx, ny), radius = w * 0.62f), radius = w * 0.62f, center = Offset(nx, ny))
    val pr = w * 0.12f
    val pc = Offset(w * (0.18f - camX * 0.0002f), h * (0.12f + camY * 0.00006f))
    drawCircle(
        Brush.radialGradient(listOf(lerp(p.accent, Color.White, 0.35f), lerp(p.accent, Color.Black, 0.55f), Color(0xFF02030A)), center = Offset(pc.x - pr * 0.4f, pc.y - pr * 0.4f), radius = pr * 1.9f),
        radius = pr, center = pc
    )
    drawRing(pc, pr * 0.9f, p.accent.copy(alpha = 0.7f), back = false)
    for (s in stars) {
        var x = (s.x - camX * 0.00018f * s.layer) % 1f
        var y = (s.y + camY * 0.00008f * s.layer) % 1f
        if (x < 0f) x += 1f
        if (y < 0f) y += 1f
        val tw = 0.5f + 0.5f * abs(sin(2f * PI.toFloat() * (phase * 2f + s.tw)))
        val c = Offset(x * w, y * h * 0.85f)
        if (s.size > 0.88f) drawStar(c, (3f + s.size * 3f) * density, 0.4f + 0.5f * tw)
        else drawCircle(Color.White.copy(alpha = (0.18f + 0.5f * s.layer) * (0.5f + 0.5f * tw)), radius = (0.5f + s.size * 1.1f) * density, center = c)
    }
}

private fun DrawScope.drawRoad(
    p: Palette, camY: Float, endY: Float, scaleAt: (Float) -> Float, sx: (Float, Float) -> Float, sy: (Float) -> Float,
    w: Float, h: Float, density: Float, firstOpen: Int, doneCount: Int, total: Int, phase: Float, animated: Boolean
) {
    // выборка точек вдоль дороги от ближней видимой до горизонта; дорога начинается в 0 и заканчивается порталом
    val xs = ArrayList<Float>(); val ls = ArrayList<Float>(); val rs = ArrayList<Float>(); val ys = ArrayList<Float>(); val ws = ArrayList<Float>()
    var wy = maxOf(camY - CAM_K * 0.8f, -STEP * 0.4f)
    val stop = minOf(camY + FAR, endY)
    val step = 22f
    while (wy < stop) {
        val s = scaleAt(wy)
        if (s > 0f) {
            val cx = roadX(wy)
            ws.add(wy); ys.add(sy(s)); xs.add(sx(cx, s))
            ls.add(sx(cx - ROAD_HALF, s)); rs.add(sx(cx + ROAD_HALF, s))
        }
        wy += step
    }
    if (ws.size < 2) return
    val ribbon = Path()
    ribbon.moveTo(ls[0], ys[0])
    for (i in 1 until ws.size) ribbon.lineTo(ls[i], ys[i])
    for (i in ws.size - 1 downTo 0) ribbon.lineTo(rs[i], ys[i])
    ribbon.close()
    drawPath(ribbon, Brush.verticalGradient(listOf(Color.Transparent, p.panel2.copy(alpha = 0.55f), p.panel2.copy(alpha = 0.85f)), startY = 0f, endY = h))

    // освещённая часть: до текущей точки дорога светится ярче
    val litTo = if (total == 0) 0f else firstOpen.coerceAtMost(total) * STEP
    for (side in 0..1) {
        var prev: Offset? = null
        for (i in ws.indices) {
            val pt = Offset(if (side == 0) ls[i] else rs[i], ys[i])
            val pv = prev
            if (pv != null) {
                val lit = ws[i] <= litTo
                val depth = ((ys[i] - h * 0.13f) / (h * 0.57f)).coerceIn(0f, 1f)
                val col = p.accent.copy(alpha = (if (lit) 0.9f else 0.32f) * (0.25f + 0.75f * depth))
                drawLine(col, pv, pt, strokeWidth = (if (lit) 2.6f else 1.6f) * density * (0.4f + 0.6f * depth), cap = StrokeCap.Round)
            }
            prev = pt
        }
    }
    // осевая пунктирная разметка с бегущим светом
    val shift = if (animated) (phase * 2f) % 1f else 0f
    var k = 0
    var i = 0
    while (i + 1 < ws.size) {
        val dash = ((k + shift) % 2f) < 1f
        if (dash) {
            val depth = ((ys[i] - h * 0.13f) / (h * 0.57f)).coerceIn(0f, 1f)
            drawLine(p.ink.copy(alpha = 0.55f * (0.2f + 0.8f * depth)), Offset(xs[i], ys[i]), Offset(xs[i + 1], ys[i + 1]), strokeWidth = 2f * density * (0.4f + 0.6f * depth))
        }
        if (i % 3 == 2) k++
        i++
    }
    // портал в конце пути: ярко горит, когда всё пройдено
    val se = scaleAt(endY)
    if (se > 0f) {
        val ex = roadX(endY)
        val c = Offset(sx(ex, se), sy(se))
        val rr = (sx(ex + ROAD_HALF, se) - c.x) * 1.5f
        val lit = total > 0 && doneCount >= total
        val a0 = if (lit) 0.95f else 0.35f
        drawCircle(Brush.radialGradient(listOf(p.accent.copy(alpha = a0 * 0.5f), Color.Transparent), center = c, radius = rr * 2.2f), radius = rr * 2.2f, center = c)
        drawCircle(p.accent.copy(alpha = a0), radius = rr, center = c, style = Stroke(width = (2.2f + 1.5f * se) * density))
        drawCircle(p.ink.copy(alpha = a0 * 0.6f), radius = rr * 0.72f, center = c, style = Stroke(width = 1.2f * density))
    }
}
