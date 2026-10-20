package app.storagesaver

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class Screen { HOME, CLEAN }

// ───────────────────────────── root ─────────────────────────────

@Composable
fun App(
    vm: MainVm,
    onAllow: () -> Unit,
    onAppSettings: () -> Unit,
    onOpenStorage: () -> Unit,
    onDelete: () -> Unit,
) {
    var screen by remember { mutableStateOf(Screen.HOME) }
    BackHandler(enabled = screen == Screen.CLEAN) { screen = Screen.HOME }
    LaunchedEffect(vm.celebrate) {
        if (vm.celebrate != null) screen = Screen.HOME
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0E1530), Pal.Bg)))
            .systemBarsPadding()
    ) {
        Crossfade(targetState = vm.phase, label = "phase") { p ->
            when (p) {
                Phase.NEEDS_PERMISSION -> PermissionScreen(onAllow, onAppSettings)
                Phase.SCANNING -> ScanningScreen()
                Phase.READY -> {
                    val r = vm.result
                    if (r != null) {
                        if (screen == Screen.HOME) {
                            HomeScreen(
                                r = r,
                                onClean = { screen = Screen.CLEAN },
                                onOpenStorage = onOpenStorage,
                                onRescan = { vm.scan() },
                            )
                        } else {
                            CleanScreen(vm, r, onBack = { screen = Screen.HOME }, onDelete = onDelete)
                        }
                    }
                }
            }
        }
        val freed = vm.celebrate
        if (freed != null) {
            CelebrationOverlay(freed) { vm.celebrate = null }
        }
    }
}

// ───────────────────────── permission + scanning ─────────────────────────

@Composable
fun PulseOrb(modifier: Modifier = Modifier) {
    val inf = rememberInfiniteTransition(label = "orb")
    val t by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "t")
    Canvas(modifier) {
        val maxR = size.minDimension / 2f
        for (i in 0..2) {
            val p = (t + i / 3f) % 1f
            drawCircle(
                color = Pal.Cyan.copy(alpha = (1f - p) * 0.5f),
                radius = maxR * (0.35f + 0.65f * p),
                center = center,
                style = Stroke(width = 3.dp.toPx()),
            )
        }
        drawCircle(
            brush = Brush.radialGradient(colors = listOf(Pal.Cyan, Pal.Green), center = center, radius = maxR * 0.4f),
            radius = maxR * 0.34f,
            center = center,
        )
    }
}

@Composable
fun PermissionScreen(onAllow: () -> Unit, onAppSettings: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        PulseOrb(Modifier.size(190.dp))
        Spacer(Modifier.height(28.dp))
        Text("Storage Buddy", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Text(
            "Tere phone ki photos, videos aur audio dekhne ki permission chahiye, taaki pata chale kya fultu hai. " +
                "Kuch bhi bina tere haan kahe delete nahi hota.",
            color = Pal.Muted,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        GradientButton("Permission do ✨", listOf(Pal.Cyan, Pal.Green), onClick = onAllow)
        TextButton(onClick = onAppSettings) {
            Text("Dialog nahi aaya? App settings kholo", color = Pal.Muted, fontSize = 13.sp)
        }
    }
}

@Composable
fun ScanningScreen() {
    val inf = rememberInfiniteTransition(label = "scan")
    val a by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "a")
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Canvas(Modifier.size(220.dp)) {
            val r = size.minDimension / 2f
            drawCircle(color = Pal.Track, radius = r, style = Stroke(width = 2.dp.toPx()))
            drawCircle(color = Pal.Track, radius = r * 0.66f, style = Stroke(width = 2.dp.toPx()))
            drawCircle(color = Pal.Track, radius = r * 0.33f, style = Stroke(width = 2.dp.toPx()))
            rotate(degrees = a, pivot = center) {
                drawCircle(
                    brush = Brush.sweepGradient(
                        colors = listOf(Color.Transparent, Pal.Cyan.copy(alpha = 0.75f)),
                        center = center,
                    ),
                    radius = r,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Text("Scan ho raha hai…", fontSize = 18.sp, fontWeight = FontWeight.Medium)
        Text("Photos, videos aur duplicates dhundh raha hu", color = Pal.Muted, fontSize = 13.sp)
    }
}

// ───────────────────────────── home ─────────────────────────────

@Composable
fun HomeScreen(
    r: ScanResult,
    onClean: () -> Unit,
    onOpenStorage: () -> Unit,
    onRescan: () -> Unit,
) {
    val info = r.storage
    val junk = r.junkBytes
    val essential = (info.used - junk).coerceAtLeast(0L)
    val media = r.sizes.photos + r.sizes.videos + r.sizes.audio
    val other = (info.used - media).coerceAtLeast(0L)
    val maxBar = maxOf(r.sizes.photos, r.sizes.videos, r.sizes.audio, other, 1L)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("Storage Buddy", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text("Dekh kya zaroori hai, kya fultu", color = Pal.Muted, fontSize = 13.sp)
            }
            TextButton(onClick = onRescan) { Text("↻ Rescan", color = Pal.Cyan) }
        }

        Spacer(Modifier.height(8.dp))
        StorageRing(info, junk, Modifier.fillMaxWidth().padding(horizontal = 12.dp))
        Spacer(Modifier.height(8.dp))

        Row(Modifier.fillMaxWidth()) {
            LegendItem("Zaroori", essential, Pal.Green, Pal.Green, true, Modifier.weight(1f))
            LegendItem("Fultu", junk, Pal.Dull, Pal.DullText, false, Modifier.weight(1f))
            LegendItem("Khaali", info.free, Color(0xFF3B4663), Color.White, false, Modifier.weight(1f))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Chamakta = zaroori, rakh. Dull = fultu, hata sakte ho.",
            color = Pal.Muted,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(20.dp))
        CleanCta(junk, onClean)

        Spacer(Modifier.height(24.dp))
        Text("Storage kahan gaya?", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        CategoryBar("🖼", "Photos", r.sizes.photos, maxBar, Pal.Pink, 0)
        CategoryBar("🎬", "Videos", r.sizes.videos, maxBar, Pal.Orange, 150)
        CategoryBar("🎵", "Audio", r.sizes.audio, maxBar, Pal.Cyan, 300)
        CategoryBar("⚙️", "Apps, system aur baaki", other, maxBar, Pal.Yellow, 450)

        Spacer(Modifier.height(18.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Pal.Card)
                .clickable(onClick = onOpenStorage)
                .padding(16.dp)
        ) {
            Column {
                Text("📱 Apps ki cache Settings mein dekho", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Dusre apps ki cache Android kisi app ko clear karne nahi deta, isliye wo phone ki " +
                        "Settings se hi hoti hai. Yahan tap karke seedha Storage settings khul jayegi.",
                    color = Pal.Muted,
                    fontSize = 12.sp,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun StorageRing(info: StorageInfo, junk: Long, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(info.free, junk) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(1600, easing = FastOutSlowInEasing))
    }
    val inf = rememberInfiniteTransition(label = "ring")
    val spin by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(3500, easing = LinearEasing)), label = "spin")
    val glow by inf.animateFloat(
        0.25f, 0.7f,
        infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow",
    )
    val breathe by inf.animateFloat(
        0.4f, 0.75f,
        infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breathe",
    )

    val total = info.total.toFloat().coerceAtLeast(1f)
    val junkFrac = (junk / total).coerceIn(0f, 1f)
    val essFrac = ((info.used - junk).coerceAtLeast(0L) / total).coerceIn(0f, 1f)
    val freeShown = countUp(info.free)

    Box(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(24.dp)) {
            val stroke = 26.dp.toPx()
            val tl = Offset(stroke / 2f, stroke / 2f)
            val arcSize = Size(size.width - stroke, size.height - stroke)

            // khaali track
            drawArc(
                color = Pal.Track,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = tl,
                size = arcSize,
                style = Stroke(width = stroke),
            )

            val essSweep = 360f * essFrac * progress.value
            val junkSweep = if (junk > 0L) max(6f, 360f * junkFrac) * progress.value else 0f

            if (essSweep > 0.5f) {
                // zaroori hissa: glow + gradient + chamak
                drawArc(
                    color = Pal.Cyan.copy(alpha = glow * 0.4f),
                    startAngle = -90f,
                    sweepAngle = essSweep,
                    useCenter = false,
                    topLeft = tl,
                    size = arcSize,
                    style = Stroke(width = stroke + 14.dp.toPx()),
                )
                rotate(degrees = -90f, pivot = center) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = listOf(Pal.Cyan, Pal.Green, Pal.Cyan),
                            center = center,
                        ),
                        startAngle = 0f,
                        sweepAngle = essSweep,
                        useCenter = false,
                        topLeft = tl,
                        size = arcSize,
                        style = Stroke(width = stroke),
                    )
                }
                if (essSweep > 30f) {
                    val s = (spin / 360f) * (essSweep - 20f)
                    drawArc(
                        color = Color.White.copy(alpha = 0.5f),
                        startAngle = -90f + s,
                        sweepAngle = 20f,
                        useCenter = false,
                        topLeft = tl,
                        size = arcSize,
                        style = Stroke(width = stroke),
                    )
                }
            }

            if (junkSweep > 0.5f) {
                // fultu hissa: dull, dashed, dheere dheere saans leta hua
                drawArc(
                    color = Pal.Dull.copy(alpha = breathe),
                    startAngle = -90f + essSweep,
                    sweepAngle = junkSweep,
                    useCenter = false,
                    topLeft = tl,
                    size = arcSize,
                    style = Stroke(
                        width = stroke,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
                    ),
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("KHAALI", color = Pal.Muted, fontSize = 12.sp, letterSpacing = 2.sp)
            Text(fmtBytes(freeShown), fontSize = 40.sp, fontWeight = FontWeight.Bold)
            Text("${fmtBytes(info.total)} mein se", color = Pal.Muted, fontSize = 13.sp)
        }
    }
}

@Composable
fun LegendItem(
    label: String,
    bytes: Long,
    dotColor: Color,
    valueColor: Color,
    pulse: Boolean,
    modifier: Modifier = Modifier,
) {
    val inf = rememberInfiniteTransition(label = "legend")
    val s by inf.animateFloat(
        0.8f, 1.4f,
        infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "s",
    )
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(12.dp)
                .graphicsLayer {
                    val k = if (pulse) s else 1f
                    scaleX = k
                    scaleY = k
                }
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            color = if (pulse) Color.White else Pal.DullText,
            fontSize = 13.sp,
            fontWeight = if (pulse) FontWeight.Bold else FontWeight.Normal,
        )
        Text(fmtBytes(bytes), color = valueColor, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun CleanCta(junk: Long, onClick: () -> Unit) {
    val inf = rememberInfiniteTransition(label = "cta")
    val s by inf.animateFloat(
        1f, 1.035f,
        infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "s",
    )
    if (junk <= 0L) {
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Pal.Card).padding(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("🎉 Sab clean hai! Koi fultu cheez nahi mili.", fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
    } else {
        GradientButton(
            text = "✨ Safai shuru karo • ${fmtBytes(junk)} mil sakta hai",
            colors = listOf(Pal.Pink, Pal.Orange),
            modifier = Modifier.graphicsLayer {
                scaleX = s
                scaleY = s
            },
            onClick = onClick,
        )
    }
}

@Composable
fun CategoryBar(emoji: String, label: String, bytes: Long, maxBytes: Long, color: Color, delayMs: Int) {
    val frac = remember { Animatable(0f) }
    val target = if (maxBytes > 0L) bytes.toFloat() / maxBytes.toFloat() else 0f
    LaunchedEffect(target) {
        delay(delayMs.toLong())
        frac.animateTo(target.coerceIn(0f, 1f), tween(1100, easing = FastOutSlowInEasing))
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("$emoji  $label", fontSize = 15.sp)
            Text(fmtBytes(bytes), color = Pal.Muted, fontSize = 14.sp)
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)).background(Pal.Track)) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(frac.value.coerceIn(0f, 1f))
                    .clip(RoundedCornerShape(6.dp))
                    .background(Brush.horizontalGradient(listOf(color, color.copy(alpha = 0.6f))))
            )
        }
    }
}

// ───────────────────────────── clean screen ─────────────────────────────

@Composable
fun CleanScreen(vm: MainVm, r: ScanResult, onBack: () -> Unit, onDelete: () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "pulse").animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse",
    )

    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 140.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                    TextButton(onClick = onBack) { Text("← Wapas", color = Pal.Muted) }
                    Text("Safai", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text("✨ Chamakta = rakh rahe ho     🌫 Dull = hat jayega", color = Pal.Muted, fontSize = 13.sp)
                    Text("Kisi bhi photo ya video pe tap karke badal sakte ho.", color = Pal.Muted, fontSize = 13.sp)
                }
            }
            if (r.junk.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        Text("🎉 Koi fultu cheez nahi mili!", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            r.junk.forEach { g ->
                item(span = { GridItemSpan(maxLineSpan) }, key = "h_" + g.type.name) {
                    GroupHeader(g, vm)
                }
                items(g.items, key = { it.key }) { m ->
                    JunkTile(
                        item = m,
                        marked = vm.marked[m.key] == true,
                        pulse = pulse,
                        onToggle = { vm.toggle(m.key) },
                    )
                }
            }
        }
        BottomBar(vm, onDelete, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
fun GroupHeader(g: JunkGroup, vm: MainVm) {
    val allMarked = g.items.all { vm.marked[it.key] == true }
    val markedBytes = g.items.filter { vm.marked[it.key] == true }.sumOf { it.size }
    Column(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 18.dp, bottom = 6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${g.type.emoji} ${g.type.title}",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { vm.setGroup(g, !allMarked) }) {
                Text(
                    if (allMarked) "Sab rakho" else "Sab hatao",
                    color = if (allMarked) Pal.Green else Pal.Pink,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Text("${g.items.size} files • ${fmtBytes(markedBytes)} hat sakta hai", color = Pal.Muted, fontSize = 13.sp)
        Text(g.type.hint, color = Pal.Muted, fontSize = 12.sp)
    }
}

@Composable
fun JunkTile(item: MediaItem, marked: Boolean, pulse: State<Float>, onToggle: () -> Unit) {
    val sat by animateFloatAsState(if (marked) 0.05f else 1f, tween(500), label = "sat")
    val alpha by animateFloatAsState(if (marked) 0.5f else 1f, tween(500), label = "alpha")
    val scale by animateFloatAsState(
        if (marked) 0.93f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "scale",
    )
    val badgeScale by animateFloatAsState(
        if (marked) 0f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "badge",
    )

    Box(Modifier.aspectRatio(1f).padding(4.dp)) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    // rakhi hui cheez halka sa uchhalti hai (tagdi animation), hatne wali sthir rehti hai
                    translationY = if (marked) 0f else -4.dp.toPx() * pulse.value
                }
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onToggle)
                .drawWithContent {
                    drawContent()
                    if (!marked) {
                        val w = 3.dp.toPx()
                        drawRoundRect(
                            color = Pal.Green.copy(alpha = 0.55f + 0.45f * pulse.value),
                            topLeft = Offset(w / 2f, w / 2f),
                            size = Size(size.width - w, size.height - w),
                            cornerRadius = CornerRadius(14.dp.toPx()),
                            style = Stroke(width = w),
                        )
                    }
                }
        ) {
            Thumb(item, Modifier.fillMaxSize(), saturation = sat, alpha = alpha)

            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
                    .padding(start = 6.dp, end = 6.dp, top = 14.dp, bottom = 4.dp)
            ) {
                Text(
                    fmtBytes(item.size),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (marked) Pal.DullText else Color.White,
                )
            }

            if (item.isVideo) {
                Text(
                    "▶",
                    modifier = Modifier.align(Alignment.Center),
                    fontSize = 22.sp,
                    color = Color.White.copy(alpha = if (marked) 0.4f else 0.9f),
                )
            }

            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .graphicsLayer {
                        val k = if (marked) 1f else badgeScale
                        scaleX = k
                        scaleY = k
                    }
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (marked) Color(0x99000000) else Pal.Green)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    if (marked) "hatega" else "✓ rakho",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (marked) Pal.DullText else Color(0xFF00110B),
                )
            }
        }
    }
}

@Composable
fun BottomBar(vm: MainVm, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    val sel = vm.junkSelected()
    val bytes = sel.sumOf { it.size }
    val shown by animateFloatAsState(bytes.toFloat(), tween(500), label = "bytes")
    val enabled = sel.isNotEmpty()

    Box(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Pal.Bg)))
            .padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 16.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(
                    if (enabled) Brush.horizontalGradient(listOf(Pal.Pink, Pal.Orange)) else SolidColor(Pal.Track)
                )
                .clickable(enabled = enabled, onClick = onDelete)
                .padding(vertical = 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (enabled) "🗑 ${sel.size} files hatao • ${fmtBytes(shown.toLong())} free hoga" else "Kuch select nahi kiya",
                color = if (enabled) Color.White else Pal.Muted,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            )
        }
    }
}

// ───────────────────────────── celebration ─────────────────────────────

private class Particle(val angle: Float, val speed: Float, val color: Color, val radius: Float)

@Composable
fun CelebrationOverlay(bytes: Long, onDone: () -> Unit) {
    val p = remember { Animatable(0f) }
    val scale = remember { Animatable(0.3f) }
    val particles = remember {
        val rnd = Random(7)
        val palette = listOf(Pal.Cyan, Pal.Green, Pal.Pink, Pal.Yellow, Pal.Orange)
        List(60) {
            Particle(
                angle = rnd.nextFloat() * 6.2832f,
                speed = 0.3f + rnd.nextFloat() * 0.7f,
                color = palette[rnd.nextInt(palette.size)],
                radius = 3f + rnd.nextFloat() * 5f,
            )
        }
    }
    LaunchedEffect(Unit) {
        launch {
            scale.animateTo(
                1f,
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
            )
        }
        launch { p.animateTo(1f, tween(2200, easing = LinearOutSlowInEasing)) }
        delay(3600)
        onDone()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xDD0B0F1A))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDone,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val maxD = size.minDimension * 0.75f
            particles.forEach { pt ->
                val d = maxD * pt.speed * p.value
                drawCircle(
                    color = pt.color.copy(alpha = (1f - p.value).coerceIn(0f, 1f)),
                    radius = pt.radius.dp.toPx(),
                    center = Offset(center.x + cos(pt.angle) * d, center.y + sin(pt.angle) * d),
                )
            }
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            },
        ) {
            Text("🎉", fontSize = 64.sp)
            Text(fmtBytes(bytes), fontSize = 44.sp, fontWeight = FontWeight.Bold, color = Pal.Green)
            Text("free ho gaya!", fontSize = 20.sp)
        }
    }
}
