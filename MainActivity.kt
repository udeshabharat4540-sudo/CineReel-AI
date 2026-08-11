package com.cinereel.ai

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.Transformer
import androidx.media3.ui.PlayerView
import androidx.media3.exoplayer.ExoPlayer
import java.io.File

private val Bg = Color(0xFF08080C)
private val Card = Color(0xFF15151D)
private val Accent = Color(0xFFB45CFF)
private val Accent2 = Color(0xFF28D7FF)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CineReelApp() }
    }

    @OptIn(UnstableApi::class)
    fun exportTrimmedVideo(uri: Uri, startMs: Long, endMs: Long, onDone: (File) -> Unit, onError: (String) -> Unit) {
        try {
            val output = File(cacheDir, "CineReel_${System.currentTimeMillis()}.mp4")
            val clip = MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(startMs)
                .setEndPositionMs(endMs)
                .build()
            val item = MediaItem.Builder()
                .setUri(uri)
                .setClippingConfiguration(clip)
                .build()

            val transformer = Transformer.Builder(this)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: androidx.media3.transformer.ExportResult) {
                        runOnUiThread { onDone(output) }
                    }
                    override fun onError(
                        composition: Composition,
                        exportResult: androidx.media3.transformer.ExportResult,
                        exportException: androidx.media3.transformer.ExportException
                    ) {
                        runOnUiThread { onError(exportException.message ?: "Export failed") }
                    }
                })
                .build()

            transformer.start(item, output.absolutePath)
        } catch (e: Exception) {
            onError(e.message ?: "Export failed")
        }
    }

    fun shareFile(file: File) {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            this, "$packageName.fileprovider", file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Share CineReel video"))
    }
}

@Composable
fun CineReelApp() {
    var selected by remember { mutableStateOf<Uri?>(null) }
    var style by remember { mutableStateOf("Cinematic") }
    var startMs by remember { mutableLongStateOf(0L) }
    var endMs by remember { mutableLongStateOf(15000L) }
    var exporting by remember { mutableStateOf(false) }
    var exported by remember { mutableStateOf<File?>(null) }
    val activity = androidx.compose.ui.platform.LocalContext.current as MainActivity

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        selected = uri
        startMs = 0L
        endMs = 15000L
        exported = null
    }

    MaterialTheme(colorScheme = darkColorScheme(
        background = Bg, surface = Card, primary = Accent
    )) {
        Surface(Modifier.fillMaxSize(), color = Bg) {
            Column(Modifier.fillMaxSize().padding(18.dp)) {
                Text("CineReel AI", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text("Cinematic Reel Editor • V1", color = Color.Gray, fontSize = 13.sp)
                Spacer(Modifier.height(18.dp))

                if (selected == null) {
                    Card(
                        modifier = Modifier.fillMaxWidth().height(280.dp).clickable { picker.launch("video/*") },
                        colors = CardDefaults.cardColors(containerColor = Card),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Column(
                            Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text("＋", color = Accent2, fontSize = 60.sp)
                            Text("Add Video", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            Text("Select one video from Gallery", color = Color.Gray)
                        }
                    }
                } else {
                    VideoPreview(selected!!)
                    Spacer(Modifier.height(12.dp))

                    Text("Style", color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(listOf("Cinematic", "Wedding", "Travel", "Trending")) { item ->
                            FilterChip(
                                selected = style == item,
                                onClick = { style = item },
                                label = { Text(item) }
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    Text("Trim: ${startMs / 1000}s → ${endMs / 1000}s", color = Color.White)
                    Text("V1 export supports real trimming. AI beat-sync/effects are next.", color = Color.Gray, fontSize = 12.sp)

                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { startMs = 0L }) { Text("Start 0s") }
                        OutlinedButton(onClick = { endMs = 15000L }) { Text("End 15s") }
                    }

                    Spacer(Modifier.height(14.dp))
                    Button(
                        enabled = !exporting,
                        onClick = {
                            exporting = true
                            activity.exportTrimmedVideo(
                                selected!!, startMs, endMs,
                                onDone = {
                                    exporting = false
                                    exported = it
                                    Toast.makeText(activity, "Video exported", Toast.LENGTH_SHORT).show()
                                },
                                onError = {
                                    exporting = false
                                    Toast.makeText(activity, it, Toast.LENGTH_LONG).show()
                                }
                            )
                        },
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text(if (exporting) "EXPORTING..." else "EXPORT VIDEO", fontWeight = FontWeight.Bold)
                    }

                    if (exported != null) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { activity.shareFile(exported!!) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("SHARE EXPORTED VIDEO") }
                    }

                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { selected = null },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Choose another video") }
                }

                Spacer(Modifier.weight(1f))
                Text("CineReel AI V1 • Android MVP", color = Color.Gray, fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }
}

@Composable
private fun VideoPreview(uri: Uri) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val player = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            playWhenReady = false
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = player
                useController = true
            }
        },
        modifier = Modifier.fillMaxWidth().height(300.dp)
    )
}
