package com.gonzaroman.acornix.ui.tools

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import com.gonzaroman.acornix.R
import com.gonzaroman.acornix.ui.components.EmptyState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class MediaItem(val uri: Uri, val dateMillis: Long, val isVideo: Boolean)

/** NETphotoFLIX: the phone's photos and videos grouped by month. */
@Composable
fun GalleryScreen(nav: NavHostController) {
    val context = LocalContext.current
    val permissions = remember {
        if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    fun granted() = permissions.any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED } ||
        (Build.VERSION.SDK_INT >= 34 && ContextCompat.checkSelfPermission(context, "android.permission.READ_MEDIA_VISUAL_USER_SELECTED") == PackageManager.PERMISSION_GRANTED)

    var hasPermission by remember { mutableStateOf(granted()) }
    var groups by remember { mutableStateOf<List<Pair<String, List<MediaItem>>>?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { hasPermission = granted() }
    val loader = remember {
        ImageLoader.Builder(context).components { add(VideoFrameDecoder.Factory()) }.crossfade(true).build()
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) groups = loadMedia(context)
    }

    ToolScaffold(nav, stringResource(R.string.tool_gallery)) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                !hasPermission -> EmptyState("🖼️", stringResource(R.string.gallery_permission_title), stringResource(R.string.gallery_permission_text), Modifier.align(Alignment.Center)) {
                    Button(onClick = { launcher.launch(permissions) }) { Text(stringResource(R.string.enable)) }
                }
                groups == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                groups!!.isEmpty() -> EmptyState("📭", stringResource(R.string.gallery_empty), "", Modifier.align(Alignment.Center))
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(110.dp),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    groups!!.forEach { (month, items) ->
                        item(span = { GridItemSpan(maxLineSpan) }, key = month) {
                            Text(month, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp, start = 4.dp))
                        }
                        items(items, key = { it.uri.toString() }) { media ->
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clip(MaterialTheme.shapes.small)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .clickable {
                                        val type = if (media.isVideo) "video/*" else "image/*"
                                        runCatching {
                                            context.startActivity(
                                                Intent(Intent.ACTION_VIEW).setDataAndType(media.uri, type).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                                            )
                                        }
                                    },
                            ) {
                                AsyncImage(
                                    model = media.uri,
                                    imageLoader = loader,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                                if (media.isVideo) {
                                    Icon(Icons.Rounded.PlayCircle, null, tint = Color.White, modifier = Modifier.align(Alignment.Center))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private suspend fun loadMedia(context: Context, limit: Int = 1500): List<Pair<String, List<MediaItem>>> = withContext(Dispatchers.IO) {
    val items = mutableListOf<MediaItem>()
    fun query(collection: Uri, isVideo: Boolean) {
        val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DATE_ADDED, MediaStore.MediaColumns.DATE_TAKEN)
        context.contentResolver.query(collection, projection, null, null, "${MediaStore.MediaColumns.DATE_ADDED} DESC")?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val addedCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            val takenCol = c.getColumnIndex(MediaStore.MediaColumns.DATE_TAKEN)
            var n = 0
            while (c.moveToNext() && n++ < limit) {
                val taken = if (takenCol >= 0) c.getLong(takenCol) else 0L
                val date = if (taken > 0) taken else c.getLong(addedCol) * 1000
                items += MediaItem(ContentUris.withAppendedId(collection, c.getLong(idCol)), date, isVideo)
            }
        }
    }
    runCatching { query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, false) }
    runCatching { query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true) }
    val fmt = SimpleDateFormat("LLLL yyyy", Locale.getDefault())
    items.sortedByDescending { it.dateMillis }
        .take(limit)
        .groupBy { fmt.format(Date(it.dateMillis)).replaceFirstChar { ch -> ch.titlecase(Locale.getDefault()) } }
        .toList()
}
