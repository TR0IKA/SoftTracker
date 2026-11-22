package com.softtrack.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/** Картинки приложения лежат в закрытой папке приложения: фон и фото привычек. */
object Imgs {
    const val BG = "bg.jpg"
    fun photoName(id: Long) = "habit_$id.jpg"

    fun decode(ctx: Context, uri: Uri, maxSide: Int): Bitmap? = runCatching {
        if (Build.VERSION.SDK_INT >= 28) {
            val src = ImageDecoder.createSource(ctx.contentResolver, uri)
            ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val m = maxOf(info.size.width, info.size.height)
                if (m > maxSide) {
                    val k = maxSide.toFloat() / m
                    decoder.setTargetSize((info.size.width * k).toInt().coerceAtLeast(1), (info.size.height * k).toInt().coerceAtLeast(1))
                }
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        }
    }.getOrNull()

    fun save(ctx: Context, name: String, bmp: Bitmap): Boolean = runCatching {
        FileOutputStream(File(ctx.filesDir, name)).use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
    }.getOrDefault(false)

    fun load(ctx: Context, name: String): ImageBitmap? = runCatching {
        val f = File(ctx.filesDir, name)
        if (f.exists()) BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap() else null
    }.getOrNull()

    fun delete(ctx: Context, name: String) {
        runCatching { File(ctx.filesDir, name).delete() }
    }
}

val habitEmojis = listOf(
    "💧", "🦷", "🧴", "☀️", "🌙", "😴", "🚶", "🏃", "🏋️", "🧘", "🍎", "🥗", "☕", "📖", "✍️", "🎧",
    "🎨", "💻", "🧠", "💊", "🧹", "👕", "💇", "🪥", "🌿", "🐾", "💰", "📞", "🎯", "⭐", "🔥", "❤️"
)

/** Значок привычки: эмодзи или своё фото. Если значок не задан, ничего не рисует. */
@Composable
fun HabitAvatar(h: Habit, p: Palette, size: Dp, modifier: Modifier = Modifier) {
    if (h.icon.isEmpty()) return
    val ctx = androidx.compose.ui.platform.LocalContext.current
    if (h.icon.startsWith("photo")) {
        val bmp by produceState<ImageBitmap?>(null, h.id, h.icon) {
            value = withContext(Dispatchers.IO) { Imgs.load(ctx, Imgs.photoName(h.id)) }
        }
        val b = bmp
        if (b != null) {
            Image(b, null, modifier.size(size).clip(CircleShape), contentScale = ContentScale.Crop)
        } else {
            Box(modifier.size(size).clip(CircleShape).background(p.panel2))
        }
    } else {
        Box(modifier.size(size).clip(CircleShape).background(p.panel2), contentAlignment = Alignment.Center) {
            Text(h.icon, fontSize = (size.value * 0.5f).sp)
        }
    }
}
