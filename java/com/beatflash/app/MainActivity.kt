package com.beatflash.app

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.random.Random

// ---------- data ----------
enum class Screen { WARNING, IMPORT, PLAYER }
data class AppMedia(val uri: Uri, val isVideo: Boolean)

private val BG = Color(0xFF08080C)
private val PANEL = Color(0xFF111117)
private val INK_DIM = Color(0xFF8B8B9A)
private val SIGNAL = Color(0xFFFF3D7F)
private val SIGNAL2 = Color(0xFF31E5C9)
private val WARN = Color(0xFFFFB020)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = BG, surface = BG)) {
                Surface(color = BG, modifier = Modifier.fillMaxSize()) {
                    BeatFlashApp()
                }
            }
        }
    }
}

@Composable
fun BeatFlashApp() {
    var screen by remember { mutableStateOf(Screen.WARNING) }
    val mediaList = remember { mutableStateListOf<AppMedia>() }

    var cycleRange by remember { mutableStateOf(1000f..3000f) }
    var bpmRange by remember { mutableStateOf(90f..180f) }
    var silenceMaxSec by remember { mutableStateOf(20f) }

    when (screen) {
        Screen.WARNING -> WarningScreen(onAccept = { screen = Screen.IMPORT })
        Screen.IMPORT -> ImportScreen(
            mediaList = mediaList,
            cycleRange = cycleRange, onCycleRangeChange = { cycleRange = it },
            bpmRange = bpmRange, onBpmRangeChange = { bpmRange = it },
            silenceMaxSec = silenceMaxSec, onSilenceChange = { silenceMaxSec = it },
            onStart = { screen = Screen.PLAYER }
        )
        Screen.PLAYER -> PlayerScreen(
            mediaList = mediaList,
            cycleMinMs = cycleRange.start.toLong(),
            cycleMaxMs = cycleRange.endInclusive.toLong(),
            bpmMin = bpmRange.start.toInt(),
            bpmMax = bpmRange.endInclusive.toInt(),
            silenceMaxMs = (silenceMaxSec * 1000).toLong(),
            onExit = { screen = Screen.IMPORT }
        )
    }
}

// ---------- warning ----------
@Composable
fun WarningScreen(onAccept: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(BG).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("BEATFLASH", color = SIGNAL2, fontSize = 12.sp, letterSpacing = 3.sp)
        Spacer(Modifier.height(8.dp))
        Text("Before you start", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(WARN.copy(alpha = 0.08f))
                .border(1.dp, WARN, RoundedCornerShape(14.dp))
                .padding(18.dp)
        ) {
            Text(
                "Photosensitive warning. This app displays rapidly changing images/video, a " +
                "pulsing light synced to a set tempo, and occasional full-screen white flash " +
                "events. Flashing visuals can trigger seizures in people with photosensitive " +
                "epilepsy, even with no prior history. Stop immediately if you feel dizzy, " +
                "nauseous, or notice altered vision. Don't use while driving or operating machinery.",
                color = Color(0xFFFFD68A), fontSize = 14.sp, lineHeight = 21.sp
            )
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onAccept,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = SIGNAL)
        ) { Text("I understand, continue", color = Color(0xFF100008), fontWeight = FontWeight.Bold) }
    }
}

// ---------- import ----------
@Composable
fun ImportScreen(
    mediaList: SnapshotStateList<AppMedia>,
    cycleRange: ClosedFloatingPointRange<Float>, onCycleRangeChange: (ClosedFloatingPointRange<Float>) -> Unit,
    bpmRange: ClosedFloatingPointRange<Float>, onBpmRangeChange: (ClosedFloatingPointRange<Float>) -> Unit,
    silenceMaxSec: Float, onSilenceChange: (Float) -> Unit,
    onStart: () -> Unit
) {
    val context = LocalContext.current
    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        uris.forEach { uri ->
            val type = context.contentResolver.getType(uri) ?: ""
            mediaList.add(AppMedia(uri, type.startsWith("video")))
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(BG).padding(24.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(12.dp))
        Text("STEP 1", color = SIGNAL2, fontSize = 12.sp, letterSpacing = 3.sp)
        Text("Import media", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
            },
            colors = ButtonDefaults.buttonColors(containerColor = SIGNAL)
        ) { Text("Choose photos & videos", color = Color(0xFF100008)) }

        Spacer(Modifier.height(10.dp))
        Text(
            "${mediaList.size} file${if (mediaList.size == 1) "" else "s"} added",
            color = INK_DIM, fontSize = 13.sp
        )

        if (mediaList.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.fillMaxWidth().height(180.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(mediaList) { item ->
                    Box(
                        modifier = Modifier.aspectRatio(1f).clip(RoundedCornerShape(8.dp)).background(PANEL)
                    ) {
                        if (item.isVideo) {
                            Text("\u25B6", modifier = Modifier.align(Alignment.Center), color = Color.White, fontSize = 18.sp)
                        } else {
                            AsyncImage(
                                model = item.uri, contentDescription = null,
                                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        SettingsCard(cycleRange, onCycleRangeChange, bpmRange, onBpmRangeChange, silenceMaxSec, onSilenceChange)

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onStart,
            enabled = mediaList.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = SIGNAL, disabledContainerColor = SIGNAL.copy(alpha = 0.3f))
        ) { Text("Start", color = Color(0xFF100008), fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun SettingsCard(
    cycleRange: ClosedFloatingPointRange<Float>, onCycleRangeChange: (ClosedFloatingPointRange<Float>) -> Unit,
    bpmRange: ClosedFloatingPointRange<Float>, onBpmRangeChange: (ClosedFloatingPointRange<Float>) -> Unit,
    silenceMaxSec: Float, onSilenceChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(PANEL).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            "Image/video change speed: %.1fs \u2013 %.1fs".format(cycleRange.start / 1000, cycleRange.endInclusive / 1000),
            color = INK_DIM, fontSize = 13.sp
        )
        RangeSlider(value = cycleRange, onValueChange = onCycleRangeChange, valueRange = 300f..3000f)

        Spacer(Modifier.height(8.dp))
        Text(
            "Tempo to follow (BPM): ${bpmRange.start.toInt()} \u2013 ${bpmRange.endInclusive.toInt()}",
            color = INK_DIM, fontSize = 13.sp
        )
        RangeSlider(value = bpmRange, onValueChange = onBpmRangeChange, valueRange = 60f..220f)

        Spacer(Modifier.height(8.dp))
        Text(
            "Random pause gaps: " + if (silenceMaxSec < 1f) "off" else "up to ${silenceMaxSec.toInt()}s",
            color = INK_DIM, fontSize = 13.sp
        )
        Slider(value = silenceMaxSec, onValueChange = onSilenceChange, valueRange = 0f..20f)
    }
}

// ---------- player ----------
@Composable
fun PlayerScreen(
    mediaList: List<AppMedia>,
    cycleMinMs: Long, cycleMaxMs: Long,
    bpmMin: Int, bpmMax: Int,
    silenceMaxMs: Long,
    onExit: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current

    // fullscreen immersive + keep screen on while this screen is visible
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        val window = (context as? android.app.Activity)?.window
        window?.let {
            WindowCompat.setDecorFitsSystemWindows(it, false)
            val controller = WindowInsetsControllerCompat(it, view)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        onDispose {
            view.keepScreenOn = false
            window?.let {
                WindowCompat.setDecorFitsSystemWindows(it, true)
                WindowInsetsControllerCompat(it, view).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    val exoPlayer = remember { ExoPlayer.Builder(context).build() }
    var currentItem by remember { mutableStateOf<AppMedia?>(null) }
    val brokenUris = remember { mutableSetOf<Uri>() }

    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                // codec/container ExoPlayer genuinely can't handle â mark it and move on
                currentItem?.let { brokenUris.add(it.uri) }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // ---- media cycler ----
    LaunchedEffect(mediaList) {
        if (mediaList.isEmpty()) return@LaunchedEffect
        var order = mediaList.indices.shuffled().toMutableList()
        var pos = 0
        while (isActive) {
            if (pos >= order.size) { order = mediaList.indices.shuffled().toMutableList(); pos = 0 }
            var idx = -1; var tries = 0
            while (tries < order.size) {
                val c = order[pos]; pos = (pos + 1) % order.size; tries++
                if (mediaList[c].uri !in brokenUris) { idx = c; break }
            }
            if (idx == -1) { delay(1500); continue }
            val item = mediaList[idx]
            currentItem = item
            if (item.isVideo) {
                exoPlayer.setMediaItem(androidx.media3.common.MediaItem.fromUri(item.uri))
                exoPlayer.prepare()
                exoPlayer.play()
            } else {
                exoPlayer.stop()
            }
            val wait = if (cycleMaxMs > cycleMinMs) Random.nextLong(cycleMinMs, cycleMaxMs) else cycleMinMs
            delay(wait)
        }
    }

    // ---- pulse (bpm) engine â visual only, no audio ----
    var bpm by remember { mutableStateOf((bpmMin + bpmMax) / 2) }
    var paused by remember { mutableStateOf(false) }
    val circlePulse = remember { Animatable(0.16f) }

    LaunchedEffect(Unit) {
        bpm = if (bpmMax > bpmMin) Random.nextInt(bpmMin, bpmMax) else bpmMin
        launch {
            while (isActive) {
                delay(Random.nextLong(4000, 10000))
                bpm = if (bpmMax > bpmMin) Random.nextInt(bpmMin, bpmMax) else bpmMin
            }
        }
        launch {
            if (silenceMaxMs > 0) {
                while (isActive) {
                    delay(Random.nextLong(8000, 23000))
                    paused = true
                    delay(1000 + Random.nextLong(0, silenceMaxMs + 1))
                    paused = false
                }
            }
        }
        while (isActive) {
            val beatMs = 60000L / bpm
            if (!paused) {
                circlePulse.animateTo(1f, tween((beatMs * 0.42).toInt().coerceAtLeast(1)))
                circlePulse.animateTo(0.16f, tween((beatMs * 0.58).toInt().coerceAtLeast(1)))
            } else {
                circlePulse.snapTo(0.1f)
                delay(beatMs)
            }
        }
    }

    // ---- "NOW!" event: 20s tappable main phase, then a 5s "EDGE" epilogue if ignored ----
    var nowActive by remember { mutableStateOf(false) }
    var nowText by remember { mutableStateOf("NOW!") }
    var showEdgeButton by remember { mutableStateOf(false) }
    val overlayAlpha = remember { Animatable(0f) }
    var edgeTap by remember { mutableStateOf<CompletableDeferred<Unit>?>(null) }

    LaunchedEffect(Unit) {
        while (isActive) {
            delay(Random.nextLong(45000, 105000))
            nowActive = true
            nowText = "NOW!"
            showEdgeButton = true
            val half = if (Random.nextBoolean()) 1700 else 575 // slow vs fast, always eased
            val pulseJob = launch {
                while (isActive) {
                    overlayAlpha.animateTo(0.94f, tween(half))
                    overlayAlpha.animateTo(0f, tween(half))
                }
            }
            val deferred = CompletableDeferred<Unit>()
            edgeTap = deferred
            val tapped = withTimeoutOrNull(20000) { deferred.await(); true } ?: false
            showEdgeButton = false
            if (!tapped) {
                nowText = "EDGE"
                delay(5000)
            }
            pulseJob.cancel()
            overlayAlpha.snapTo(0f)
            nowActive = false
            edgeTap = null
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        currentItem?.let { item ->
            if (item.isVideo) {
                AndroidView(
                    factory = {
                        PlayerView(context).apply {
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            player = exoPlayer
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                AsyncImage(
                    model = item.uri, contentDescription = null,
                    contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()
                )
            }
        }

        if (nowActive) {
            Box(
                Modifier.fillMaxSize().graphicsLayer { alpha = overlayAlpha.value }.background(Color.White)
            )
            Text(
                nowText, color = Color.White, fontSize = 64.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        if (showEdgeButton) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter).padding(top = 18.dp)
                    .size(52.dp).clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .border(2.dp, Color.White, CircleShape)
                    .clickable { edgeTap?.complete(Unit) }
            )
        } else if (!nowActive) {
            Column(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .graphicsLayer {
                            val s = 0.7f + circlePulse.value * 0.3f
                            scaleX = s; scaleY = s
                            alpha = 0.16f + circlePulse.value * 0.84f
                        }
                        .clip(CircleShape)
                        .background(Brush.radialGradient(listOf(SIGNAL, Color(0xFF7A0033))))
                )
                Spacer(Modifier.height(6.dp))
                Text("$bpm BPM", color = Color.White, fontSize = 12.sp)
            }
        }

        if (paused && !nowActive) {
            Text(
                "paused", color = INK_DIM, fontSize = 11.sp,
                modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd).padding(16.dp)
                .size(42.dp).clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable { onExit() },
            contentAlignment = Alignment.Center
        ) {
            Text("\u2715", color = Color.White, fontSize = 16.sp)
        }
    }
}
