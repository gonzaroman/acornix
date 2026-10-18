package com.gonzaroman.acornix.ui.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gonzaroman.acornix.AppContainer
import com.gonzaroman.acornix.ai.AiException
import com.gonzaroman.acornix.ai.ImageData
import com.gonzaroman.acornix.core.GenerationException
import com.gonzaroman.acornix.core.GenerationRequest
import com.gonzaroman.acornix.data.AppKind
import com.gonzaroman.acornix.data.AppMeta
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface CreateUi {
    data object Form : CreateUi
    data class Generating(val chars: Int = 0, val code: String = "", val codeFinished: Boolean = false) : CreateUi
    data class Done(val app: AppMeta, val suggestion: String?, val truncated: Boolean) : CreateUi
    data class Failed(val message: String, val error: Exception? = null) : CreateUi
}

class CreateViewModel(
    private val c: AppContainer,
    private val feature: Boolean,
    improveAppId: String?,
) : ViewModel() {

    private val _ui = MutableStateFlow<CreateUi>(CreateUi.Form)
    val ui: StateFlow<CreateUi> = _ui.asStateFlow()

    /** The app being improved: given on entry, or the one just created. */
    var improveTarget: AppMeta? = improveAppId?.let { c.apps.get(it) }
        private set

    var lastIdea: String = ""
        private set

    private var lastRequest: GenerationRequest? = null
    private var previousDone: CreateUi.Done? = null
    private var job: Job? = null

    fun generate(idea: String, name: String, category: String?, images: List<ImageData>, emoji: String? = null) {
        lastIdea = idea
        start(
            GenerationRequest(
                idea = idea,
                name = name,
                kind = if (feature) AppKind.FEATURE else AppKind.APP,
                category = category,
                images = images,
                improveAppId = improveTarget?.id,
                emoji = emoji,
            ),
        )
    }

    /** Applies a change (typed or the AI's suggestion) to the app shown in the Done state. */
    fun improve(change: String, images: List<ImageData>) {
        val target = (ui.value as? CreateUi.Done)?.app ?: improveTarget ?: return
        if (change.isBlank()) return
        lastIdea = change
        start(GenerationRequest(idea = change, images = images, improveAppId = target.id))
    }

    fun retry() {
        lastRequest?.let { start(it) }
    }

    fun backToForm() {
        _ui.value = previousDone ?: CreateUi.Form
    }

    fun cancel() {
        job?.cancel()
    }

    private fun start(request: GenerationRequest) {
        lastRequest = request
        previousDone = ui.value as? CreateUi.Done
        job?.cancel()
        _ui.value = CreateUi.Generating()
        job = viewModelScope.launch {
            try {
                val result = c.generator.generate(request) { p ->
                    _ui.value = CreateUi.Generating(p.chars, p.code, p.codeFinished)
                }
                improveTarget = result.app
                _ui.value = CreateUi.Done(result.app, result.suggestion, result.truncated)
            } catch (e: CancellationException) {
                _ui.value = previousDone ?: CreateUi.Form
                throw e
            } catch (e: GenerationException) {
                _ui.value = CreateUi.Failed(e.message ?: "Error", e)
            } catch (e: AiException) {
                _ui.value = CreateUi.Failed(e.message ?: "Error", e)
            } catch (e: Exception) {
                _ui.value = CreateUi.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }
}
