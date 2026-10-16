package com.gonzaroman.acornix.device

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.gonzaroman.acornix.data.AppMeta
import com.gonzaroman.acornix.data.AppRepository
import com.gonzaroman.acornix.web.AcornixOsActivity
import com.gonzaroman.acornix.web.AppLauncher
import kotlin.math.abs

/** Pins generated apps to the Android home screen as if they were installed apps. */
object ShortcutHelper {

    fun isSupported(context: Context) = ShortcutManagerCompat.isRequestPinShortcutSupported(context)

    fun pin(context: Context, app: AppMeta, repo: AppRepository): Boolean {
        if (!isSupported(context)) return false
        val info = ShortcutInfoCompat.Builder(context, "app_${app.id}")
            .setShortLabel(app.name.take(24))
            .setLongLabel(app.name)
            .setIcon(IconCompat.createWithAdaptiveBitmap(iconBitmap(app, repo)))
            .setIntent(AppLauncher.intent(context, app.id))
            .build()
        return ShortcutManagerCompat.requestPinShortcut(context, info, null)
    }

    /** AcornixOS on the home screen, so a tablet can open straight into the desktop. */
    fun pinOs(context: Context): Boolean {
        if (!isSupported(context)) return false
        val size = 432
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), Paint().apply {
            shader = LinearGradient(0f, 0f, size.toFloat(), size.toFloat(), Color.parseColor("#6A1B9A"), Color.parseColor("#FF6E7F"), Shader.TileMode.CLAMP)
        })
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 170f; textAlign = Paint.Align.CENTER }
        canvas.drawText("🍏", size / 2f, size / 2f - (paint.descent() + paint.ascent()) / 2, paint)
        val info = ShortcutInfoCompat.Builder(context, "acornix_os")
            .setShortLabel("AcornixOS")
            .setIcon(IconCompat.createWithAdaptiveBitmap(bitmap))
            .setIntent(AcornixOsActivity.intent(context))
            .build()
        return ShortcutManagerCompat.requestPinShortcut(context, info, null)
    }

    /** 432×432 adaptive icon: the emoji over a gradient derived from the app id. */
    fun iconBitmap(app: AppMeta, repo: AppRepository): Bitmap {
        val size = 432
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val (c1, c2) = gradientFor(app.id)
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), Paint().apply {
            shader = LinearGradient(0f, 0f, size.toFloat(), size.toFloat(), c1, c2, Shader.TileMode.CLAMP)
        })
        val thumb = repo.thumbnailFile(app.id)
        if (app.emoji.isBlank() && thumb.exists()) {
            BitmapFactory.decodeFile(thumb.path)?.let { src ->
                val side = minOf(src.width, src.height)
                val srcRect = Rect((src.width - side) / 2, 0, (src.width + side) / 2, side)
                canvas.drawBitmap(src, srcRect, Rect(72, 72, size - 72, size - 72), Paint(Paint.FILTER_BITMAP_FLAG))
            }
        } else {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 170f; textAlign = Paint.Align.CENTER }
            val y = size / 2f - (paint.descent() + paint.ascent()) / 2
            canvas.drawText(app.emoji.ifBlank { "🌰" }, size / 2f, y, paint)
        }
        return bitmap
    }

    private val PALETTES = listOf(
        "#FF7A59" to "#FF3D77", "#4FACFE" to "#00F2FE", "#43E97B" to "#38F9D7", "#FA709A" to "#FEE140",
        "#A18CD1" to "#FBC2EB", "#667EEA" to "#764BA2", "#F6D365" to "#FDA085", "#2E7D4F" to "#A8E063",
    )

    fun gradientFor(id: String): Pair<Int, Int> {
        val (a, b) = PALETTES[abs(id.hashCode()) % PALETTES.size]
        return Color.parseColor(a) to Color.parseColor(b)
    }
}
