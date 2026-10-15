package com.gonzaroman.acornix.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.speech.RecognizerIntent
import android.text.format.DateUtils
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.gonzaroman.acornix.R
import com.gonzaroman.acornix.ai.ImageData
import com.gonzaroman.acornix.container
import com.gonzaroman.acornix.data.AppKind
import com.gonzaroman.acornix.data.AppMeta
import com.gonzaroman.acornix.device.ShortcutHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.Locale

// ---------- App visuals ----------

fun appGradient(id: String): Brush {
    val (a, b) = ShortcutHelper.gradientFor(id)
    return Brush.linearGradient(listOf(Color(a), Color(b)))
}

@Composable
fun AppIcon(app: AppMeta, size: Dp = 56.dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f))
            .background(appGradient(app.id)),
        contentAlignment = Alignment.Center,
    ) {
        Text(app.emoji, fontSize = (size.value * 0.5f).sp)
    }
}

/** Big card for the launcher grid: thumbnail (or emoji art) plus name. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppCard(app: AppMeta, onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val thumb = remember(app.id, app.updatedAt, app.lastOpenedAt) {
        context.container.apps.thumbnailFile(app.id).takeIf { it.exists() }?.let { it to it.lastModified() }
    }
    Card(
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.85f)
                .clip(MaterialTheme.shapes.large)
                .background(appGradient(app.id)),
            contentAlignment = Alignment.Center,
        ) {
            if (thumb != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(thumb.first).memoryCacheKey("${thumb.first.path}:${thumb.second}").build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(app.emoji, fontSize = 54.sp)
            }
            if (app.kind != AppKind.APP) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                ) {
                    Text(
                        stringResource(if (app.kind == AppKind.FEATURE) R.string.kind_feature else R.string.kind_sample),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
        }
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(app.emoji, fontSize = 16.sp)
            Spacer(Modifier.width(6.dp))
            Text(app.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun EmptyState(emoji: String, title: String, text: String, modifier: Modifier = Modifier, action: @Composable (() -> Unit)? = null) {
    Column(modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(emoji, fontSize = 56.sp)
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            action()
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

fun relativeTime(time: Long): String =
    DateUtils.getRelativeTimeSpanString(time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var v = bytes / 1024.0
    var i = 0
    while (v >= 1024 && i < units.lastIndex) { v /= 1024; i++ }
    return String.format(Locale.getDefault(), "%.1f %s", v, units[i])
}

// ---------- Attachments, voice and the prompt bar ----------

class Attachment(val preview: Bitmap, val data: ImageData)

object ImageUtils {
    /** Loads an image, downsizes it (long side ≤ 1568 px) and encodes it as JPEG for the AI. */
    suspend fun load(context: Context, uri: Uri): Attachment? = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = if (Build.VERSION.SDK_INT >= 28) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val long = maxOf(info.size.width, info.size.height)
                    if (long > MAX_SIDE) {
                        val s = MAX_SIDE.toFloat() / long
                        decoder.setTargetSize((info.size.width * s).toInt(), (info.size.height * s).toInt())
                    }
                }
            } else {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
                } ?: error("Cannot read image")
            }
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            val bytes = out.toByteArray()
            val preview = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            Attachment(preview, ImageData(Base64.encodeToString(bytes, Base64.NO_WRAP), "image/jpeg"))
        }.getOrNull()
    }

    private const val MAX_SIDE = 1568
}

/** Holds attached images and exposes a picker launcher. */
class AttachmentState(private val launch: () -> Unit) {
    val items = mutableStateListOf<Attachment>()
    fun pick() = launch()
    fun images() = items.map { it.data }
    fun clear() = items.clear()
}

@Composable
fun rememberAttachments(max: Int = 3): AttachmentState {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    lateinit var state: AttachmentState
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(max)) { uris ->
        scope.launch {
            uris.forEach { uri ->
                if (state.items.size < max) ImageUtils.load(context, uri)?.let { state.items += it }
            }
        }
    }
    state = remember { AttachmentState { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) } }
    return state
}

/** Speech-to-text through the system recognizer dialog (no microphone permission needed). */
@Composable
fun rememberVoiceInput(onText: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val callback by rememberUpdatedState(onText)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let(callback)
    }
    val prompt = stringResource(R.string.voice_prompt)
    return remember(launcher) {
        {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                .putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
            try {
                launcher.launch(intent)
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(context, R.string.voice_unavailable, Toast.LENGTH_SHORT).show()
            }
        }
    }
}

@Composable
fun AttachmentRow(attachments: AttachmentState, modifier: Modifier = Modifier) {
    AnimatedVisibility(attachments.items.isNotEmpty(), modifier) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
            items(attachments.items.toList()) { att ->
                Box {
                    Image(
                        att.preview.asImageBitmap(), null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(64.dp).clip(MaterialTheme.shapes.small),
                    )
                    FilledIconButton(
                        onClick = { attachments.items.remove(att) },
                        modifier = Modifier.align(Alignment.TopEnd).size(22.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.inverseSurface),
                    ) {
                        Icon(Icons.Rounded.Close, stringResource(R.string.remove), Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

/**
 * Text field with photo, microphone and send/stop buttons, used by the assistant and the creator.
 * With [sendAfterVoice], dictated text is sent as soon as the user stops talking.
 */
@Composable
fun PromptBar(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    onSend: () -> Unit,
    attachments: AttachmentState?,
    modifier: Modifier = Modifier,
    running: Boolean = false,
    onStop: () -> Unit = {},
    enabled: Boolean = true,
    sendAfterVoice: Boolean = false,
) {
    val voice = rememberVoiceInput { spoken ->
        onValueChange(if (value.isBlank()) spoken else "$value $spoken")
        if (sendAfterVoice && spoken.isNotBlank()) onSend()
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp,
    ) {
        Column(Modifier.padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 6.dp)) {
            if (attachments != null) AttachmentRow(attachments, Modifier.padding(start = 8.dp, top = 6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                if (attachments != null) {
                    IconButton(onClick = attachments::pick, enabled = enabled && !running) {
                        Icon(Icons.Rounded.AddPhotoAlternate, stringResource(R.string.attach_image))
                    }
                }
                TextField(
                    value = value,
                    onValueChange = onValueChange,
                    placeholder = { Text(placeholder) },
                    modifier = Modifier.weight(1f),
                    enabled = enabled,
                    maxLines = 6,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                    ),
                )
                when {
                    running -> FilledIconButton(onClick = onStop, colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.error)) {
                        Icon(Icons.Rounded.Stop, stringResource(R.string.stop))
                    }
                    value.isBlank() && (attachments?.items?.isEmpty() != false) -> IconButton(onClick = voice, enabled = enabled) {
                        Icon(Icons.Rounded.Mic, stringResource(R.string.dictate))
                    }
                    else -> FilledIconButton(onClick = onSend, enabled = enabled) {
                        Icon(Icons.AutoMirrored.Rounded.Send, stringResource(R.string.send))
                    }
                }
            }
        }
    }
}
