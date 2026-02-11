from core.utils import is_server_active
import os
import shutil
import platform
import time

config = {"label": "System Health", "icon": "🔋"}

def _human_bytes(n):
    """Convert bytes to human-readable string."""
    for unit in ['B', 'KB', 'MB', 'GB', 'TB']:
        if n < 1024.0:
            return f"{n:3.1f} {unit}"
        n /= 1024.0
    return f"{n:.1f} PB"

def _get_battery_info():
    """Try multiple methods to obtain battery percentage and charging status."""
    # 1) Try psutil if available
    try:
        import psutil
        batt = psutil.sensors_battery()
        if batt is not None:
            percent = int(round(batt.percent))
            charging = bool(batt.power_plugged)
            status = "Charging" if charging else "Discharging"
            return f"{percent}% ({status})"
    except Exception:
        pass

    # 2) Try reading /sys/class/power_supply (Linux / Android)
    try:
        psp = "/sys/class/power_supply"
        if os.path.isdir(psp):
            for name in os.listdir(psp):
                d = os.path.join(psp, name)
                # Typical battery dirs: BAT0, battery, or similar
                if not os.path.isdir(d):
                    continue
                cap_file = None
                status_file = None
                # capacity variants
                for candidate in ("capacity", "charge_now", "charge_full", "energy_now", "energy_full"):
                    path = os.path.join(d, candidate)
                    if os.path.exists(path):
                        cap_file = path
                        break
                # status variants
                for candidate in ("status", "charging", "online"):
                    path = os.path.join(d, candidate)
                    if os.path.exists(path):
                        status_file = path
                        break
                percent = None
                stat = None
                if cap_file:
                    try:
                        with open(cap_file, "r") as f:
                            txt = f.read().strip()
                        # capacity is often a percent already, otherwise try to compute safely
                        if txt.isdigit():
                            percent = int(txt)
                        else:
                            # fallback: try parse float
                            try:
                                percent = int(float(txt))
                            except:
                                percent = None
                    except Exception:
                        percent = None
                if status_file:
                    try:
                        with open(status_file, "r") as f:
                            stat = f.read().strip()
                    except Exception:
                        stat = None
                if percent is not None:
                    status = "Charging" if (stat and "charg" in stat.lower()) else ("Online" if stat and stat.lower() in ("online","1") else "Discharging")
                    return f"{percent}% ({status})"
    except Exception:
        pass

    # 3) Unable to determine
    return "Unavailable"

def _get_free_ram():
    """Return free/available RAM in bytes and formatted string."""
    # Try psutil first
    try:
        import psutil
        vm = psutil.virtual_memory()
        avail = getattr(vm, "available", None) or getattr(vm, "free", None)
        if avail is not None:
            return avail, _human_bytes(avail)
    except Exception:
        pass

    # Fallback: read /proc/meminfo (Linux)
    try:
        with open("/proc/meminfo", "r") as f:
            data = f.read().splitlines()
        info = {}
        for line in data:
            parts = line.split(":")
            if len(parts) < 2:
                continue
            key = parts[0].strip()
            val = parts[1].strip().split()[0]
            try:
                info[key] = int(val)  # value in kB
            except:
                continue
        # Prefer MemAvailable then MemFree
        if "MemAvailable" in info:
            kb = info["MemAvailable"]
        elif "MemFree" in info:
            kb = info["MemFree"]
        else:
            kb = None
        if kb is not None:
            bytes_avail = kb * 1024
            return bytes_avail, _human_bytes(bytes_avail)
    except Exception:
        pass

    return None, "Unavailable"

def _get_free_storage(path="."):
    """Return free storage bytes and formatted string for the given path."""
    try:
        du = shutil.disk_usage(path)
        free = du.free
        return free, _human_bytes(free)
    except Exception:
        return None, "Unavailable"

def _print_table(rows, title=None):
    """
    Print a clean, professional table-like format.
    rows: list of (label, value) tuples
    """
    left_w = max(len(r[0]) for r in rows) + 2
    right_w = max(len(r[1]) for r in rows) + 2
    total_w = left_w + right_w + 3

    sep = "+" + "-" * (left_w) + "+" + "-" * (right_w) + "+"
    if title:
        print(title.center(total_w))
    print(sep)
    header = f"| {'METRIC'.ljust(left_w-2)}| {'VALUE'.ljust(right_w-2)}|"
    print(header)
    print(sep)
    for label, value in rows:
        print(f"| {label.ljust(left_w-2)}| {value.ljust(right_w-2)}|")
    print(sep)

def run():
    os.system("clear")
    print("==========================================")
    print("          SYSTEM HEALTH — STATUS          ")
    print("==========================================\n")
    # Gather values
    battery = _get_battery_info()
    ram_bytes, ram_text = _get_free_ram()
    storage_bytes, storage_text = _get_free_storage(os.getcwd())

    cwd = os.path.abspath(os.getcwd())

    rows = [
        ("Battery", battery),
        ("Free RAM", ram_text if ram_text else "Unavailable"),
        ("Free Storage", f"{storage_text} (in {cwd})" if storage_text != "Unavailable" else "Unavailable"),
        ("OS", platform.system() + " " + platform.release()),
        ("Python", platform.python_version())
    ]

    _print_table(rows, title="System Health Report")
    print("\nNotes:")
    print(" - Values are snapshots. On some systems (e.g., desktops without battery) battery may be unavailable.")
    print(" - For more accurate results install 'psutil' (pip install psutil).")

    input("\nPress Enter to return...")