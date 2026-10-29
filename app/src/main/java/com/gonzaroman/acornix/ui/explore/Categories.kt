package com.gonzaroman.acornix.ui.explore

import androidx.annotation.StringRes
import com.gonzaroman.acornix.R

/** A gallery category: the App Store's, without the ones that don't fit apps made in Acornix. */
data class Category(val id: String, val emoji: String, @StringRes val label: Int)

/** Same ids and order as CATEGORIES in the gallery repository (scripts/build_catalog.py). */
val CATEGORIES = listOf(
    Category("games", "🎮", R.string.cat_games),
    Category("entertainment", "🎭", R.string.cat_entertainment),
    Category("utilities", "🧰", R.string.cat_utilities),
    Category("productivity", "✅", R.string.cat_productivity),
    Category("finance", "💶", R.string.cat_finance),
    Category("business", "💼", R.string.cat_business),
    Category("education", "🎓", R.string.cat_education),
    Category("reference", "📚", R.string.cat_reference),
    Category("health-fitness", "💪", R.string.cat_health_fitness),
    Category("sports", "⚽", R.string.cat_sports),
    Category("music", "🎵", R.string.cat_music),
    Category("photo-video", "📷", R.string.cat_photo_video),
    Category("graphics-design", "🎨", R.string.cat_graphics_design),
    Category("lifestyle", "🏠", R.string.cat_lifestyle),
    Category("food-drink", "🍳", R.string.cat_food_drink),
    Category("travel", "✈️", R.string.cat_travel),
    Category("kids", "🧸", R.string.cat_kids),
    Category("shopping", "🛒", R.string.cat_shopping),
    Category("weather", "🌦️", R.string.cat_weather),
)

fun category(id: String): Category? = CATEGORIES.firstOrNull { it.id == id }
