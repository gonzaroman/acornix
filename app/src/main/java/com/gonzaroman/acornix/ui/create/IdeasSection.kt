package com.gonzaroman.acornix.ui.create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AssistChip
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * "Need ideas?": with an AI configured, a batch of original ideas written by the AI (cached, so
 * opening the screen is free) and a button for more; without one, or if it fails, the fixed examples.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IdeasSection(feature: Boolean, category: String?, onPick: (Idea) -> Unit) {
    val context = LocalContext.current
    val generator = context.container.ideas
    val scope = rememberCoroutineScope()
    var available by remember { mutableStateOf<Boolean?>(null) }
    var ideas by remember(feature, category) { mutableStateOf<List<Idea>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var failed by remember(feature, category) { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }

    fun refresh() {
        job?.cancel()
        job = scope.launch {
            loading = true
            failed = false
            try {
                ideas = generator.fresh(feature, category)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed = true
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(feature, category) {
        available = generator.isAvailable()
        if (available != true) return@LaunchedEffect
        delay(400) // tapping through the categories shouldn't fire a request for each one
        val cached = generator.cached(feature, category)
        if (cached != null) ideas = cached.first
        if (cached == null || cached.second) refresh()
    }

    if (available != true) {
        StaticIdeas(feature, stringResource(R.string.inspiration), onPick)
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.ideas_ai_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = { refresh() }, enabled = !loading) {
                if (loading) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(stringResource(R.string.ideas_more))
            }
        }
        when {
            ideas.isNotEmpty() -> ideas.forEach { IdeaCard(it) { onPick(it) } }
            loading -> Text(
                stringResource(R.string.ideas_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            failed -> {
                Text(
                    stringResource(R.string.ideas_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                StaticIdeas(feature, null, onPick)
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

/** The fixed examples: without an API key, or when the AI couldn't answer. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StaticIdeas(feature: Boolean, title: String?, onPick: (Idea) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (title != null) Text(title, style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            stringArrayResource(if (feature) R.array.feature_examples else R.array.app_examples).forEach { ex ->
                // No title: a fixed example is a full request, not a name for the app.
                AssistChip(onClick = { onPick(Idea(title = "", prompt = ex)) }, label = { Text(ex) })
            }
        }
    }
}
