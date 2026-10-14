package com.gonzaroman.acornix.agent

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.gonzaroman.acornix.AcornixApplication
import com.gonzaroman.acornix.MainActivity
import com.gonzaroman.acornix.R
import com.gonzaroman.acornix.ai.AgentReply
import com.gonzaroman.acornix.ai.AgentSession
import com.gonzaroman.acornix.ai.AiException
import com.gonzaroman.acornix.ai.ImageData
import com.gonzaroman.acornix.control.AcornixAccessibilityService
import com.gonzaroman.acornix.core.GenerationProgress
import com.gonzaroman.acornix.core.Prompts
import com.gonzaroman.acornix.data.AppMeta
import com.gonzaroman.acornix.data.AppRepository
import com.gonzaroman.acornix.data.SettingsRepository
import com.gonzaroman.acornix.device.DeviceActions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

sealed interface ChatItem {
    val key: Long

    data class User(override val key: Long, val text: String, val images: Int) : ChatItem
    data class Assistant(override val key: Long, val text: String) : ChatItem
    data class Step(override val key: Long, val tool: String, val detail: String, val status: StepStatus, val result: String? = null) : ChatItem
    data class AppCard(
        override val key: Long,
        val name: String,
        val emoji: String,
        val chars: Int = 0,
        val app: AppMeta? = null,
        val suggestion: String? = null,
        val error: String? = null,
    ) : ChatItem
    data class Error(override val key: Long, val text: String) : ChatItem
    data class Notice(override val key: Long, val text: String) : ChatItem
}

enum class StepStatus { RUNNING, DONE, FAILED }

data class AssistantState(
    val items: List<ChatItem> = emptyList(),
    val running: Boolean = false,
    val pendingConfirm: String? = null,
)

/**
 * The single assistant ("agente único"): keeps the conversation, runs the tool-calling loop and
 * survives navigation so it can keep working while the user is in another app.
 */
class AssistantController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val settings: SettingsRepository,
    private val repo: AppRepository,
    private val device: DeviceActions,
    private val tools: AgentTools,
) : ToolHost {

    private val _state = MutableStateFlow(AssistantState())
    val state: StateFlow<AssistantState> = _state.asStateFlow()

    private val keys = AtomicLong(1)
    private var session: AgentSession? = null
    private var sessionSignature: String? = null
    private var job: Job? = null
    private var confirmDeferred: CompletableDeferred<Boolean>? = null

    fun send(text: String, images: List<ImageData> = emptyList()) {
        if (text.isBlank() && images.isEmpty()) return
        if (_state.value.running) return
        add(ChatItem.User(nextKey(), text, images.size))
        job = scope.launch { runTurn(text, images) }
    }

    fun stop() {
        job?.cancel()
        device.stopSpeaking()
    }

    fun newChat() {
        stop()
        session = null
        _state.value = AssistantState()
    }

    fun answerConfirm(approved: Boolean) {
        confirmDeferred?.complete(approved)
    }

    private suspend fun runTurn(text: String, images: List<ImageData>) {
        _state.update { it.copy(running = true) }
        showWorkingNotification()
        try {
            val s = settings.current()
            val controlEnabled = AcornixAccessibilityService.instance != null
            val signature = "${s.provider}|${s.config().model}|$controlEnabled"
            val current = session.takeIf { signature == sessionSignature } ?: run {
                val provider = settings.activeProvider()
                val system = Prompts.assistant(
                    language = Locale.getDefault().getDisplayLanguage(Locale.ENGLISH),
                    now = DateFormat.getDateTimeInstance(DateFormat.FULL, DateFormat.SHORT, Locale.ENGLISH).format(Date()),
                    apps = AgentTools.appListText(repo.apps.value),
                    controlEnabled = controlEnabled,
                )
                provider.newAgentSession(system, tools.specs(controlEnabled)).also {
                    session = it
                    sessionSignature = signature
                }
            }

            var reply: AgentReply = current.send(text.ifBlank { "(see the attached image)" }, images)
            var steps = 0
            while (true) {
                if (reply.text.isNotBlank()) {
                    add(ChatItem.Assistant(nextKey(), reply.text.trim()))
                    if (s.speakReplies) device.speak(reply.text)
                }
                if (reply.toolCalls.isEmpty()) break
                if (++steps > MAX_STEPS) {
                    add(ChatItem.Notice(nextKey(), context.getString(R.string.assistant_too_many_steps)))
                    break
                }
                val results = reply.toolCalls.map { call ->
                    val key = nextKey()
                    add(ChatItem.Step(key, call.name, stepDetail(call.arguments), StepStatus.RUNNING))
                    val result = tools.execute(call, this)
                    replace(key) {
                        (it as ChatItem.Step).copy(
                            status = if (result.isError) StepStatus.FAILED else StepStatus.DONE,
                            result = result.content.lineSequence().firstOrNull()?.take(160),
                        )
                    }
                    result
                }
                reply = current.sendToolResults(results)
            }
        } catch (e: CancellationException) {
            // A half-finished tool turn cannot be continued, so start a fresh session next time.
            session = null
            markRunningStepsFailed()
            add(ChatItem.Notice(nextKey(), context.getString(R.string.assistant_stopped)))
        } catch (e: AiException) {
            session = null
            markRunningStepsFailed()
            add(ChatItem.Error(nextKey(), e.message ?: "Error"))
        } catch (e: Exception) {
            session = null
            markRunningStepsFailed()
            add(ChatItem.Error(nextKey(), e.message ?: e.javaClass.simpleName))
        } finally {
            confirmDeferred?.cancel()
            _state.update { it.copy(running = false, pendingConfirm = null) }
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        }
    }

    // ---------- ToolHost ----------

    override suspend fun confirm(message: String): Boolean {
        val title = context.getString(R.string.confirm_title)
        AcornixAccessibilityService.instance?.let { svc ->
            return svc.confirmOverlay(title, message, context.getString(R.string.confirm_yes), context.getString(R.string.confirm_no))
        }
        val deferred = CompletableDeferred<Boolean>()
        confirmDeferred = deferred
        _state.update { it.copy(pendingConfirm = message) }
        return try {
            deferred.await()
        } finally {
            _state.update { it.copy(pendingConfirm = null) }
        }
    }

    override fun appGenerationStarted(name: String, emoji: String): Long {
        val key = nextKey()
        add(ChatItem.AppCard(key, name, emoji))
        return key
    }

    override fun appGenerationProgress(key: Long, progress: GenerationProgress) {
        replace(key) { (it as ChatItem.AppCard).copy(chars = progress.chars) }
    }

    override fun appGenerationFinished(key: Long, app: AppMeta?, suggestion: String?, error: String?) {
        replace(key) { (it as ChatItem.AppCard).copy(app = app, suggestion = suggestion, error = error) }
    }

    // ---------- Helpers ----------

    private fun nextKey() = keys.getAndIncrement()

    private fun add(item: ChatItem) = _state.update { it.copy(items = it.items + item) }

    private fun replace(key: Long, transform: (ChatItem) -> ChatItem) = _state.update { s ->
        s.copy(items = s.items.map { if (it.key == key) transform(it) else it })
    }

    private fun markRunningStepsFailed() = _state.update { s ->
        s.copy(items = s.items.map { if (it is ChatItem.Step && it.status == StepStatus.RUNNING) it.copy(status = StepStatus.FAILED) else it })
    }

    private fun stepDetail(args: kotlinx.serialization.json.JsonObject): String {
        // Strings are shown as their text (JSON arguments without escaped quotes).
        fun text(v: kotlinx.serialization.json.JsonElement) =
            (v as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it.isString }?.content ?: v.toString()
        return args.entries.filter { it.key !in setOf("description", "change") }
            .joinToString(" · ") { text(it.value) }
            .ifEmpty { args["description"]?.let { text(it) }.orEmpty() }
            .take(120)
    }

    private fun showWorkingNotification() {
        device.ensureChannel(DeviceActions.CHANNEL_AGENT, context.getString(R.string.channel_agent))
        val nm = NotificationManagerCompat.from(context)
        if (!device.canNotify()) return
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_OPEN_ASSISTANT),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getBroadcast(
            context, 1, Intent(context, AgentStopReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, DeviceActions.CHANNEL_AGENT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.agent_working_title))
            .setContentText(context.getString(R.string.agent_working_text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(0, context.getString(R.string.stop), stop)
            .build()
        @SuppressLint("MissingPermission") // checked by canNotify()
        runCatching { nm.notify(NOTIFICATION_ID, n) }
    }

    companion object {
        private const val MAX_STEPS = 25
        private const val NOTIFICATION_ID = 4242
    }
}

/** "Stop" action of the ongoing notification shown while the assistant works. */
class AgentStopReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        (context.applicationContext as AcornixApplication).container.assistant.stop()
    }
}
