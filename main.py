import os
import importlib.util
import sys
import threading

def load_plugins():
    """
    Scans the 'plugins' directory and dynamically loads modules 
    containing a 'config' dict and a 'run' function.
    """
    plugin_dir = "plugins"
    if not os.path.exists(plugin_dir):
        os.makedirs(plugin_dir)

    loaded_plugins = []
    
    for item in os.listdir(plugin_dir):
        # Skip hidden files, __init__, and cache
        if item == "__init__.py" or item.startswith(".") or item == "__pycache__":
            continue
            
        item_path = os.path.join(plugin_dir, item)
        module_name = ""
        entry_point = ""

        # Check for single-file plugins (.py)
        if item.endswith(".py"):
            module_name = item[:-3]
            entry_point = item_path
        # Check for folder-based plugins (folder/main.py)
        elif os.path.isdir(item_path):
            main_file = os.path.join(item_path, "main.py")
            if os.path.exists(main_file):
                module_name = item 
                entry_point = main_file
            else:
                continue

        if entry_point:
            try:
                spec = importlib.util.spec_from_file_location(module_name, entry_point)
                module = importlib.util.module_from_spec(spec)
                spec.loader.exec_module(module)
                
                # Only load if valid plugin structure is present
                if hasattr(module, 'config') and hasattr(module, 'run'):
                    loaded_plugins.append(module)
            except Exception as e:
                print(f"⚠️ Error loading {item}: {e}")

    # Default alphabetical sort by label for the 'Others' category
    return sorted(loaded_plugins, key=lambda x: x.config.get("label", "").lower())

def ai_status():
    """One line with the active AI, or a hint to configure one."""
    try:
        from core import ai
        settings = ai.load_settings()
        provider = settings["active_provider"]
        if not ai.is_configured(settings):
            return " 🤖 AI: not configured → open ⚙️ Global Settings Hub"
        return f" 🤖 AI: {ai.PROVIDERS[provider]['label']} · {settings['models'][provider]}"
    except Exception as e:
        return f" 🤖 AI: settings error ({e})"

UPDATE_CHECK = {"available": False}

def start_update_check(plugins):
    """Checks GitHub for a new version in the background, once per session (silent when offline)."""
    updater = next((p for p in plugins if p.config.get("label") == "Update acornix"), None)
    if updater is None:
        return None
    def check():
        UPDATE_CHECK["available"] = updater.update_available()
    thread = threading.Thread(target=check, daemon=True)
    thread.start()
    return thread

def main_menu():
    """
    Displays the categorized main menu and handles user selection.
    """
    # Categorization mapping: names must match the 'label' in each plugin's config
    CATEGORIES = {
        "🚀 CREATION & AI": [
            "App Creator & Editor",
            "Auto-Evolve (create functionality)",
            "Agent Mode (Total control)"
        ],
        "🗂️ APPs MANAGEMENT": [
            "APPs Manager",
            "Visual Launcher",
            "Delete Apps",
            "Smart Import-Export Hub"
        ],
        "🧠 SYSTEM & SECURITY": [            
            "Time Machine (Restore)",
            "Uninstaller Functionality"            
        ],
        "⚙️ SETTINGS": [
            "Global Settings Hub",                      
            "System Health",
            "Update acornix"
        ],
        "ℹ️ HELP": [
            "Help"
        ]
    }

    check_thread = start_update_check(load_plugins())
    if check_thread:
        check_thread.join(timeout=2)  # Usually enough to show the notice on the first screen.

    while True:
        os.system('clear')
        plugins = load_plugins()
        
        print("==========================================")
        print("      🚀 ACORNIX          ")
        print("==========================================")
        print(ai_status())
        if UPDATE_CHECK["available"]:
            print(" 🆕 Update available → 🔄 Update acornix (in ⚙️ SETTINGS)")
        
        mapping = {}
        current_idx = 1
        displayed_labels = set()

        # 1. Display Categorized Plugins (in the order defined above)
        for cat_name, desired_order in CATEGORIES.items():
            plugins_in_cat = []
            
            for label_name in desired_order:
                # Find the loaded plugin matching the category label
                found = next((p for p in plugins if p.config.get('label') == label_name), None)
                if found:
                    plugins_in_cat.append(found)

            if plugins_in_cat:
                print(f"\n {cat_name}")
                for p in plugins_in_cat:
                    icon = p.config.get('icon', '🧩')
                    label = p.config.get('label', 'Unknown')
                    print(f"   {current_idx}) {icon} {label}")
                    
                    mapping[current_idx] = p
                    displayed_labels.add(label)
                    current_idx += 1

        # 2. Display Uncategorized / New Plugins
        others = [p for p in plugins if p.config.get('label') not in displayed_labels]
        
        if others:
            print(f"\n 📂 OTHERS / UTILITIES")
            for p in others:
                icon = p.config.get('icon', '🧩')
                label = p.config.get('label', 'Unknown')
                print(f"   {current_idx}) {icon} {label}")
                mapping[current_idx] = p
                current_idx += 1

        print("\n------------------------------------------")
        print(" 0) ❌ EXIT")
        print("------------------------------------------")
        
        choice = input(f"\nSelect an option (1-{current_idx-1} or 0): ").strip()
        
        if choice == "0":
            print("\nGoodbye, creator! Shutdown complete.")
            break
            
        if choice.isdigit():
            idx = int(choice)
            if idx in mapping:
                try:
                    # Run the selected plugin's main function
                    mapping[idx].run()
                except Exception as e:
                    print(f"\n❌ Critical Error running plugin: {e}")
                    input("\nPress Enter to return to main menu...")
            else:
                print(f"\n⚠️ Invalid selection. Please choose a number between 0 and {current_idx-1}.")
                import time
                time.sleep(1.5)

if __name__ == "__main__":
    main_menu()