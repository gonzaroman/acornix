"""
Updates acornix to the latest version of the `termux` branch on GitHub.

Safe for the user's own work:
- Apps and plugins the user created (files git doesn't track) are never deleted.
  If one has the same path as a new file in the update, the user's version wins.
- config.json (provider, keys, models) and backups/ are ignored by git and never touched.
- The bundled code and sample apps are updated, even if the user changed them;
  a ZIP with the previous my_apps/, plugins/ and config.json is saved first in backups/updates/.
"""
import datetime
import os
import shutil
import subprocess
import sys
import tempfile
import zipfile

config = {"label": "Update acornix", "icon": "🔄"}

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
REMOTE = "origin"
BRANCH = "termux"
TARGET = f"{REMOTE}/{BRANCH}"
BACKUP_DIR = os.path.join("backups", "updates")


class UpdateError(Exception):
    pass


def git(*args, timeout=60, check=True):
    """Runs git in the acornix folder and returns its output."""
    env = dict(os.environ, GIT_TERMINAL_PROMPT="0", LC_ALL="C")
    try:
        res = subprocess.run(["git", *args], cwd=ROOT, env=env, capture_output=True, text=True, timeout=timeout)
    except FileNotFoundError:
        raise UpdateError("git is not installed. Install it with: pkg install git")
    except subprocess.TimeoutExpired:
        raise UpdateError("git took too long. Check your internet connection and try again.")
    if check and res.returncode != 0:
        raise UpdateError((res.stderr or res.stdout).strip() or f"git {args[0]} failed")
    return res.stdout


def fetch(timeout=60):
    """Downloads the latest termux branch (works even for clones that only had main)."""
    if not os.path.isdir(os.path.join(ROOT, ".git")):
        raise UpdateError("This acornix folder was not installed with git, so it can't update itself.\n"
                          "   Reinstall it with the command in the README (your apps: copy my_apps/ first).")
    git("fetch", "-q", REMOTE, f"+refs/heads/{BRANCH}:refs/remotes/{TARGET}", timeout=timeout)


def pending_commits():
    """Commits in the latest version that this copy doesn't have yet."""
    out = git("log", "--format=%h %s", f"HEAD..{TARGET}")
    return [line for line in out.splitlines() if line.strip()]


def update_available(timeout=8):
    """Quick background check for the main menu. Never raises."""
    try:
        fetch(timeout=timeout)
        return len(pending_commits()) > 0
    except Exception:
        return False


def _split_z(out):
    return [p for p in out.split("\0") if p]


def changed_bundled_files():
    """Files from the repo that the user changed or deleted: they will be replaced by the new version."""
    return _split_z(git("diff", "--name-only", "-z", "HEAD"))


USER_FOLDERS = ("my_apps/", "plugins/")


def colliding_user_files():
    """Apps/plugins the user created that the update would overwrite (same path as a new bundled file)."""
    untracked = set(_split_z(git("ls-files", "--others", "-z")))
    incoming = set(_split_z(git("diff", "--name-only", "-z", "HEAD", TARGET)))
    return sorted(p for p in untracked & incoming if p.startswith(USER_FOLDERS))


def backup():
    """ZIP with the user's apps, plugins and settings, saved before touching anything."""
    os.makedirs(os.path.join(ROOT, BACKUP_DIR), exist_ok=True)
    stamp = datetime.datetime.now().strftime("%Y%m%d_%H%M%S")
    path = os.path.join(BACKUP_DIR, f"pre_update_{stamp}.zip")
    with zipfile.ZipFile(os.path.join(ROOT, path), "w", zipfile.ZIP_DEFLATED) as z:
        for top in ("my_apps", "plugins"):
            for folder, dirs, files in os.walk(os.path.join(ROOT, top)):
                dirs[:] = [d for d in dirs if d != "__pycache__"]
                for name in files:
                    full = os.path.join(folder, name)
                    z.write(full, os.path.relpath(full, ROOT))
        if os.path.exists(os.path.join(ROOT, "config.json")):
            z.write(os.path.join(ROOT, "config.json"), "config.json")
    return path


def apply_update():
    """Moves this copy to the latest version, keeping the user's own files. Returns the new commit."""
    keep = colliding_user_files()
    with tempfile.TemporaryDirectory() as tmp:
        for rel in keep:
            dest = os.path.join(tmp, rel)
            os.makedirs(os.path.dirname(dest), exist_ok=True)
            shutil.copy2(os.path.join(ROOT, rel), dest)

        # reset --hard never deletes untracked files; never run `git clean` here.
        git("checkout", "-q", "-f", "-B", BRANCH, TARGET)
        git("reset", "-q", "--hard", TARGET)

        for rel in keep:
            dest = os.path.join(ROOT, rel)
            os.makedirs(os.path.dirname(dest), exist_ok=True)
            shutil.copy2(os.path.join(tmp, rel), dest)
    return git("log", "-1", "--format=%h %s").strip()


def restart():
    print("\n🔄 Restarting acornix...")
    os.chdir(ROOT)
    os.execv(sys.executable, [sys.executable, os.path.join(ROOT, "main.py")])


def run(ask=input, do_restart=True):
    os.system("clear")
    print("=== 🔄 UPDATE ACORNIX ===")
    print("\n📡 Looking for a new version...")
    try:
        fetch()
        commits = pending_commits()
    except UpdateError as e:
        print(f"❌ {e}")
        ask("\nPress Enter to return...")
        return False

    if not commits:
        print(f"✅ You already have the latest version ({git('log', '-1', '--format=%h %cs').strip()}).")
        ask("\nPress Enter to return...")
        return False

    print(f"\n🆕 {len(commits)} update(s) available:")
    for line in commits[:15]:
        print(f"   • {line}")
    if len(commits) > 15:
        print(f"   … and {len(commits) - 15} more")

    print("\n🛡️ Your own apps, plugins and settings (API keys, models) are kept.")
    changed = changed_bundled_files()
    if changed:
        print("⚠️ These bundled files you changed will get the new version (your copy goes to the backup):")
        for rel in changed[:10]:
            print(f"   - {rel}")
        if len(changed) > 10:
            print(f"   … and {len(changed) - 10} more")

    if ask("\n👉 Update now? (y/n): ").strip().lower() != "y":
        print("👍 Nothing changed.")
        return False

    try:
        saved = backup()
        print(f"💾 Backup saved: {saved}")
    except Exception as e:
        print(f"❌ Couldn't create the backup, so nothing was updated: {e}")
        ask("\nPress Enter to return...")
        return False

    try:
        head = apply_update()
    except Exception as e:
        print(f"❌ Update failed: {e}")
        print(f"   Your apps, plugins and settings are in {saved}")
        ask("\nPress Enter to return...")
        return False

    print(f"✅ Updated to: {head}")
    if do_restart:
        ask("\nPress Enter to restart acornix with the new version...")
        restart()
    return True


if __name__ == "__main__":
    run()
