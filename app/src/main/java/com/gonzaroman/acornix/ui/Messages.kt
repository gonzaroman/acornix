package com.gonzaroman.acornix.ui

import android.content.Context
import com.gonzaroman.acornix.R
import com.gonzaroman.acornix.ai.ProviderNotConfiguredException
import com.gonzaroman.acornix.core.GenerationException
import com.gonzaroman.acornix.data.ImportException

fun importErrorMessage(context: Context, e: ImportException): String = context.getString(
    when (e.reason) {
        ImportException.Reason.NOT_A_ZIP -> R.string.import_error_zip
        ImportException.Reason.PYTHON_PLUGIN -> R.string.import_error_plugin
        ImportException.Reason.NO_INDEX -> R.string.import_error_index
    },
)

fun generationErrorMessage(context: Context, e: GenerationException): String = when (e.reason) {
    GenerationException.Reason.NO_CODE -> context.getString(R.string.gen_error_no_code)
    GenerationException.Reason.REFUSED -> context.getString(R.string.gen_error_refused)
    GenerationException.Reason.API -> e.message ?: context.getString(R.string.gen_error_generic)
}

/** Text for an error shown to the user, translated where Acornix knows the cause. */
fun errorMessage(context: Context, e: Throwable): String = when (e) {
    is ProviderNotConfiguredException -> context.getString(R.string.error_not_configured)
    is GenerationException -> generationErrorMessage(context, e)
    else -> e.message ?: e.javaClass.simpleName
}
