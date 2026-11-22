package com.softtrack.app

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

enum class Look { AURORA, NOCTURNE, ROAD }

data class Fx(val level: Int, val particles: Boolean)

data class Palette(
    val look: Look,
    val bgTop: Color, val bgBottom: Color,
    val solid: Color, val panel: Color, val panel2: Color,
    val ink: Color, val muted: Color, val line: Color,
    val accent: Color, val accentInk: Color, val danger: Color
) {
    val editorial: Boolean get() = false
    /** Тёмные «звёздные» оформления: стеклянные панели и фон с эффектами. */
    val aurora: Boolean get() = look == Look.AURORA || look == Look.ROAD
    val light: Boolean get() = false
    val font: FontFamily get() = if (editorial) FontFamily.Serif else FontFamily.Default
    fun shape(r: Dp = 17.dp): Shape = if (editorial) RoundedCornerShape(3.dp) else RoundedCornerShape(r)
    val checkShape: Shape get() = if (editorial) RoundedCornerShape(2.dp) else CircleShape
}

fun accentOptions(look: Look): List<Pair<String, Color>> = when (look) {
    Look.AURORA -> listOf(
        "Лаванда" to Color(0xFFB9A4FF), "Сирень" to Color(0xFFD7B4FF), "Слива" to Color(0xFFE08ACB), "Жемчуг" to Color(0xFFEDE8FF),
        "Голубой" to Color(0xFF8FC7FF), "Мятный" to Color(0xFF8FE3C8), "Розовый" to Color(0xFFFF9EC4), "Коралл" to Color(0xFFFF8F7A),
        "Золото" to Color(0xFFE8C468), "Изумруд" to Color(0xFF4FD1A0), "Закат" to Color(0xFFFFA05A), "Серебро" to Color(0xFFC9CCD6)
    )
    Look.NOCTURNE -> listOf(
        "Сланец" to Color(0xFF91AFC2), "Шалфей" to Color(0xFF9DB59A), "Терракота" to Color(0xFFC77F68), "Охра" to Color(0xFFD1B96F),
        "Лёд" to Color(0xFFA8C8E8), "Глициния" to Color(0xFFA99BC9), "Мята" to Color(0xFF8FC9B5), "Роза" to Color(0xFFC98A9A),
        "Бронза" to Color(0xFFB88A5E), "Бирюза" to Color(0xFF6FB3B8), "Лазурь" to Color(0xFF7C9EDB), "Лимон" to Color(0xFFD6D17A)
    )
    Look.ROAD -> listOf(
        "Орбита" to Color(0xFF6FA8FF), "Неон" to Color(0xFF5CE1E6), "Квазар" to Color(0xFFB27CFF), "Рассвет" to Color(0xFFFFB86B),
        "Мята" to Color(0xFF7CF2C0), "Пульсар" to Color(0xFFFF7BD0), "Лёд" to Color(0xFFCFE8FF), "Плазма" to Color(0xFFFF6B6B),
        "Солнце" to Color(0xFFFFD95E), "Комета" to Color(0xFF9BF0FF), "Ржавчина" to Color(0xFFE08A5A), "Пыль" to Color(0xFFD9D4FF)
    )
}

fun accentPrefKey(look: Look) = when (look) {
    Look.AURORA -> "accent_aurora"
    Look.NOCTURNE -> "accent"
    Look.ROAD -> "accent_road"
}

/** Свой цвет хранится в имени акцента как «#RRGGBB». */
fun parseCustomAccent(name: String): Color? =
    if (name.startsWith("#")) runCatching { Color(android.graphics.Color.parseColor(name)) }.getOrNull() else null

fun resolveAccent(look: Look, accentName: String): Color {
    val opts = accentOptions(look)
    return parseCustomAccent(accentName) ?: (opts.firstOrNull { it.first == accentName } ?: opts.first()).second
}

fun buildPalette(look: Look, accentName: String): Palette {
    val accent = resolveAccent(look, accentName)
    val ink = if (accent.luminance() > 0.35f) Color(0xFF14101F) else Color.White
    fun tint(base: Color, k: Float) = lerp(base, accent, k)
    return when (look) {
        // фон, панели и линии подкрашиваются выбранным акцентом
        Look.AURORA -> Palette(look, tint(Color(0xFF060509), 0.03f), tint(Color(0xFF15101F), 0.17f), tint(Color(0xFF17131F), 0.08f),
            tint(Color(0xFF17131F), 0.08f).copy(alpha = 0.72f), tint(Color(0xFF231D2E), 0.10f),
            Color(0xFFF5F2FA), Color(0xFFA59DB5), tint(Color(0xFF2E2840), 0.16f), accent, ink, Color(0xFFE59AAE))
        Look.ROAD -> Palette(look, tint(Color(0xFF03040A), 0.02f), tint(Color(0xFF0A1022), 0.14f), tint(Color(0xFF0E1426), 0.07f),
            tint(Color(0xFF0E1426), 0.07f).copy(alpha = 0.72f), tint(Color(0xFF171F36), 0.10f),
            Color(0xFFEAF1FF), Color(0xFF8FA0C0), tint(Color(0xFF243050), 0.16f), accent, ink, Color(0xFFFF8A8A))
        Look.NOCTURNE -> Palette(look, Color(0xFF101722), Color(0xFF101722), Color(0xFF192331), Color(0xFF192331), Color(0xFF202C3B),
            Color(0xFFF1F3F5), Color(0xFF98A6B8), Color(0xFF344253), accent, Color(0xFF101722), Color(0xFFD98C82))
    }
}

// ---------- четырёхконечная звезда ----------

fun DrawScope.drawStar(c: Offset, r: Float, alpha: Float = 1f, tint: Color = Color(0xFFD9CCFF)) {
    drawCircle(
        Brush.radialGradient(listOf(tint.copy(alpha = 0.26f * alpha), Color.Transparent), center = c, radius = r * 3.2f),
        radius = r * 3.2f, center = c
    )
    val k = 0.16f
    val path = Path().apply {
        moveTo(c.x, c.y - r)
        quadraticTo(c.x + r * k, c.y - r * k, c.x + r, c.y)
        quadraticTo(c.x + r * k, c.y + r * k, c.x, c.y + r)
        quadraticTo(c.x - r * k, c.y + r * k, c.x - r, c.y)
        quadraticTo(c.x - r * k, c.y - r * k, c.x, c.y - r)
        close()
    }
    drawPath(
        path,
        Brush.linearGradient(
            listOf(Color.White.copy(alpha = alpha), tint.copy(alpha = alpha)),
            start = Offset(c.x - r, c.y - r), end = Offset(c.x + r, c.y + r)
        )
    )
    drawCircle(Color.White.copy(alpha = alpha), radius = r * 0.16f, center = c)
}

@Composable
fun StarGlyph(size: Dp, tint: Color = Color(0xFFD9CCFF), modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) { drawStar(Offset(this.size.width / 2, this.size.height / 2), this.size.minDimension * 0.42f, 1f, tint) }
}

// ---------- фон: свечение, звёзды, частицы ----------

private class Spark(val x: Float, val y: Float, val size: Float, val k: Int, val off: Float, val tw: Int)

@Composable
fun AuroraBackdrop(p: Palette, fx: Fx, modifier: Modifier = Modifier, hasImage: Boolean = false) {
    if (!p.aurora) {
        if (!hasImage) Box(modifier.background(p.bgTop))
        return
    }
    val animated = fx.level > 0
    val phaseState: State<Float> = if (animated) {
        val t = rememberInfiniteTransition(label = "aurora")
        t.animateFloat(0f, 1f, infiniteRepeatable(tween(90000, easing = LinearEasing)), label = "phase")
    } else {
        remember { mutableStateOf(0.25f) }
    }
    val sparks = remember {
        val r = java.util.Random(7L)
        List(44) { Spark(r.nextFloat(), r.nextFloat(), r.nextFloat(), 1 + r.nextInt(3), r.nextFloat(), 8 + r.nextInt(18)) }
    }
    val stars = remember {
        val r = java.util.Random(21L)
        List(14) { Spark(0.06f + r.nextFloat() * 0.88f, 0.05f + r.nextFloat() * 0.9f, r.nextFloat(), 1 + r.nextInt(2), r.nextFloat(), 6 + r.nextInt(10)) }
    }
    Canvas(modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        if (!hasImage) drawRect(Brush.verticalGradient(listOf(p.bgTop, p.bgBottom)))
        if (fx.level == 0) return@Canvas
        val ph = phaseState.value
        val a = 2f * PI.toFloat() * ph
        val glow = 0.08f + 0.04f * fx.level
        fun blob(cx: Float, cy: Float, r: Float, c: Color) {
            drawCircle(Brush.radialGradient(listOf(c.copy(alpha = glow), Color.Transparent), center = Offset(cx, cy), radius = r), radius = r, center = Offset(cx, cy))
        }
        blob(w * (0.2f + 0.12f * sin(a)), h * (0.16f + 0.07f * cos(a)), w * 0.95f, lerp(Color(0xFF05040A), p.accent, 0.85f))
        blob(w * (0.9f + 0.08f * cos(a)), h * (0.55f + 0.08f * sin(a)), w * 0.85f, lerp(Color(0xFF05040A), lerp(p.accent, Color.White, 0.2f), 0.7f))
        blob(w * (0.3f + 0.14f * sin(2f * a + 1f)), h * (0.98f + 0.03f * cos(a)), w * 0.95f, lerp(Color(0xFF05040A), p.accent, 0.6f))
        if (!fx.particles) return@Canvas
        val nStars = 2 + fx.level * 2
        val nSparks = 6 + fx.level * 8
        for (i in 0 until minOf(nSparks, sparks.size)) {
            val s = sparks[i]
            val yy = (((s.y - ph * s.k) % 1f) + 1f) % 1f
            val xx = s.x + 0.02f * sin(a * s.k + s.off * 6.28f)
            val tw = 0.35f + 0.65f * abs(sin(PI.toFloat() * (ph * s.tw + s.off)))
            drawCircle(Color(0xFFE6DDFF).copy(alpha = 0.36f * tw), radius = (0.7f + s.size * 1.1f) * density, center = Offset(xx * w, yy * h))
        }
        for (i in 0 until minOf(nStars, stars.size)) {
            val s = stars[i]
            val tw = 0.45f + 0.55f * abs(sin(PI.toFloat() * (ph * s.tw + s.off)))
            val bob = 5f * density * sin(a * s.k + s.off * 6.28f)
            drawStar(Offset(s.x * w, s.y * h + bob), (3f + s.size * 3.5f) * density, tw * 0.9f)
        }
    }
}

// ---------- общие компоненты ----------

fun Modifier.appear(index: Int): Modifier = composed {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index.coerceAtMost(12) * 45L)
        a.animateTo(1f, tween(450, easing = FastOutSlowInEasing))
    }
    graphicsLayer {
        alpha = a.value
        translationY = (1f - a.value) * 28.dp.toPx()
    }
}

fun Modifier.bounceClick(onClick: () -> Unit): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.96f else 1f, spring(dampingRatio = 0.5f, stiffness = 500f), label = "press")
    graphicsLayer { scaleX = s; scaleY = s }.clickable(interactionSource = src, indication = null, onClick = onClick)
}

@Composable
fun Panel(
    p: Palette,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    pad: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = p.shape(18.dp)
    val base = modifier.fillMaxWidth()
        .then(if (onClick != null) Modifier.bounceClick(onClick) else Modifier)
        .clip(shape)
        .then(
            if (p.aurora) Modifier.background(Brush.verticalGradient(listOf(lerp(Color(0xD0211B2C), p.accent.copy(alpha = 0.82f), 0.07f), lerp(Color(0xD017131F), p.accent.copy(alpha = 0.82f), 0.04f))))
            else Modifier.background(p.panel)
        )
        .then(
            if (p.aurora) Modifier.border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.03f))), shape)
            else if (p.editorial) Modifier.border(1.dp, p.line, shape)
            else Modifier
        )
    Column(base.padding(pad), content = content)
}

@Composable
fun SectionLabel(label: String, p: Palette, modifier: Modifier = Modifier) {
    Text(label, modifier.padding(bottom = 9.dp), color = p.muted, fontSize = 12.sp, letterSpacing = 0.4.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
fun CheckButton(done: Boolean, p: Palette, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val prev = remember { mutableStateOf(done) }
    val burst = remember { Animatable(0f) }
    val pop = remember { Animatable(1f) }
    LaunchedEffect(done) {
        if (done && !prev.value) {
            launch { pop.snapTo(0.7f); pop.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 400f)) }
            burst.snapTo(0f)
            burst.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
        }
        prev.value = done
    }
    val fill by animateColorAsState(if (done) p.accent else Color.Transparent, tween(250), label = "check-fill")
    Box(modifier.size(32.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(30.dp)
                .graphicsLayer { scaleX = pop.value; scaleY = pop.value }
                .clip(p.checkShape)
                .background(fill)
                .border(1.5.dp, if (done) p.accent else p.muted.copy(alpha = 0.7f), p.checkShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            if (done) Icon(Icons.Default.Check, null, tint = p.accentInk, modifier = Modifier.size(18.dp))
        }
        if (burst.value > 0f && burst.value < 1f) {
            Canvas(Modifier.requiredSize(84.dp)) {
                val b = burst.value
                val c = Offset(size.width / 2, size.height / 2)
                for (i in 0 until 6) {
                    val ang = (i * 60f + 15f) * PI.toFloat() / 180f
                    val dist = size.width * 0.42f * b
                    drawStar(Offset(c.x + cos(ang) * dist, c.y + sin(ang) * dist), 4.5f * density * (1f - 0.6f * b), 1f - b, p.accent)
                }
            }
        }
    }
}

@Composable
fun ProgressRing(progress: Float, p: Palette, size: Dp, stroke: Dp, content: @Composable BoxScope.() -> Unit) {
    val anim by animateFloatAsState(progress.coerceIn(0f, 1f), tween(900, easing = FastOutSlowInEasing), label = "ring")
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = stroke.toPx()
            val inset = sw / 2f
            val arcSize = Size(this.size.width - sw, this.size.height - sw)
            drawArc(p.line, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(sw, cap = StrokeCap.Round))
            if (anim > 0f) {
                rotate(-90f) {
                    drawArc(
                        Brush.sweepGradient(listOf(p.accent.copy(alpha = 0.45f), p.accent)),
                        0f, 360f * anim, false, Offset(inset, inset), arcSize, style = Stroke(sw, cap = StrokeCap.Round)
                    )
                }
                if (p.aurora && anim > 0.02f) {
                    val rad = (this.size.width - sw) / 2f
                    val ang = (-90f + 360f * anim) * PI.toFloat() / 180f
                    val cx = this.size.width / 2f
                    val cy = this.size.height / 2f
                    drawStar(Offset(cx + cos(ang) * rad, cy + sin(ang) * rad), sw * 0.85f, 1f, p.accent)
                }
            }
        }
        content()
    }
}

/** values: доля 0..1 или null, если день не запланирован. */
@Composable
fun BarChart(values: List<Float?>, labels: List<String>, p: Palette, modifier: Modifier = Modifier, height: Dp = 130.dp) {
    val prog = remember(values) { Animatable(0f) }
    LaunchedEffect(values) { prog.animateTo(1f, tween(800, easing = FastOutSlowInEasing)) }
    Column(modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val n = values.size.coerceAtLeast(1)
            val slot = size.width / n
            val barW = slot * 0.58f
            val base = size.height
            drawLine(p.line, Offset(0f, base), Offset(size.width, base), strokeWidth = 1.dp.toPx())
            values.forEachIndexed { i, v ->
                val x = i * slot + (slot - barW) / 2f
                if (v == null) {
                    drawCircle(p.line, radius = 1.5.dp.toPx(), center = Offset(x + barW / 2f, base - 3.dp.toPx()))
                } else {
                    val bh = maxOf(v * (size.height - 6.dp.toPx()) * prog.value, 3.dp.toPx())
                    drawRoundRect(
                        Brush.verticalGradient(listOf(p.accent, p.accent.copy(alpha = 0.35f)), startY = base - bh, endY = base),
                        topLeft = Offset(x, base - bh), size = Size(barW, bh),
                        cornerRadius = CornerRadius(minOf(barW / 2f, 6.dp.toPx()))
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            labels.forEach { Text(it, Modifier.weight(1f), color = p.muted, fontSize = 9.sp, maxLines = 1, softWrap = false, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
        }
    }
}
