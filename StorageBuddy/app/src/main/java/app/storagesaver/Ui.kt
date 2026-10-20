package app.storagesaver

import android.util.LruCache
import android.util.Size as ASize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object Pal {
    val Bg = Color(0xFF0B0F1A)
    val Card = Color(0xFF141B2D)
    val Track = Color(0xFF182036)
    val Cyan = Color(0xFF00E5FF)
    val Green = Color(0xFF00E5A8)
    val Pink = Color(0xFFFF4D8D)
    val Orange = Color(0xFFFFA726)
    val Yellow = Color(0xFFFFD54F)
    val Dull = Color(0xFF6F6A7E)
    val DullText = Color(0xFF9A98A8)
    val Muted = Color(0xFF8A94AD)
}

fun fmtBytes(b: Long): String {
    val v = b.toDouble()
    return when {
        v >= 1e9 -> String.format(Locale.US, "%.1f GB", v / 1e9)
        v >= 1e6 -> String.format(Locale.US, "%.0f MB", v / 1e6)
        v >= 1e3 -> String.format(Locale.US, "%.0f KB", v / 1e3)
        else -> "$b B"
    }
}

@Composable
fun StorageTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Pal.Green,
            background = Pal.Bg,
            surface = Pal.Card,
        )
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = Pal.Bg, contentColor = Color.White) {
            content()
        }
    }
}

/** 0 se target tak gintee ki tarah chadhta number */
@Composable
fun countUp(target: Long, durationMs: Int = 1400): Long {
    val a = remember { Animatable(0f) }
    LaunchedEffect(target) {
        a.animateTo(target.toFloat(), tween(durationMs, easing = FastOutSlowInEasing))
    }
    return a.value.toLong()
}

@Composable
fun GradientButton(
    text: String,
    colors: List<Color>,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.horizontalGradient(colors))
            .clickable(onClick = onClick)
            .padding(vertical = 18.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
        )
    }
}

object ThumbCache {
    val cache = LruCache<String, ImageBitmap>(150)
}

@Composable
fun Thumb(
    item: MediaItem,
    modifier: Modifier = Modifier,
    saturation: Float = 1f,
    alpha: Float = 1f,
) {
    val ctx = LocalContext.current
    val bmp by produceState<ImageBitmap?>(ThumbCache.cache.get(item.key), item.key) {
        if (value == null) {
            val loaded = withContext(Dispatchers.IO) {
                try {
                    ctx.contentResolver
                        .loadThumbnail(item.uri, ASize(256, 256), null)
                        .asImageBitmap()
                } catch (e: Exception) {
                    null
                }
            }
            if (loaded != null) ThumbCache.cache.put(item.key, loaded)
            value = loaded
        }
    }
    val b = bmp
    if (b != null) {
        val filter = if (saturation >= 0.99f) {
            null
        } else {
            ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(saturation) })
        }
        Image(
            bitmap = b,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alpha = alpha,
            colorFilter = filter,
            modifier = modifier,
        )
    } else {
        Box(modifier.background(Color(0xFF1B2338)))
    }
}
