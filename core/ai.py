"""
AI providers for acornix: OpenAI, Anthropic (Claude), Google Gemini and any
OpenAI-compatible endpoint (OpenRouter, Groq, DeepSeek, Ollama, LM Studio...).

Settings live in config.json:
{
    "active_provider": "openai",
    "api_keys":  {"openai": "...", "anthropic": "", "gemini": "", "custom": ""},
    "models":    {"openai": "gpt-6-luna", ...},
    "base_urls": {"custom": "https://openrouter.ai/api/v1"},
    "preferences": {"backup_before_evolve": true}
}
"""
import json
import os
import re
import sys
import threading
import time

import requests

CONFIG_FILE = "config.json"

PROVIDERS = {
    "openai": {"label": "OpenAI (GPT)", "default_model": "gpt-6-luna",
               "base_url": "https://api.openai.com/v1"},
    "anthropic": {"label": "Anthropic (Claude)", "default_model": "claude-opus-5-5",
                  "base_url": "https://api.anthropic.com/v1"},
    "gemini": {"label": "Google Gemini", "default_model": "gemini-2.5-flash",
               "base_url": "https://generativelanguage.googleapis.com/v1beta"},
    "custom": {"label": "OpenAI-compatible (OpenRouter, Groq, Ollama...)", "default_model": "",
               "base_url": ""},
}

# Ready-made base URLs offered when configuring the OpenAI-compatible provider.
CUSTOM_PRESETS = [
    ("OpenRouter", "https://openrouter.ai/api/v1"),
    ("Groq", "https://api.groq.com/openai/v1"),
    ("DeepSeek", "https://api.deepseek.com/v1"),
    ("Mistral", "https://api.mistral.ai/v1"),
    ("Ollama (local)", "http://localhost:11434/v1"),
    ("LM Studio (local)", "http://localhost:1234/v1"),
]

ANTHROPIC_VERSION = "2023-06-01"
ANTHROPIC_MAX_TOKENS = 32000
CONNECT_TIMEOUT = 20
# Reasoning models can think for minutes before the first token; this is the max silence between chunks.
READ_TIMEOUT = 300

# OpenAI lists every model it has (audio, images, embeddings...). Only chat models can write code.
_NON_CHAT = ("embedding", "tts", "whisper", "dall-e", "image", "audio", "realtime", "transcribe",
             "moderation", "search", "davinci", "babbage", "sora", "live", "instruct", "translate",
             "deep-research", "codex", "-pro")


# How every code-generating prompt must answer (process_and_execute splits on these markers).
OUTPUT_FORMAT = """OUTPUT FORMAT (strict):
---CODIGO---
<the complete code, nothing else>
---SUGERENCIA---
<one short sentence in English proposing the next new feature or improvement for this app>

Do not add explanations, notes or markdown outside these two sections."""


class AIError(Exception):
    """A readable error from a provider (bad key, unknown model, no credit...)."""


# --- Settings -----------------------------------------------------------------

def default_settings():
    return {
        "active_provider": "openai",
        "api_keys": {p: "" for p in PROVIDERS},
        "models": {p: info["default_model"] for p, info in PROVIDERS.items()},
        "base_urls": {"custom": ""},
        "preferences": {"backup_before_evolve": True},
    }


def normalize(settings):
    """Fills in anything missing, so configs from older acornix versions keep working."""
    base = default_settings()
    settings = settings if isinstance(settings, dict) else {}
    for key in ("api_keys", "models", "base_urls", "preferences"):
        merged = dict(base[key])
        merged.update(settings.get(key) or {})
        settings[key] = merged
    for p in PROVIDERS:
        if not settings["models"].get(p):
            settings["models"][p] = PROVIDERS[p]["default_model"]
    if settings.get("active_provider") not in PROVIDERS:
        settings["active_provider"] = "openai"
    return settings


def load_settings():
    if not os.path.exists(CONFIG_FILE):
        return default_settings()
    try:
        with open(CONFIG_FILE, "r", encoding="utf-8") as f:
            return normalize(json.load(f))
    except Exception as e:
        print(f"⚠️ Error reading {CONFIG_FILE}: {e}")
        return default_settings()


def save_settings(settings):
    with open(CONFIG_FILE, "w", encoding="utf-8") as f:
        json.dump(settings, f, indent=4)
    try:
        os.chmod(CONFIG_FILE, 0o600)  # It holds API keys.
    except OSError:
        pass


def is_configured(settings, provider=None):
    provider = provider or settings.get("active_provider")
    key = settings["api_keys"].get(provider, "")
    model = settings["models"].get(provider, "")
    if provider == "custom":
        # Local servers such as Ollama don't need a key.
        return bool(settings["base_urls"].get("custom")) and bool(model)
    return bool(key) and bool(model)


def mask_key(key):
    if not key:
        return "not set"
    return key[:7] + "…" + key[-4:] if len(key) > 14 else "••••"


def base_url(settings, provider):
    return (settings["base_urls"].get(provider) or PROVIDERS[provider]["base_url"]).rstrip("/")


# --- HTTP helpers -------------------------------------------------------------

def _error_message(res):
    """Pulls the human message out of an error response from any provider."""
    try:
        body = res.json()
        err = body[0].get("error", body[0]) if isinstance(body, list) and body else body.get("error", body)
        msg = err.get("message") if isinstance(err, dict) else str(err)
    except Exception:
        msg = res.text[:300]
    hint = {
        401: "Check your API key.",
        403: "This key has no access to that model or service.",
        404: "Unknown model or wrong URL. Pick another model in Global Settings Hub.",
        429: "Rate limit or no credit left on this account.",
    }.get(res.status_code, "")
    return f"HTTP {res.status_code}: {msg}" + (f"\n   💡 {hint}" if hint else "")


def _request(method, url, headers, body=None, stream=False):
    try:
        res = requests.request(method, url, headers=headers, json=body, stream=stream,
                               timeout=(CONNECT_TIMEOUT, READ_TIMEOUT))
    except requests.exceptions.ConnectionError:
        raise AIError(f"Can't connect to {url.split('/')[2]}. Check your internet connection or the URL.")
    except requests.exceptions.Timeout:
        raise AIError("The AI took too long to answer. Try again or pick a faster model.")
    if res.status_code >= 400:
        raise AIError(_error_message(res))
    return res


def _sse(res):
    """Yields the JSON payload of each server-sent event."""
    res.encoding = "utf-8"
    for line in res.iter_lines(decode_unicode=True):
        if not line or not line.startswith("data:"):
            continue
        data = line[5:].strip()
        if data == "[DONE]":
            return
        try:
            yield json.loads(data)
        except ValueError:
            continue


def _openai_headers(key):
    headers = {"Content-Type": "application/json"}
    if key:
        headers["Authorization"] = f"Bearer {key}"
    return headers


def _anthropic_headers(key):
    return {"x-api-key": key, "anthropic-version": ANTHROPIC_VERSION, "content-type": "application/json"}


def _gemini_headers(key):
    return {"x-goog-api-key": key, "Content-Type": "application/json"}


def _model_path(model):
    return model if model.startswith("models/") else f"models/{model}"


# --- Streaming per provider ---------------------------------------------------

def _stream_openai(url, key, model, system, prompt):
    body = {"model": model, "stream": True,
            "messages": [{"role": "system", "content": system}, {"role": "user", "content": prompt}]}
    res = _request("POST", f"{url}/chat/completions", _openai_headers(key), body, stream=True)
    for chunk in _sse(res):
        if "error" in chunk:
            raise AIError(f"API error: {chunk['error'].get('message', chunk['error'])}")
        for choice in chunk.get("choices") or []:
            text = (choice.get("delta") or {}).get("content")
            if text:
                yield text
            if choice.get("finish_reason") == "length":
                yield _TRUNCATED


def _stream_anthropic(url, key, model, system, prompt):
    body = {"model": model, "max_tokens": ANTHROPIC_MAX_TOKENS, "stream": True, "system": system,
            "messages": [{"role": "user", "content": prompt}]}
    res = _request("POST", f"{url}/messages", _anthropic_headers(key), body, stream=True)
    for event in _sse(res):
        kind = event.get("type")
        if kind == "error":
            raise AIError(f"API error: {event.get('error', {}).get('message', event)}")
        if kind == "content_block_delta" and event["delta"].get("type") == "text_delta":
            yield event["delta"]["text"]
        elif kind == "message_delta":
            stop = event.get("delta", {}).get("stop_reason")
            if stop == "max_tokens":
                yield _TRUNCATED
            elif stop == "refusal":
                raise AIError("The model declined this request. Try rephrasing it.")


def _stream_gemini(url, key, model, system, prompt):
    body = {"systemInstruction": {"parts": [{"text": system}]},
            "contents": [{"role": "user", "parts": [{"text": prompt}]}]}
    res = _request("POST", f"{url}/{_model_path(model)}:streamGenerateContent?alt=sse",
                   _gemini_headers(key), body, stream=True)
    for chunk in _sse(res):
        if "error" in chunk:
            raise AIError(f"API error: {chunk['error'].get('message', chunk['error'])}")
        for cand in chunk.get("candidates") or []:
            for part in (cand.get("content") or {}).get("parts") or []:
                if part.get("text") and not part.get("thought"):
                    yield part["text"]
            if cand.get("finishReason") == "MAX_TOKENS":
                yield _TRUNCATED


_TRUNCATED = object()
_STREAMERS = {"openai": _stream_openai, "custom": _stream_openai,
              "anthropic": _stream_anthropic, "gemini": _stream_gemini}


def generate(prompt, system_prompt, settings=None, provider=None, model=None, on_text=None):
    """Streams an answer from the configured provider and returns the full text.

    Raises AIError with a readable message when something goes wrong.
    """
    settings = normalize(settings) if settings is not None else load_settings()
    provider = provider or settings["active_provider"]
    model = model or settings["models"].get(provider, "")
    ready = settings["base_urls"].get("custom") if provider == "custom" else settings["api_keys"].get(provider)
    if not ready or not model:
        what = "base URL" if provider == "custom" else "API key"
        raise AIError(f"{PROVIDERS[provider]['label']} has no {what} or model yet. "
                      "Add it in ⚙️ Global Settings Hub (option 1).")
    key = settings["api_keys"].get(provider, "")
    url = base_url(settings, provider)
    out, truncated = [], False
    for piece in _STREAMERS[provider](url, key, model, system_prompt, prompt):
        if piece is _TRUNCATED:
            truncated = True
            continue
        out.append(piece)
        if on_text:
            on_text(piece)
    text = "".join(out)
    if not text.strip():
        raise AIError("The model returned an empty answer. Try again or pick another model.")
    if truncated:
        print("\n⚠️ The answer hit the model's length limit and may be cut off.")
    return text


def list_models(settings, provider):
    """Ids of the models this key can use, newest first (only chat models for OpenAI)."""
    key = settings["api_keys"].get(provider, "")
    url = base_url(settings, provider)
    if provider == "anthropic":
        ids, after = [], None
        while True:
            q = f"?limit=100" + (f"&after_id={after}" if after else "")
            body = _request("GET", f"{url}/models{q}", _anthropic_headers(key)).json()
            ids += [m["id"] for m in body.get("data", [])]
            if not body.get("has_more"):
                break
            after = body.get("last_id")
        return _newest_first(ids)
    if provider == "gemini":
        body = _request("GET", f"{url}/models?pageSize=200", _gemini_headers(key)).json()
        return _newest_first(m["name"].removeprefix("models/") for m in body.get("models", [])
                             if "generateContent" in m.get("supportedGenerationMethods", []))
    body = _request("GET", f"{url}/models", _openai_headers(key)).json()
    ids = [m["id"] for m in body.get("data", []) if m.get("id")]
    if provider == "openai":
        # Skip non-chat models and dated snapshots (gpt-4o-2024-08-06): the plain alias is enough.
        ids = [i for i in ids if not any(word in i for word in _NON_CHAT) and not _SNAPSHOT.search(i)]
        return sorted(_newest_first(ids), key=lambda i: not i.startswith("gpt-"))  # GPT family first
    return _newest_first(ids)


_SNAPSHOT = re.compile(r"-(\d{4}-\d{2}-\d{2}|\d{4})$")


def _newest_first(ids):
    """Reverse natural order, so gpt-6.1 comes before gpt-6 and gpt-5.10 before gpt-5.9."""
    key = lambda s: [int(t) if t.isdigit() else t for t in re.split(r"(\d+)", s)]
    return sorted(set(ids), key=key, reverse=True)


def test_connection(settings, provider=None):
    """Sends a tiny request. Returns (ok, message)."""
    provider = provider or settings["active_provider"]
    try:
        start = time.time()
        reply = generate("Reply with exactly: OK", "You are a connection test. Answer in one word.",
                         settings=settings, provider=provider)
        return True, f"{reply.strip()[:40]} ({time.time() - start:.1f}s)"
    except AIError as e:
        return False, str(e)


class Progress:
    """Live one-line status: '🧠 Thinking... 12s' until text arrives, then '✍️ Writing... 12.4k characters · 18s'."""

    def __init__(self, label="✍️ Writing"):
        self.label, self.chars, self.start, self.last = label, 0, time.time(), 0.0
        self._lock = threading.Lock()
        self._stop = threading.Event()
        threading.Thread(target=self._tick, daemon=True).start()

    def _write(self, line):
        with self._lock:
            sys.stdout.write(f"\r{line}   ")
            sys.stdout.flush()

    def _tick(self):
        while not self._stop.wait(1):
            if self.chars == 0:
                self._write(f"🧠 Thinking... {time.time() - self.start:.0f}s")

    def __call__(self, piece):
        self.chars += len(piece)
        now = time.time()
        if now - self.last > 0.25:
            self.last = now
            size = f"{self.chars / 1000:.1f}k" if self.chars >= 1000 else str(self.chars)
            self._write(f"{self.label}... {size} characters · {now - self.start:.0f}s")

    def done(self):
        if self._stop.is_set():
            return
        self._stop.set()
        if self.chars:
            self._write(f"✅ Done: {self.chars} characters in {time.time() - self.start:.0f}s     ")
            sys.stdout.write("\n")
        else:
            self._write(" " * 30)
            sys.stdout.write("\r")
        sys.stdout.flush()
