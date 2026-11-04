package com.gonzaroman.acornix.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A shortcut built step by step, like iPhone Shortcuts: a list of actions with their settings. Steps can ask
 * something, pick from a menu, read a web API and keep the answer in a variable that later steps use as {name};
 * "if" and "repeat" blocks run their inner steps conditionally or several times. The recipe is saved inside the
 * shortcut's own index.html (a JSON script tag), so it travels with ZIPs and the community gallery and can be
 * edited again; the same file has the runtime that performs the steps through window.Acornix.
 */
@Serializable
data class ShortcutRecipe(
    val version: Int = 2,
    val steps: List<ShortcutStep> = emptyList(),
    /** The same steps with their texts in other languages ({"es": […]}), for shortcuts shared in the gallery. */
    val translations: Map<String, List<ShortcutStep>> = emptyMap(),
) {
    /** The steps in [language] when there is a translation, otherwise the original ones. */
    fun stepsFor(language: String): List<ShortcutStep> = translations[language]?.takeIf { it.isNotEmpty() } ?: steps


    fun toJson(): String = json.encodeToString(serializer(), this)

    /** The complete index.html for this shortcut. */
    fun html(name: String, emoji: String): String = TEMPLATE
        .replace("{{TITLE}}", escapeHtml("$emoji $name"))
        .replace("{{NAME}}", escapeHtml(name))
        .replace("{{EMOJI}}", escapeHtml(emoji))
        // Inside <script>, "</" would end the tag early.
        .replace("{{RECIPE}}", toJson().replace("</", "<\\/"))

    /** How deep each step is nested inside "if"/"repeat" blocks, for the editor's indentation. */
    fun depths(): List<Int> {
        var d = 0
        return steps.map { s ->
            when (s.action) {
                "else" -> (d - 1).coerceAtLeast(0)
                "endif", "endrepeat" -> { d = (d - 1).coerceAtLeast(0); d }
                "if", "repeat" -> d.also { d++ }
                else -> d
            }
        }
    }

    /** The variables earlier steps define before step [index], for the editor's variable chips. */
    fun variablesBefore(index: Int): List<String> =
        steps.take(index).mapNotNull { s -> s.params["var"]?.trim()?.takeIf { it.isNotEmpty() && s.action in PRODUCERS } }.distinct()

    companion object {
        /** Every action the runtime knows. */
        val ACTIONS = setOf(
            "volume", "ringer", "dnd", "media", "brightness", "flashlight", "vibrate", "alarm", "timer", "wait",
            "whatsapp", "sms", "call", "speak", "notify", "launchApp", "maps", "youtube", "openUrl", "copy", "share",
            "ask", "menu", "if", "else", "endif", "repeat", "endrepeat", "stop", "getUrl", "setVar", "calc", "show",
        )

        /** Actions whose result goes into a variable ("var"). */
        val PRODUCERS = setOf("ask", "menu", "getUrl", "setVar", "calc")

        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        private val TAG = Regex("""<script type="application/json" id="acornix-recipe">(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)

        /** The recipe of a step-by-step shortcut, or null for any other app (e.g. one written by the AI). */
        fun fromHtml(html: String): ShortcutRecipe? =
            TAG.find(html)?.groupValues?.get(1)?.let { fromJson(it.replace("<\\/", "</")) }

        fun fromJson(text: String): ShortcutRecipe? = runCatching { json.decodeFromString(serializer(), text) }.getOrNull()

        private fun escapeHtml(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

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
         padding: calc(env(safe-area-inset-top) + 36px) 22px calc(env(safe-area-inset-bottom) + 24px); }
  .icon { width: 88px; height: 88px; margin: 0 auto 12px; border-radius: 26px; display: grid; place-items: center; font-size: 48px;
          background: linear-gradient(135deg, #7c5cff, #ff6bb5); box-shadow: 0 10px 40px rgba(124, 92, 255, .45); }
  h1 { margin: 0; text-align: center; font-size: 25px; }
  #status { text-align: center; color: #b9b2d9; margin: 6px 0 18px; min-height: 1.2em; }
  ul { list-style: none; margin: 0 auto; padding: 0; max-width: 480px; }
  li { display: flex; gap: 10px; align-items: center; padding: 10px 12px; margin-bottom: 6px; border-radius: 12px;
       background: rgba(255, 255, 255, .06); color: #cfc9ea; font-size: 15px; }
  li .s { width: 20px; text-align: center; flex: none; }
  li.run { background: rgba(124, 92, 255, .28); color: #fff; }
  li.ok .s { color: #5ee6a0; }
  li.bad { background: rgba(255, 120, 120, .14); }
  li.block { background: transparent; color: #9d96c4; font-weight: 600; }
  #sheet { position: fixed; inset: 0; background: rgba(10, 8, 20, .72); display: flex; align-items: flex-end; justify-content: center; }
  #sheet .card { width: 100%; max-width: 520px; background: #241d42; border-radius: 26px 26px 0 0; padding: 24px 20px calc(env(safe-area-inset-bottom) + 22px); }
  #sheet h2 { margin: 0 0 14px; font-size: 19px; line-height: 1.35; white-space: pre-wrap; }
  #sheet .result { max-height: 50vh; overflow: auto; white-space: pre-wrap; line-height: 1.45; color: #e9e5ff; margin-bottom: 16px; font-size: 17px; }
  #sheet input { width: 100%; font: inherit; font-size: 17px; padding: 14px; border-radius: 14px; border: 2px solid #4a3f86; background: #171230; color: #fff; outline: none; }
  #sheet .opts { display: grid; gap: 8px; max-height: 55vh; overflow: auto; }
  button { font: inherit; font-weight: 700; border: 0; border-radius: 16px; padding: 14px 18px; background: #f4f1ff; color: #22193f; }
  button.opt { background: #362c63; color: #fff; text-align: left; }
  .row { display: flex; gap: 10px; margin-top: 14px; } .row button { flex: 1; }
  .row .alt { background: transparent; color: #cfc9ea; border: 2px solid #4a3f86; }
  #close { display: block; margin: 20px auto 0; }
  [hidden] { display: none !important; }
</style>
</head>
<body>
<div class="icon">{{EMOJI}}</div>
<h1>{{NAME}}</h1>
<div id="status"></div>
<ul id="steps"></ul>
<button id="close" hidden></button>
<div id="sheet" hidden><div class="card" id="card"></div></div>
<script type="application/json" id="acornix-recipe">{{RECIPE}}</script>
<script>
(function () {
  var A = window.Acornix;
  var L = ((A && A.language) || navigator.language || 'en').toLowerCase().indexOf('es') === 0 ? 'es' : 'en';
  var T = {
    es: { needControl: 'El mensaje está escrito pero no enviado. Para que se envíe solo, activa el Control total en Acornix › Ajustes.', preview: 'Vista previa: así es tu atajo. Ábrelo para que lo haga.', running: 'Haciéndolo…', done: 'Hecho', failed: 'Algunos pasos no se han podido hacer. Si Acornix te ha pedido un permiso, dalo y vuelve a tocar el atajo.', close: 'Cerrar', outside: 'Este atajo funciona dentro de Acornix.', empty: 'Este atajo aún no tiene pasos.', ok: 'Aceptar', cancel: 'Cancelar', stopped: 'Cancelado', next: 'Continuar' },
    en: { needControl: 'The message is written but not sent. To send it on its own, turn on Total control in Acornix › Settings.', preview: 'Preview: this is your shortcut. Open it to run it.', running: 'Doing it…', done: 'Done', failed: 'Some steps couldn\'t be done. If Acornix asked you for a permission, give it and tap the shortcut again.', close: 'Close', outside: 'This shortcut works inside Acornix.', empty: 'This shortcut has no steps yet.', ok: 'OK', cancel: 'Cancel', stopped: 'Cancelled', next: 'Continue' }
  }[L];
  var recipe = JSON.parse(document.getElementById('acornix-recipe').textContent || '{}');
  var steps = (recipe.translations && recipe.translations[L] && recipe.translations[L].length) ? recipe.translations[L] : (recipe.steps || []);
  var list = document.getElementById('steps'), status = document.getElementById('status'), closeBtn = document.getElementById('close');
  var sheet = document.getElementById('sheet'), card = document.getElementById('card');
  var vars = {};

  function norm(k) { return String(k == null ? '' : k).trim().toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, ''); }
  function sleep(ms) { return new Promise(function (r) { setTimeout(r, ms); }); }
  function pad(n) { return (n < 10 ? '0' : '') + n; }
  function esc(s) { return String(s).replace(/[&<>"]/g, function (c) { return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]; }); }

  // Built-in variables, in Spanish and English, computed only when a step uses them.
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
  var BUILT_IN = {
    hora: function () { var d = new Date(); return pad(d.getHours()) + ':' + pad(d.getMinutes()); },
    fecha: function () { return new Date().toLocaleDateString(L); },
    ubicacion: where,
    bateria: function () { return A.battery().then(function (b) { return b.level; }); },
    portapapeles: function () { return A.clipboard(); },
    entrada: function () { return A.input || ''; }
  };
  BUILT_IN.time = BUILT_IN.hora; BUILT_IN.date = BUILT_IN.fecha; BUILT_IN.location = BUILT_IN.ubicacion;
  BUILT_IN.battery = BUILT_IN.bateria; BUILT_IN.clipboard = BUILT_IN.portapapeles; BUILT_IN.input = BUILT_IN.entrada;

  async function value(name) {
    var k = norm(name);
    if (Object.prototype.hasOwnProperty.call(vars, k)) return vars[k];
    if (BUILT_IN[k]) { try { return String(await BUILT_IN[k]()); } catch (e) { return ''; } }
    return null;
  }
  // "Hello {name}" -> "Hello Ana". Unknown {things} are left as they are.
  async function fill(text) {
    text = String(text == null ? '' : text);
    var names = text.match(/\{[^{}]{1,40}\}/g) || [];
    for (var i = 0; i < names.length; i++) {
      var v = await value(names[i].slice(1, -1));
      if (v !== null) text = text.split(names[i]).join(v);
    }
    return text;
  }
  function setVar(name, v) { if (String(name || '').trim()) vars[norm(name)] = String(v == null ? '' : v); }

  // The sheet that asks, offers a menu or shows a result. Resolves with the answer, or null when cancelled.
  function ask(question, type, def) {
    return new Promise(function (done) {
      card.innerHTML = '<h2>' + esc(question) + '</h2><input id="answer"><div class="row"><button class="alt" id="no">' + T.cancel + '</button><button id="yes">' + T.ok + '</button></div>';
      var input = document.getElementById('answer');
      input.type = type === 'number' ? 'number' : 'text';
      input.value = def || '';
      sheet.hidden = false; setTimeout(function () { input.focus(); }, 50);
      function finish(v) { sheet.hidden = true; done(v); }
      document.getElementById('yes').onclick = function () { finish(input.value); };
      document.getElementById('no').onclick = function () { finish(null); };
      input.onkeydown = function (e) { if (e.key === 'Enter') finish(input.value); };
    });
  }
  function menu(question, options) {
    return new Promise(function (done) {
      card.innerHTML = '<h2>' + esc(question) + '</h2><div class="opts">' + options.map(function (o, i) {
        return '<button class="opt" data-i="' + i + '">' + esc(o) + '</button>';
      }).join('') + '</div><div class="row"><button class="alt" id="no">' + T.cancel + '</button></div>';
      sheet.hidden = false;
      card.querySelectorAll('.opt').forEach(function (b) { b.onclick = function () { sheet.hidden = true; done(options[+b.dataset.i]); }; });
      document.getElementById('no').onclick = function () { sheet.hidden = true; done(null); };
    });
  }
  function show(text) {
    return new Promise(function (done) {
      card.innerHTML = '<div class="result">' + esc(text) + '</div><button id="yes" style="width:100%">' + T.next + '</button>';
      sheet.hidden = false;
      document.getElementById('yes').onclick = function () { sheet.hidden = true; done(true); };
    });
  }
  // A value inside JSON: "current.temperature_2m", "items.0.name".
  function pick(data, path) {
    return String(path || '').split('.').filter(Boolean).reduce(function (o, k) { return o == null ? o : o[k]; }, data);
  }
  // "12,5 * (3 + 1) / 2" -> 25, with + - * / % and parentheses; no code is run. Empty values count as 0.
  function arithmetic(text) {
    var src = String(text).replace(/,/g, '.').replace(/\s+/g, ''), i = 0;
    function num() {
      var m = /^\d*\.?\d+/.exec(src.slice(i)) || /^\d+\.?/.exec(src.slice(i));
      if (!m) return null;
      i += m[0].length; return parseFloat(m[0]);
    }
    function factor() {
      if (src[i] === '-') { i++; var f = factor(); return f === null ? null : -f; }
      if (src[i] === '+') { i++; return factor(); }
      if (src[i] === '(') { i++; var e = expr(); if (src[i] !== ')') return null; i++; return e; }
      if (i >= src.length || ')*/%'.indexOf(src[i]) >= 0) return 0;
      return num();
    }
    function term() {
      var v = factor();
      while (v !== null && (src[i] === '*' || src[i] === '/' || src[i] === '%')) {
        var op = src[i++], r = factor(); if (r === null) return null;
        v = op === '*' ? v * r : op === '/' ? (r === 0 ? null : v / r) : v % r;
      }
      return v;
    }
    function expr() {
      var v = term();
      while (v !== null && (src[i] === '+' || src[i] === '-')) { var op = src[i++], r = term(); if (r === null) return null; v = op === '+' ? v + r : v - r; }
      return v;
    }
    var out = expr();
    return (out === null || i !== src.length || !isFinite(out)) ? null : out;
  }
  function compare(a, op, b) {
    var na = parseFloat(String(a).replace(',', '.')), nb = parseFloat(String(b).replace(',', '.'));
    var sa = norm(a), sb = norm(b);
    switch (op) {
      case 'equals': return sa === sb;
      case 'notEquals': return sa !== sb;
      case 'contains': return sa.indexOf(sb) >= 0;
      case 'greater': return na > nb;
      case 'less': return na < nb;
      case 'empty': return !String(a).trim();
      case 'notEmpty': return !!String(a).trim();
    }
    return false;
  }

  var DESC = {
    volume: function (p) { return (L === 'es' ? '🔊 Volumen ' : '🔊 Volume ') + (p.level || 0) + ' %'; },
    ringer: function (p) { return { normal: L === 'es' ? '🔔 Sonido normal' : '🔔 Sound on', vibrate: L === 'es' ? '📳 Solo vibración' : '📳 Vibrate only', silent: L === 'es' ? '🔕 Silencio' : '🔕 Silent' }[p.mode] || '🔔'; },
    dnd: function (p) { return (p.on === 'off' ? (L === 'es' ? '🌙 Quitar No molestar' : '🌙 Do not disturb off') : (L === 'es' ? '🌙 No molestar' : '🌙 Do not disturb')); },
    brightness: function (p) { return (L === 'es' ? '☀️ Brillo ' : '☀️ Brightness ') + (p.level || 0) + ' %'; },
    media: function (p) { return '🎵 ' + ({ play: '▶️', pause: '⏸️', next: '⏭️', previous: '⏮️' }[p.action] || '⏯️'); },
    alarm: function (p) { return (L === 'es' ? '⏰ Alarma ' : '⏰ Alarm ') + (p.time || ''); },
    timer: function (p) { return (L === 'es' ? '⏲️ Temporizador ' : '⏲️ Timer ') + (p.minutes || 0) + ' min'; },
    whatsapp: function (p) { return '💬 WhatsApp' + ((p.contact || p.number) ? ' · ' + (p.contact || p.number) : '') + (p.send === 'yes' ? ' ➤' : ''); },
    sms: function (p) { return '✉️ SMS · ' + (p.contact || p.number || '') + (p.send === 'yes' ? ' ➤' : ''); },
    call: function (p) { return (L === 'es' ? '📞 Llamar · ' : '📞 Call · ') + (p.contact || p.number || ''); },
    maps: function (p) { return '🗺️ ' + (p.query || ''); },
    launchApp: function (p) { return (L === 'es' ? '📱 Abrir ' : '📱 Open ') + (p.name || ''); },
    openUrl: function (p) { return '🌐 ' + (p.url || '') + (p.app ? ' · ' + p.app : ''); },
    youtube: function (p) { return '▶️ YouTube · ' + (p.query || ''); },
    speak: function (p) { return '🗣️ ' + (p.text || ''); },
    notify: function (p) { return '🔔 ' + (p.title || ''); },
    vibrate: function () { return L === 'es' ? '📳 Vibrar' : '📳 Vibrate'; },
    flashlight: function (p) { return '🔦 ' + ({ on: L === 'es' ? 'Linterna encendida' : 'Flashlight on', off: L === 'es' ? 'Linterna apagada' : 'Flashlight off', sos: 'SOS' }[p.mode] || ''); },
    wait: function (p) { return (L === 'es' ? '⏳ Esperar ' : '⏳ Wait ') + (p.seconds || 0) + ' s'; },
    copy: function (p) { return (L === 'es' ? '📋 Copiar · ' : '📋 Copy · ') + (p.text || ''); },
    share: function (p) { return (L === 'es' ? '📤 Compartir · ' : '📤 Share · ') + (p.text || ''); },
    ask: function (p) { return '❓ ' + (p.question || '') + (p['var'] ? ' → {' + p['var'] + '}' : ''); },
    menu: function (p) { return '📋 ' + (p.question || '') + (p['var'] ? ' → {' + p['var'] + '}' : ''); },
    getUrl: function (p) { return '🌍 ' + (p.url || '').replace(/^https?:\/\//, '').slice(0, 40) + (p['var'] ? ' → {' + p['var'] + '}' : ''); },
    setVar: function (p) { return '🏷️ {' + (p['var'] || '') + '} = ' + (p.value || ''); },
    calc: function (p) { return '🧮 {' + (p['var'] || '') + '} = ' + (p.expr || ''); },
    show: function (p) { return '💬 ' + (p.text || ''); },
    'if': function (p) { return (L === 'es' ? 'Si ' : 'If ') + (p.left || '') + ' ' + ({ equals: '=', notEquals: '≠', contains: (L === 'es' ? 'contiene' : 'contains'), greater: '>', less: '<', empty: (L === 'es' ? 'está vacío' : 'is empty'), notEmpty: (L === 'es' ? 'tiene algo' : 'has a value') }[p.op] || '') + ' ' + (p.op === 'empty' || p.op === 'notEmpty' ? '' : (p.right || '')); },
    'else': function () { return L === 'es' ? 'Si no' : 'Otherwise'; },
    endif: function () { return L === 'es' ? 'Fin del si' : 'End if'; },
    repeat: function (p) { return (L === 'es' ? 'Repetir ' : 'Repeat ') + (p.times || 1) + (L === 'es' ? ' veces' : ' times'); },
    endrepeat: function () { return L === 'es' ? 'Fin de repetir' : 'End repeat'; },
    stop: function () { return L === 'es' ? '⏹️ Terminar' : '⏹️ Stop'; }
  };
  var BLOCKS = { 'if': 1, 'else': 1, endif: 1, repeat: 1, endrepeat: 1 };

  // Matching block ends: if -> else/endif, else -> endif, repeat <-> endrepeat.
  var match = {};
  (function () {
    var stack = [];
    steps.forEach(function (s, i) {
      if (s.action === 'if' || s.action === 'repeat') stack.push(i);
      else if (s.action === 'else') { var o = stack[stack.length - 1]; if (o !== undefined && steps[o].action === 'if') { match[o] = i; stack[stack.length - 1] = i; } }
      else if (s.action === 'endif' || s.action === 'endrepeat') { var j = stack.pop(); if (j !== undefined) { match[j] = i; if (steps[j].action === 'repeat') match[i] = j; } }
    });
  })();

  var depth = 0;
  var items = steps.map(function (s) {
    if (s.action === 'endif' || s.action === 'endrepeat' || s.action === 'else') depth = Math.max(0, depth - 1);
    var li = document.createElement('li');
    li.style.marginLeft = (depth * 18) + 'px';
    if (BLOCKS[s.action]) li.className = 'block';
    li.innerHTML = '<span class="s">' + (BLOCKS[s.action] ? '' : '•') + '</span><span></span>';
    li.lastChild.textContent = (DESC[s.action] || function () { return s.action; })(s.params || {});
    list.appendChild(li);
    if (s.action === 'if' || s.action === 'repeat' || s.action === 'else') depth++;
    return li;
  });

  // Runs one action. Returns false when it couldn't be done, 'stop' to end the shortcut.
  async function run(s) {
    var p = s.params || {};
    switch (s.action) {
      case 'volume': return A.volume(+(await fill(p.level)) || 0, p.stream || 'media');
      case 'ringer': return A.ringer(p.mode || 'normal');
      case 'dnd': return A.doNotDisturb(p.on !== 'off');
      case 'brightness': return A.brightness(+(await fill(p.level)) || 0);
      case 'media': return A.media(p.action || 'toggle');
      case 'alarm': var t = String(await fill(p.time || '07:00')).split(':'); return A.alarm(+t[0] || 0, +t[1] || 0, await fill(p.label || ''));
      case 'timer': return A.timer(Math.max(1, Math.round((+(await fill(p.minutes)) || 1) * 60)), await fill(p.label || ''));
      case 'whatsapp':
        var w = await A.whatsapp((await fill(p.number)) || null, await fill(p.text), p.send === 'yes');
        if (w === 'failed' || w === false) return false;
        if (w === 'noControl') { await sleep(1500); await show(T.needControl); }
        return true;
      case 'sms': return A.sms(await fill(p.number), await fill(p.text), p.send === 'yes');
      case 'call': return A.call(await fill(p.number));
      case 'maps': return A.maps(await fill(p.query));
      case 'launchApp': var r = await A.launchApp(p['package'] || (await fill(p.name)), await fill(p.name)); return r === true;
      case 'openUrl': return A.openUrl(await fill(p.url), p.appPackage || null);
      case 'youtube': return A.youtube(await fill(p.query));
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
      case 'wait': await sleep(Math.min(600, Math.max(0, +(await fill(p.seconds)) || 0)) * 1000); return true;
      case 'copy': return A.copy(await fill(p.text));
      case 'share': return A.share(await fill(p.text));
      case 'ask':
        var key = 'ask:' + norm(p['var']);
        if (p.remember === 'yes') {
          var saved = await A.db.get(key);
          if (saved !== null && saved !== undefined && saved !== '') { setVar(p['var'], saved); return true; }
        }
        var answer = await ask(await fill(p.question), p.type, await fill(p['default'] || ''));
        if (answer === null) return 'stop';
        setVar(p['var'], answer);
        if (p.remember === 'yes') await A.db.set(key, answer);
        return true;
      case 'menu':
        var options = String(await fill(p.options || '')).split('\n').map(function (o) { return o.trim(); }).filter(Boolean);
        var choice = await menu(await fill(p.question), options);
        if (choice === null) return 'stop';
        setVar(p['var'], choice);
        return true;
      case 'getUrl':
        var text = await A.http(await fill(p.url));
        var out = text;
        if (String(p.path || '').trim()) { var v = pick(JSON.parse(text), await fill(p.path)); out = (v !== null && typeof v === 'object') ? JSON.stringify(v) : v; }
        setVar(p['var'], out == null ? '' : out);
        return true;
      case 'setVar': setVar(p['var'], await fill(p.value)); return true;
      case 'calc':
        var res = arithmetic(String(await fill(p.expr)));
        if (res === null) return false;
        setVar(p['var'], Math.round(res * 100) / 100);
        return true;
      case 'show': await show(await fill(p.text)); return true;
      case 'stop': return 'stop';
    }
    return false;
  }

  async function main() {
    if (!A) { status.textContent = T.outside; return; }
    if (!steps.length) { status.textContent = T.empty; return; }
    // While it's being created or shared, show the steps without doing them.
    if (A.trigger === 'preview') { status.textContent = T.preview; return; }
    status.textContent = T.running;
    var failed = false, loops = {}, guard = 0, i = 0;
    while (i < steps.length && guard++ < 5000) {
      var s = steps[i], p = s.params || {};
      if (s.action === 'if') {
        var yes = compare(await fill(p.left), p.op, await fill(p.right));
        items[i].className = 'block ok';
        if (!yes) { i = match[i] !== undefined ? match[i] + 1 : steps.length; continue; }
        i++; continue;
      }
      // Reached by running the "if" part: skip the "otherwise" part.
      if (s.action === 'else') { i = endOf(i); continue; }
      if (s.action === 'endif') { i++; continue; }
      if (s.action === 'repeat') {
        var times = Math.min(100, Math.max(0, parseInt(await fill(p.times), 10) || 0));
        loops[i] = (loops[i] || 0) + 1;
        if (loops[i] > times) { loops[i] = 0; i = match[i] !== undefined ? match[i] + 1 : steps.length; continue; }
        vars['repeticion'] = vars['repeat'] = String(loops[i]);
        i++; continue;
      }
      if (s.action === 'endrepeat') { i = match[i] !== undefined ? match[i] : i + 1; continue; }
      items[i].className = 'run';
      var r;
      try { r = await run(s); } catch (e) { r = false; }
      if (r === 'stop') { items[i].className = 'ok'; status.textContent = T.stopped; await sleep(400); A.done(); return; }
      items[i].className = r === false ? 'bad' : 'ok';
      items[i].firstChild.textContent = r === false ? '⚠️' : '✓';
      if (r === false) failed = true;
      i++;
      await sleep(120);
    }
    if (failed) {
      status.textContent = T.failed;
      closeBtn.textContent = T.close; closeBtn.hidden = false;
      closeBtn.onclick = function () { A.done(); };
    } else {
      status.textContent = T.done;
      await sleep(450);
      A.done(document.querySelector('h1').textContent);
    }
  }
  // The step after the "endif" that closes the "else" at [i] (nested ifs included).
  function endOf(i) {
    var nested = 0;
    for (var j = i + 1; j < steps.length; j++) {
      if (steps[j].action === 'if') nested++;
      else if (steps[j].action === 'endif') { if (nested === 0) return j + 1; nested--; }
    }
    return steps.length;
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
