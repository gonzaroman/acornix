package com.gonzaroman.acornix.ui.create

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gonzaroman.acornix.R
import com.gonzaroman.acornix.container
import com.gonzaroman.acornix.core.Idea
import com.gonzaroman.acornix.core.IdeaGenerator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * "Need ideas?": the built-in ideas, or the last batch the AI wrote, so opening the screen never costs
 * tokens. With an AI configured, a button asks it for a new batch, which shows up idea by idea.
 */
@Composable
fun IdeasSection(feature: Boolean, category: String?, onPick: (Idea) -> Unit) {
    val context = LocalContext.current
    val generator = context.container.ideas
    val scope = rememberCoroutineScope()
    val categoryIndex = stringArrayResource(R.array.categories).indexOf(category)
    var available by remember { mutableStateOf(false) }
    var ideas by remember(feature, category) { mutableStateOf<List<Idea>>(emptyList()) }
    /** The ideas shown were written by the AI (not the built-in ones). */
    var fromAi by remember(feature, category) { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    /** The list shows a new batch that is still arriving. */
    var streaming by remember { mutableStateOf(false) }
    var failed by remember(feature, category) { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }

    fun refresh() {
        job?.cancel()
        job = scope.launch {
            loading = true
            failed = false
            try {
                ideas = generator.fresh(feature, category) { partial -> scope.launch { ideas = partial; streaming = true } }
                fromAi = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed = true
            } finally {
                loading = false
                streaming = false
            }
        }
    }

    LaunchedEffect(feature, category) {
        available = generator.isAvailable()
        val cached = if (available) generator.cached(feature, category) else null
        fromAi = cached != null
        ideas = cached ?: generator.builtIn(feature, categoryIndex)
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(if (fromAi) R.string.ideas_ai_title else R.string.inspiration),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            if (available) {
                TextButton(onClick = { refresh() }, enabled = !loading) {
                    if (loading) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(stringResource(if (fromAi) R.string.ideas_more else R.string.ideas_ask_ai))
                }
            }
        }
        if (loading) {
            Text(
                stringResource(R.string.ideas_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (failed) {
            Text(
                stringResource(R.string.ideas_failed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ideas.forEach { IdeaCard(it) { onPick(it) } }
        // While a new batch streams in, keep its place so the screen doesn't jump.
        if (streaming) repeat(IdeaGenerator.BATCH_SIZE - ideas.size) { IdeaPlaceholder() }
    }
}

/** An empty card the size of an idea, for the ones still on their way. */
@Composable
private fun IdeaPlaceholder() {
    val shade = MaterialTheme.colorScheme.surfaceVariant
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(32.dp).background(shade, CircleShape))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.fillMaxWidth(0.55f).height(14.dp).background(shade, RoundedCornerShape(7.dp)))
                Box(Modifier.fillMaxWidth().height(10.dp).background(shade, RoundedCornerShape(5.dp)))
                Box(Modifier.fillMaxWidth(0.8f).height(10.dp).background(shade, RoundedCornerShape(5.dp)))
            }
        }
    }
}

@Composable
private fun IdeaCard(idea: Idea, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(idea.emoji, fontSize = 28.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(idea.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    idea.prompt,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
