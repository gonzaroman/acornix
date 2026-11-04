package com.gonzaroman.acornix.core

/**
 * Ready-made step-by-step shortcuts that show what shortcuts can do: the tutorial's "Create this one" buttons,
 * the examples installed with Acornix and the ones published in the community gallery.
 */
data class ShortcutTemplate(
    val id: String,
    val emoji: String,
    val name: String,
    val description: String,
    val recipe: ShortcutRecipe,
    /** A good trigger for it, explained to the user ("Put it on the car's Bluetooth"). */
    val tip: String = "",
)

object ShortcutTemplates {
    private fun s(action: String, vararg p: Pair<String, String>) = ShortcutStep(action, mapOf(*p))

    fun all(spanish: Boolean): List<ShortcutTemplate> = if (spanish) ES else EN

    fun find(id: String, spanish: Boolean) = all(spanish).firstOrNull { it.id == id }

    private val ES = listOf(
        ShortcutTemplate(
            "bedtime", "😴", "Modo dormir", "No molestar, brillo bajo, volumen bajo y la alarma de mañana, con un toque.",
            ShortcutRecipe(steps = listOf(
                s("ask", "question" to "¿A qué hora suena la alarma? (por ejemplo 07:30)", "type" to "text", "var" to "despertador", "remember" to "yes"),
                s("dnd", "on" to "on"),
                s("brightness", "level" to "10"),
                s("volume", "level" to "20", "stream" to "media"),
                s("alarm", "time" to "{despertador}", "label" to "Buenos días"),
                s("speak", "text" to "Buenas noches. Alarma a las {despertador}"),
            )),
            "Ponlo a las 23:00 en «Automatizar» o en una pegatina NFC en la mesilla.",
        ),
        ShortcutTemplate(
            "home", "🏠", "He llegado", "Avisa por WhatsApp de que has llegado, con tu ubicación.",
            ShortcutRecipe(steps = listOf(
                s("ask", "question" to "¿A quién aviso? Número con prefijo, por ejemplo +34600000000", "type" to "text", "var" to "contacto", "remember" to "yes"),
                s("whatsapp", "number" to "{contacto}", "text" to "He llegado bien 🏠 {ubicación}"),
            )),
            "Automatízalo al conectarte al wifi de casa.",
        ),
        ShortcutTemplate(
            "sos", "🆘", "SOS con mi ubicación", "Manda tu ubicación a alguien de confianza y hace parpadear la linterna.",
            ShortcutRecipe(steps = listOf(
                s("ask", "question" to "¿A quién pido ayuda? Número con prefijo", "type" to "text", "var" to "contacto", "remember" to "yes"),
                s("whatsapp", "number" to "{contacto}", "text" to "🆘 Necesito ayuda. Estoy aquí: {ubicación}"),
                s("flashlight", "mode" to "sos"),
            )),
            "Ponlo en tu pantalla de inicio o en los ajustes rápidos para tenerlo a un toque.",
        ),
        ShortcutTemplate(
            "weather", "🌦️", "¿Qué tiempo hace?", "Busca tu ciudad en internet y te dice la temperatura en voz alta.",
            ShortcutRecipe(steps = listOf(
                s("ask", "question" to "¿De qué ciudad?", "type" to "text", "var" to "ciudad", "remember" to "yes"),
                s("getUrl", "url" to "https://geocoding-api.open-meteo.com/v1/search?count=1&name={ciudad}", "path" to "results.0.latitude", "var" to "lat"),
                s("getUrl", "url" to "https://geocoding-api.open-meteo.com/v1/search?count=1&name={ciudad}", "path" to "results.0.longitude", "var" to "lon"),
                s("getUrl", "url" to "https://api.open-meteo.com/v1/forecast?latitude={lat}&longitude={lon}&current=temperature_2m", "path" to "current.temperature_2m", "var" to "temperatura"),
                s("speak", "text" to "En {ciudad} hace {temperatura} grados"),
                s("show", "text" to "🌡️ {ciudad}: {temperatura} °C"),
            )),
            "Prográmalo cada mañana a las 8:00.",
        ),
        ShortcutTemplate(
            "battery", "🔋", "¿Cómo va la batería?", "Si queda poca, baja el brillo y te avisa; si no, te dice cuánta te queda.",
            ShortcutRecipe(steps = listOf(
                s("if", "left" to "{batería}", "op" to "less", "right" to "20"),
                s("brightness", "level" to "25"),
                s("speak", "text" to "Batería baja, solo queda un {batería} por ciento. Conecta el cargador."),
                s("else"),
                s("speak", "text" to "Tienes un {batería} por ciento de batería"),
                s("endif"),
            )),
            "Automatízalo con «La batería baja del 20 %».",
        ),
        ShortcutTemplate(
            "kitchen", "🍳", "Temporizador de cocina", "Elige qué cocinas y pone el temporizador justo.",
            ShortcutRecipe(steps = listOf(
                s("menu", "question" to "¿Qué cocinas?", "options" to "Huevo pasado por agua\nPasta\nArroz", "var" to "plato"),
                s("if", "left" to "{plato}", "op" to "contains", "right" to "Huevo"),
                s("setVar", "var" to "minutos", "value" to "6"),
                s("endif"),
                s("if", "left" to "{plato}", "op" to "equals", "right" to "Pasta"),
                s("setVar", "var" to "minutos", "value" to "10"),
                s("endif"),
                s("if", "left" to "{plato}", "op" to "equals", "right" to "Arroz"),
                s("setVar", "var" to "minutos", "value" to "18"),
                s("endif"),
                s("timer", "minutes" to "{minutos}", "label" to "{plato}"),
                s("speak", "text" to "{plato}: {minutos} minutos"),
            )),
        ),
        ShortcutTemplate(
            "car", "🚗", "Modo coche", "Sube el volumen, abre Maps y pone tu música.",
            ShortcutRecipe(steps = listOf(
                s("volume", "level" to "80", "stream" to "media"),
                s("launchApp", "name" to "Maps"),
                s("wait", "seconds" to "2"),
                s("media", "action" to "play"),
                s("speak", "text" to "¡Buen viaje!"),
            )),
            "Automatízalo con «Se conecta un Bluetooth» y elige el de tu coche.",
        ),
        ShortcutTemplate(
            "water", "💧", "Bebe agua", "Te recuerda beber agua con una vibración y un aviso.",
            ShortcutRecipe(steps = listOf(
                s("vibrate"),
                s("notify", "title" to "💧 Hora de beber agua", "text" to "Son las {hora}. Un vaso y sigues."),
            )),
            "Prográmalo varias veces al día en «Automatizar».",
        ),
        ShortcutTemplate(
            "countdown", "🔁", "Cuenta atrás", "Cuenta hacia atrás en voz alta: repetir, calcular y variables.",
            ShortcutRecipe(steps = listOf(
                s("ask", "question" to "¿Desde cuánto cuento?", "type" to "number", "var" to "desde", "default" to "5"),
                s("repeat", "times" to "{desde}"),
                s("calc", "expr" to "{desde} - {repetición} + 1", "var" to "numero"),
                s("speak", "text" to "{numero}"),
                s("wait", "seconds" to "1"),
                s("endrepeat"),
                s("speak", "text" to "¡Ya!"),
                s("vibrate"),
            )),
        ),
    )

    private val EN = listOf(
        ShortcutTemplate(
            "bedtime", "😴", "Bedtime mode", "Do not disturb, low brightness, low volume and tomorrow's alarm, in one tap.",
            ShortcutRecipe(steps = listOf(
                s("ask", "question" to "What time is the alarm? (e.g. 07:30)", "type" to "text", "var" to "wakeup", "remember" to "yes"),
                s("dnd", "on" to "on"),
                s("brightness", "level" to "10"),
                s("volume", "level" to "20", "stream" to "media"),
                s("alarm", "time" to "{wakeup}", "label" to "Good morning"),
                s("speak", "text" to "Good night. Alarm at {wakeup}"),
            )),
            "Set it for 11 pm in “Automate”, or on an NFC sticker on your nightstand.",
        ),
        ShortcutTemplate(
            "home", "🏠", "I'm home", "Tells someone on WhatsApp that you got home, with your location.",
            ShortcutRecipe(steps = listOf(
                s("ask", "question" to "Who should I tell? Number with country code, e.g. +15551234567", "type" to "text", "var" to "contact", "remember" to "yes"),
                s("whatsapp", "number" to "{contact}", "text" to "I got home safe 🏠 {location}"),
            )),
            "Automate it when you connect to your home wifi.",
        ),
        ShortcutTemplate(
            "sos", "🆘", "SOS with my location", "Sends your location to someone you trust and flashes the flashlight.",
            ShortcutRecipe(steps = listOf(
                s("ask", "question" to "Who should I ask for help? Number with country code", "type" to "text", "var" to "contact", "remember" to "yes"),
                s("whatsapp", "number" to "{contact}", "text" to "🆘 I need help. I'm here: {location}"),
                s("flashlight", "mode" to "sos"),
            )),
            "Put it on your home screen or in Quick Settings so it's one tap away.",
        ),
        ShortcutTemplate(
            "weather", "🌦️", "What's the weather?", "Looks up your city online and says the temperature out loud.",
            ShortcutRecipe(steps = listOf(
                s("ask", "question" to "Which city?", "type" to "text", "var" to "city", "remember" to "yes"),
                s("getUrl", "url" to "https://geocoding-api.open-meteo.com/v1/search?count=1&name={city}", "path" to "results.0.latitude", "var" to "lat"),
                s("getUrl", "url" to "https://geocoding-api.open-meteo.com/v1/search?count=1&name={city}", "path" to "results.0.longitude", "var" to "lon"),
                s("getUrl", "url" to "https://api.open-meteo.com/v1/forecast?latitude={lat}&longitude={lon}&current=temperature_2m", "path" to "current.temperature_2m", "var" to "temperature"),
                s("speak", "text" to "It's {temperature} degrees in {city}"),
                s("show", "text" to "🌡️ {city}: {temperature} °C"),
            )),
            "Schedule it every morning at 8:00.",
        ),
        ShortcutTemplate(
            "battery", "🔋", "How's my battery?", "If it's low it dims the screen and warns you; otherwise it says how much is left.",
            ShortcutRecipe(steps = listOf(
                s("if", "left" to "{battery}", "op" to "less", "right" to "20"),
                s("brightness", "level" to "25"),
                s("speak", "text" to "Battery low, only {battery} percent left. Plug in the charger."),
                s("else"),
                s("speak", "text" to "You have {battery} percent battery"),
                s("endif"),
            )),
            "Automate it with “The battery drops below 20 %”.",
        ),
        ShortcutTemplate(
            "kitchen", "🍳", "Kitchen timer", "Pick what you're cooking and it sets the right timer.",
            ShortcutRecipe(steps = listOf(
                s("menu", "question" to "What are you cooking?", "options" to "Soft-boiled egg\nPasta\nRice", "var" to "dish"),
                s("if", "left" to "{dish}", "op" to "contains", "right" to "egg"),
                s("setVar", "var" to "minutes", "value" to "6"),
                s("endif"),
                s("if", "left" to "{dish}", "op" to "equals", "right" to "Pasta"),
                s("setVar", "var" to "minutes", "value" to "10"),
                s("endif"),
                s("if", "left" to "{dish}", "op" to "equals", "right" to "Rice"),
                s("setVar", "var" to "minutes", "value" to "18"),
                s("endif"),
                s("timer", "minutes" to "{minutes}", "label" to "{dish}"),
                s("speak", "text" to "{dish}: {minutes} minutes"),
            )),
        ),
        ShortcutTemplate(
            "car", "🚗", "Car mode", "Turns the volume up, opens Maps and plays your music.",
            ShortcutRecipe(steps = listOf(
                s("volume", "level" to "80", "stream" to "media"),
                s("launchApp", "name" to "Maps"),
                s("wait", "seconds" to "2"),
                s("media", "action" to "play"),
                s("speak", "text" to "Have a good trip!"),
            )),
            "Automate it with “A Bluetooth device connects” and pick your car.",
        ),
        ShortcutTemplate(
            "water", "💧", "Drink water", "Reminds you to drink water with a buzz and a notification.",
            ShortcutRecipe(steps = listOf(
                s("vibrate"),
                s("notify", "title" to "💧 Time to drink water", "text" to "It's {time}. One glass and carry on."),
            )),
            "Schedule it a few times a day in “Automate”.",
        ),
        ShortcutTemplate(
            "countdown", "🔁", "Countdown", "Counts down out loud: repeat, calculate and variables.",
            ShortcutRecipe(steps = listOf(
                s("ask", "question" to "Count down from?", "type" to "number", "var" to "from", "default" to "5"),
                s("repeat", "times" to "{from}"),
                s("calc", "expr" to "{from} - {repeat} + 1", "var" to "number"),
                s("speak", "text" to "{number}"),
                s("wait", "seconds" to "1"),
                s("endrepeat"),
                s("speak", "text" to "Go!"),
                s("vibrate"),
            )),
        ),
    )
}

/**
 * Puts a few example shortcuts in the Shortcuts tab once, for new and existing users, in the language picked in
 * Acornix, so the tab shows what shortcuts can do from day one. Deleting them is fine: they are not added again.
 */
class ShortcutSamples(private val context: android.content.Context, private val apps: com.gonzaroman.acornix.data.AppRepository) {
    suspend fun installIfNeeded(language: String) {
        val prefs = context.getSharedPreferences("shortcut_samples", android.content.Context.MODE_PRIVATE)
        if (prefs.getInt(KEY, 0) >= VERSION) return
        val spanish = language == "es"
        INSTALLED.mapNotNull { ShortcutTemplates.find(it, spanish) }.forEach { t ->
            if (apps.apps.value.none { it.name == t.name }) {
                apps.create(t.name, t.recipe.html(t.name, t.emoji), com.gonzaroman.acornix.data.AppKind.FEATURE,
                    description = t.description, emoji = t.emoji, note = "Example shortcut")
            }
        }
        prefs.edit().putInt(KEY, VERSION).apply()
    }

    private companion object {
        const val KEY = "installed"
        const val VERSION = 1
        val INSTALLED = listOf("bedtime", "weather", "battery", "kitchen")
    }
}
