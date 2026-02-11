import os
import shutil
import sys
from core.utils import is_server_active

# Plugin Configuration
config = {"label": "Uninstaller Functionality", "icon": "🧹"}

# Core system files that should not be deleted
PROTECTED = {
    "agent_mode.py",
    "app_creator.py",
    "auto_envolve.py",
    "projectmanager.py",
    "deleteApps",
    "import_export",
    "settings_manager",
    "time_machine",
    "uninstaller_plugins",
    "__init__.py",
    "help"
}

def _get_plugins():
    """Scans the plugins directory and identifies folders, files, and protected items."""
    plugins_dir = "plugins"
    os.makedirs(plugins_dir, exist_ok=True)
    items = []
    
    for name in sorted(os.listdir(plugins_dir)):
        # Skip hidden files, init, and cache
        if name.startswith(".") or name == "__pycache__":
            continue
            
        path = os.path.join(plugins_dir, name)
        is_dir = os.path.isdir(path)
        protected = name in PROTECTED
        
        items.append({
            "name": name, 
            "path": path, 
            "is_dir": is_dir, 
            "protected": protected
        })
    return items

def run():
    """Main loop for the Plugin Uninstaller."""
    while True:
        os.system("clear")
        print("=== 🧹 UNINSTALLER UTILITY ===\n")
        items = _get_plugins()
        
        if not items:
            print("🚫 No plugins found in the 'plugins/' directory.")
            print("0) Back")
            choice = input("\nSelection: ")
            if choice == "0": break
            continue

        print("Currently installed plugins:\n")
        for i, it in enumerate(items, 1):
            tag = "[FOLDER]" if it["is_dir"] else "[FILE]  "
            prot = " (PROTECTED)" if it["protected"] else ""
            print(f"{i}) {tag} {it['name']}{prot}")
        
        refresh_idx = len(items) + 1
        print(f"{refresh_idx}) 🔄 Refresh List")
        print("0) 🔙 Back\n")

        choice = input("Select the plugin number to uninstall (or 0): ").strip()
        
        if choice == "0":
            break # Exit to main menu
            
        if not choice.isdigit():
            continue
            
        idx = int(choice)

        # Refresh Logic
        if idx == refresh_idx:
            continue

        # Uninstall Logic
        real_idx = idx - 1
        if 0 <= real_idx < len(items):
            target = items[real_idx]
            name = target["name"]
            path = target["path"]

            # Protection Check
            if target["protected"]:
                print(f"\n⚠️ The plugin '{name}' is a core system component and cannot be removed.")
                input("\nPress Enter to continue...")
                continue

            # User Confirmation
            confirm = input(f"\nUninstall '{name}'? This action is permanent. (y/n): ").strip().lower()
            if confirm != "y":
                print("\n🔙 Operation cancelled.")
                input("\nPress Enter to continue...")
                continue

            try:
                # Path Traversal Security: Ensure we are only deleting inside /plugins
                base_abs = os.path.abspath("plugins")
                target_abs = os.path.abspath(path)
                
                if not target_abs.startswith(base_abs):
                    raise Exception("Target path is outside the plugins directory.")

                if target["is_dir"]:
                    shutil.rmtree(path)
                else:
                    os.remove(path)
                    
                print(f"\n✅ Plugin '{name}' successfully uninstalled.")
            except Exception as e:
                print(f"\n❌ Uninstallation error: {e}")

            input("\nPress Enter to continue...")