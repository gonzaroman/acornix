package com.gonzaroman.acornix.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A shortcut built step by step, without AI: a list of actions with their settings. It is saved inside the
 * shortcut's own index.html (a JSON script tag), so it travels with ZIPs and the community gallery and can
 * be edited again; the same file has a small runtime that performs the steps through window.Acornix.
 */
@Serializable
data class ShortcutRecipe(val version: Int = 1, val steps: List<ShortcutStep> = emptyList()) {

    fun toJson(): String = json.encodeToString(serializer(), this)

    /** The complete index.html for this shortcut. */
    fun html(name: String, emoji: String): String = TEMPLATE
        .replace("{{TITLE}}", escapeHtml("$emoji $name"))
        .replace("{{NAME}}", escapeHtml(name))
        .replace("{{EMOJI}}", escapeHtml(emoji))
        // Inside <script>, "</" would end the tag early.
        .replace("{{RECIPE}}", toJson().replace("</", "<\\/"))

    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        private val TAG = Regex("""<script type="application/json" id="acornix-recipe">(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)

        /** The recipe of a step-by-step shortcut, or null for any other app (e.g. one written by the AI). */
        fun fromHtml(html: String): ShortcutRecipe? =
            TAG.find(html)?.groupValues?.get(1)?.let { runCatching { json.decodeFromString(serializer(), it.replace("<\\/", "</")) }.getOrNull() }

        private fun escapeHtml(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

        // The runtime: runs every step in order, showing each one, then Acornix.done(). Steps that fail (no
        // permission yet, no flashlight…) are marked and the page stays open so the user can see why.
        private val TEMPLATE = """
<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no">
<title>{{TITLE}}</title>
<style>
  * { box-sizing: border-box; }
  html, body { margin: 0; min-height: 100%; background: #12101c; color: #f4f1ff; font-family: system-ui, -apple-system, Roboto, sans-serif; }
  body { background: radial-gradient(120% 80% at 50% 0%, #3b2b78 0%, #12101c 60%); min-height: 100vh;
         padding: calc(env(safe-area-inset-top) + 40px) 24px calc(env(safe-area-inset-bottom) + 24px); }
  .icon { width: 96px; height: 96px; margin: 0 auto 14px; border-radius: 28px; display: grid; place-items: center; font-size: 52px;
          background: linear-gradient(135deg, #7c5cff, #ff6bb5); box-shadow: 0 10px 40px rgba(124, 92, 255, .45); }
  h1 { margin: 0; text-align: center; font-size: 26px; }
  #status { text-align: center; color: #b9b2d9; margin: 6px 0 22px; }
  ul { list-style: none; margin: 0 auto; padding: 0; max-width: 460px; }
  li { display: flex; gap: 12px; align-items: center; padding: 12px 14px; margin-bottom: 8px; border-radius: 14px;
       background: rgba(255, 255, 255, .06); color: #cfc9ea; transition: background .2s; }
  li .s { width: 22px; text-align: center; }
  li.run { background: rgba(124, 92, 255, .25); color: #fff; }
  li.ok .s { color: #5ee6a0; }
  li.bad { background: rgba(255, 120, 120, .14); }
  button { display: block; margin: 22px auto 0; padding: 14px 28px; border: 0; border-radius: 999px; font: inherit; font-weight: 700;
           background: #f4f1ff; color: #22193f; }
  [hidden] { display: none !important; }
</style>
</head>
<body>
<div class="icon">{{EMOJI}}</div>
<h1>{{NAME}}</h1>
<div id="status"></div>
<ul id="steps"></ul>
<button id="close" hidden></button>
<script type="application/json" id="acornix-recipe">{{RECIPE}}</script>
<script>
(function () {
  var A = window.Acornix;
  var L = ((A && A.language) || navigator.language || 'en').toLowerCase().indexOf('es') === 0 ? 'es' : 'en';
  var T = {
    es: { running: 'Haciéndolo…', done: 'Hecho', failed: 'Algunos pasos no se han podido hacer. Si Acornix te ha pedido un permiso, dalo y vuelve a tocar el atajo.', close: 'Cerrar', outside: 'Este atajo funciona dentro de Acornix.', empty: 'Este atajo aún no tiene pasos.' },
    en: { running: 'Doing it…', done: 'Done', failed: 'Some steps couldn\'t be done. If Acornix asked you for a permission, give it and tap the shortcut again.', close: 'Close', outside: 'This shortcut works inside Acornix.', empty: 'This shortcut has no steps yet.' }
  }[L];
  var recipe = JSON.parse(document.getElementById('acornix-recipe').textContent || '{}');
  var steps = recipe.steps || [];
  var list = document.getElementById('steps'), status = document.getElementById('status'), closeBtn = document.getElementById('close');
  var DESC = {
    volume: function (p) { return (L === 'es' ? '🔊 Volumen ' : '🔊 Volume ') + (p.level || 0) + ' %'; },
    ringer: function (p) { return { normal: L === 'es' ? '🔔 Sonido normal' : '🔔 Sound on', vibrate: L === 'es' ? '📳 Solo vibración' : '📳 Vibrate only', silent: L === 'es' ? '🔕 Silencio' : '🔕 Silent' }[p.mode] || '🔔'; },
    dnd: function (p) { return (p.on === 'off' ? (L === 'es' ? '🌙 Quitar No molestar' : '🌙 Do not disturb off') : (L === 'es' ? '🌙 No molestar' : '🌙 Do not disturb')); },
    brightness: function (p) { return (L === 'es' ? '☀️ Brillo ' : '☀️ Brightness ') + (p.level || 0) + ' %'; },
    media: function (p) { return '🎵 ' + ({ play: '▶️', pause: '⏸️', next: '⏭️', previous: '⏮️' }[p.action] || '⏯️'); },
    alarm: function (p) { return (L === 'es' ? '⏰ Alarma ' : '⏰ Alarm ') + (p.time || ''); },
    timer: function (p) { return (L === 'es' ? '⏲️ Temporizador ' : '⏲️ Timer ') + (p.minutes || 0) + ' min'; },
    whatsapp: function (p) { return '💬 WhatsApp' + ((p.contact || p.number) ? ' · ' + (p.contact || p.number) : ''); },
    sms: function (p) { return '✉️ SMS · ' + (p.contact || p.number || ''); },
    call: function (p) { return (L === 'es' ? '📞 Llamar · ' : '📞 Call · ') + (p.contact || p.number || ''); },
    maps: function (p) { return '🗺️ ' + (p.query || ''); },
    launchApp: function (p) { return (L === 'es' ? '📱 Abrir ' : '📱 Open ') + (p.name || ''); },
    openUrl: function (p) { return '🌐 ' + (p.url || ''); },
    speak: function (p) { return '🗣️ ' + (p.text || ''); },
    notify: function (p) { return '🔔 ' + (p.title || ''); },
    vibrate: function () { return L === 'es' ? '📳 Vibrar' : '📳 Vibrate'; },
    flashlight: function (p) { return '🔦 ' + ({ on: L === 'es' ? 'Linterna encendida' : 'Flashlight on', off: L === 'es' ? 'Linterna apagada' : 'Flashlight off', sos: 'SOS' }[p.mode] || ''); },
    wait: function (p) { return (L === 'es' ? '⏳ Esperar ' : '⏳ Wait ') + (p.seconds || 0) + ' s'; },
    copy: function (p) { return (L === 'es' ? '📋 Copiar · ' : '📋 Copy · ') + (p.text || ''); },
    share: function (p) { return (L === 'es' ? '📤 Compartir · ' : '📤 Share · ') + (p.text || ''); }
  };
  var items = steps.map(function (s) {
    var li = document.createElement('li');
    li.innerHTML = '<span class="s">•</span><span></span>';
    li.lastChild.textContent = (DESC[s.action] || function () { return s.action; })(s.params || {});
    list.appendChild(li);
    return li;
  });
  function sleep(ms) { return new Promise(function (r) { setTimeout(r, ms); }); }
  function pad(n) { return (n < 10 ? '0' : '') + n; }
  var place = null;
  function where() {
    if (place) return place;
    place = new Promise(function (ok) {
      if (!navigator.geolocation) return ok('');
      navigator.geolocation.getCurrentPosition(function (p) {
        ok('https://maps.google.com/?q=' + p.coords.latitude.toFixed(6) + ',' + p.coords.longitude.toFixed(6));
      }, function () { ok(''); }, { enableHighAccuracy: true, timeout: 15000, maximumAge: 60000 });
    });
    return place;
  }
  // {time} {date} {location} {battery}, in English or Spanish.
  async function fill(text) {
    text = String(text || '');
    var now = new Date();
    text = text.replace(/\{(hora|time)\}/gi, pad(now.getHours()) + ':' + pad(now.getMinutes()))
               .replace(/\{(fecha|date)\}/gi, now.toLocaleDateString(L));
    if (/\{(ubicaci[oó]n|location)\}/i.test(text)) { var w = await where(); text = text.replace(/\{(ubicaci[oó]n|location)\}/gi, w); }
    if (/\{(bater[ií]a|battery)\}/i.test(text)) { var b = await A.battery(); text = text.replace(/\{(bater[ií]a|battery)\}/gi, b.level + ' %'); }
    return text;
  }
  async function run(s) {
    var p = s.params || {};
    switch (s.action) {
      case 'volume': return A.volume(+p.level || 0, p.stream || 'media');
      case 'ringer': return A.ringer(p.mode || 'normal');
      case 'dnd': return A.doNotDisturb(p.on !== 'off');
      case 'brightness': return A.brightness(+p.level || 0);
      case 'media': return A.media(p.action || 'toggle');
      case 'alarm': var t = String(p.time || '07:00').split(':'); return A.alarm(+t[0] || 0, +t[1] || 0, await fill(p.label || ''));
      case 'timer': return A.timer(Math.max(1, Math.round((+p.minutes || 1) * 60)), await fill(p.label || ''));
      case 'whatsapp': return A.whatsapp(p.number || null, await fill(p.text));
      case 'sms': return A.sms(p.number || '', await fill(p.text));
      case 'call': return A.call(p.number || '');
      case 'maps': return A.maps(await fill(p.query));
      case 'launchApp': var r = await A.launchApp(p['package'] || p.name || '', p.name || ''); return r === true;
      case 'openUrl': return A.openUrl(await fill(p.url));
      case 'speak': return A.speak(await fill(p.text), L === 'es' ? 'es-ES' : 'en-US');
      case 'notify': return A.notify(await fill(p.title), await fill(p.text));
      case 'vibrate': return A.vibrate([200, 100, 200]);
      case 'flashlight':
        if (p.mode === 'sos') {
          var unit = 200, pattern = [1,1,1,3,3,3,1,1,1];
          for (var i = 0; i < pattern.length; i++) { await A.flashlight(true); await sleep(unit * pattern[i]); await A.flashlight(false); await sleep(unit); }
          return true;
        }
        return A.flashlight(p.mode !== 'off');
      case 'wait': await sleep(Math.min(600, Math.max(0, +p.seconds || 0)) * 1000); return true;
      case 'copy': return A.copy(await fill(p.text));
      case 'share': return A.share(await fill(p.text));
    }
    return false;
  }
  async function main() {
    if (!A) { status.textContent = T.outside; return; }
    if (!steps.length) { status.textContent = T.empty; return; }
    status.textContent = T.running;
    var failed = false;
    for (var i = 0; i < steps.length; i++) {
      items[i].className = 'run';
      var ok;
      try { ok = await run(steps[i]); } catch (e) { ok = false; }
      items[i].className = ok === false ? 'bad' : 'ok';
      items[i].firstChild.textContent = ok === false ? '⚠️' : '✓';
      if (ok === false) failed = true;
      await sleep(150);
    }
    if (failed) {
      status.textContent = T.failed;
      closeBtn.textContent = T.close; closeBtn.hidden = false;
      closeBtn.onclick = function () { A.done(); };
    } else {
      status.textContent = T.done;
      await sleep(500);
      A.done(document.querySelector('h1').textContent);
    }
  }
  main();
})();
</script>
</body>
</html>
""".trimStart()
    }
}

@Serializable
data class ShortcutStep(val action: String, val params: Map<String, String> = emptyMap())
