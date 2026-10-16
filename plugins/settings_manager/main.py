import getpass
import os
import sys

sys.path.insert(0, os.getcwd())
from core import ai

# Plugin Configuration
config = {"label": "Global Settings Hub", "icon": "⚙️"}
PROVIDER_IDS = list(ai.PROVIDERS)
PAGE_SIZE = 30


def pause():
    input("\nPress Enter to continue...")


def choose_provider(settings, title):
    """Numbered provider list. Returns a provider id or None."""
    print(f"\n--- {title} ---")
    for i, p in enumerate(PROVIDER_IDS, 1):
        mark = "✅" if ai.is_configured(settings, p) else "  "
        active = "  ← active" if p == settings["active_provider"] else ""
        print(f"{i}) {mark} {ai.PROVIDERS[p]['label']}{active}")
    print("0) Cancel")
    choice = input("\nProvider number: ").strip()
    if choice.isdigit() and 1 <= int(choice) <= len(PROVIDER_IDS):
        return PROVIDER_IDS[int(choice) - 1]
    return None


def choose_base_url(settings):
    """For the OpenAI-compatible provider: a preset or any URL ending in /v1."""
    current = settings["base_urls"].get("custom", "")
    print("\n--- Base URL (OpenAI-compatible API) ---")
    for i, (name, url) in enumerate(ai.CUSTOM_PRESETS, 1):
        print(f"{i}) {name}: {url}")
    print(f"\nCurrent: {current or 'not set'}")
    choice = input("Number, your own URL, or Enter to keep it: ").strip()
    if choice.isdigit() and 1 <= int(choice) <= len(ai.CUSTOM_PRESETS):
        settings["base_urls"]["custom"] = ai.CUSTOM_PRESETS[int(choice) - 1][1]
    elif choice.startswith("http"):
        settings["base_urls"]["custom"] = choice.rstrip("/")


def choose_model(settings, provider):
    """Lists the models this key can use and lets the user pick or filter. Returns the model or None."""
    current = settings["models"].get(provider, "")
    print(f"\n🔎 Fetching the models available to your {ai.PROVIDERS[provider]['label']} account...")
    try:
        models = ai.list_models(settings, provider)
    except ai.AIError as e:
        print(f"⚠️ Couldn't list models: {e}")
        models = []

    shown = models
    while True:
        if shown:
            print(f"\n--- MODELS ({len(shown)}) ---")
            for i, m in enumerate(shown[:PAGE_SIZE], 1):
                print(f"{i:>3}) {m}{'  ← current' if m == current else ''}")
            if len(shown) > PAGE_SIZE:
                print(f"     … {len(shown) - PAGE_SIZE} more. Type part of a name to filter.")
        print(f"\nCurrent model: {current or 'not set'}")
        print("Type a number, part of a name to filter (e.g. 'luna'), a full model name, or Enter to keep it.")
        choice = input("👉 Model: ").strip()
        if not choice:
            return None
        if choice.isdigit() and shown and 1 <= int(choice) <= min(len(shown), PAGE_SIZE):
            return shown[int(choice) - 1]
        if choice in models or not models:
            return choice
        matches = [m for m in models if choice.lower() in m.lower()]
        if len(matches) == 1:
            return matches[0]
        if matches:
            shown = matches
            continue
        if input(f"'{choice}' is not in the list. Use it anyway? (y/n): ").strip().lower() == "y":
            return choice


def run_test(settings, provider=None):
    provider = provider or settings["active_provider"]
    print(f"\n📡 Testing {ai.PROVIDERS[provider]['label']} · {settings['models'].get(provider)}...")
    ok, message = ai.test_connection(settings, provider)
    print(f"{'✅ Connected' if ok else '❌ Failed'}: {message}")
    return ok


def guided_setup(settings):
    provider = choose_provider(settings, "STEP 1: Choose provider")
    if not provider:
        return
    label = ai.PROVIDERS[provider]["label"]

    if provider == "custom":
        choose_base_url(settings)
        if not settings["base_urls"].get("custom"):
            print("❌ A base URL is required for this provider.")
            return

    print(f"\n--- STEP 2: API key for {label} ---")
    current_key = settings["api_keys"].get(provider, "")
    optional = " (optional for local servers)" if provider == "custom" else ""
    print(f"Current: {ai.mask_key(current_key)}. The key stays hidden while you paste it.")
    new_key = getpass.getpass(f"Paste your key{optional} and press Enter (Enter alone keeps it): ").strip()
    if new_key:
        settings["api_keys"][provider] = new_key
        print(f"🔑 Saved key: {ai.mask_key(new_key)}")

    print(f"\n--- STEP 3: Model ---")
    model = choose_model(settings, provider)
    if model:
        settings["models"][provider] = model

    settings["active_provider"] = provider
    ai.save_settings(settings)
    print(f"\n✅ {label} · {settings['models'][provider]} saved and activated.")
    run_test(settings, provider)


def preferences(settings):
    print("\n--- 🛠️ SYSTEM PREFERENCES ---")
    backup_pref = settings["preferences"].get("backup_before_evolve", True)
    print(f"1) Backup before Evolution: {'YES' if backup_pref else 'NO'}")
    print("0) Back")
    if input("\nSelect preference number to toggle: ").strip() == "1":
        settings["preferences"]["backup_before_evolve"] = not backup_pref
        ai.save_settings(settings)
        print("✅ Preference updated.")


def run():
    """Main loop for the Settings Hub."""
    while True:
        os.system("clear")
        settings = ai.load_settings()
        active = settings["active_provider"]

        print("=== ⚙️ GLOBAL SETTINGS HUB ===")
        print(f"PROVIDER: {ai.PROVIDERS[active]['label']}")
        print(f"MODEL:    {settings['models'].get(active) or 'not set'}")
        if active == "custom":
            print(f"URL:      {settings['base_urls'].get('custom') or 'not set'}")
        print(f"API KEY:  {ai.mask_key(settings['api_keys'].get(active, ''))}")
        if not ai.is_configured(settings):
            print("\n⚠️ Not configured yet: start with option 1.")
        print("-" * 35)
        print("1) 🚀 Guided Configuration (Provider -> Key -> Model)")
        print("2) 🧩 Change Model")
        print("3) 🧠 Change Active Provider")
        print("4) 📡 Test Connection")
        print("5) 🛠️ System Preferences")
        print("\n0) 🔙 Back")

        option = input("\nSelect an option: ").strip()

        if option == "0":
            break
        if option == "1":
            guided_setup(settings)
            pause()
        elif option == "2":
            model = choose_model(settings, active)
            if model:
                settings["models"][active] = model
                ai.save_settings(settings)
                print(f"✅ Model set to {model}.")
                run_test(settings)
            pause()
        elif option == "3":
            provider = choose_provider(settings, "🧠 SELECT ACTIVE AI")
            if provider:
                settings["active_provider"] = provider
                ai.save_settings(settings)
                print(f"✅ Active provider: {ai.PROVIDERS[provider]['label']}")
                if not ai.is_configured(settings, provider):
                    print("⚠️ It has no key or model yet: use option 1 to configure it.")
            pause()
        elif option == "4":
            run_test(settings)
            pause()
        elif option == "5":
            preferences(settings)
            pause()
