import os
import http.server
import socketserver
import threading
import shutil
import time

# Plugin Configuration
config = {"label": "Help", "icon": "📘"}

PORT = 8083

HTML_CONTENT = """<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>AcornixOS - Official Manual</title>
  <style>
    :root {
      --bg: #0f172a; --bg-panel: #1e293b; --bg-hover: #334155;
      --text-main: #f8fafc; --text-muted: #94a3b8;
      --accent: #38bdf8; --accent-glow: rgba(56, 189, 248, 0.4);
      --success: #10b981; --warning: #f59e0b;
    }
    * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif; }
    body { background-color: var(--bg); color: var(--text-main); display: flex; flex-direction: column; height: 100vh; overflow: hidden; }
    
    /* Header */
    header { background: var(--bg-panel); padding: 16px 20px; display: flex; align-items: center; gap: 12px; border-bottom: 1px solid #334155; box-shadow: 0 4px 20px rgba(0,0,0,0.2); z-index: 10; flex-shrink: 0; }
    header svg { width: 32px; height: 32px; color: var(--accent); filter: drop-shadow(0 0 8px var(--accent-glow)); }
    header h1 { font-size: 20px; font-weight: 700; letter-spacing: 1px; }
    header .badge { background: var(--accent); color: #000; font-size: 10px; padding: 3px 8px; border-radius: 12px; font-weight: 900; letter-spacing: 1px; }

    /* Layout */
    .layout { display: flex; flex: 1; overflow: hidden; flex-direction: column; }
    @media (min-width: 768px) { .layout { flex-direction: row; } }

    /* Navigation */
    nav { background: var(--bg-panel); display: flex; overflow-x: auto; padding: 10px; gap: 8px; border-bottom: 1px solid #334155; flex-shrink: 0; }
    nav::-webkit-scrollbar { display: none; }
    @media (min-width: 768px) { nav { flex-direction: column; width: 260px; overflow-y: auto; border-bottom: none; border-right: 1px solid #334155; padding: 20px 10px; } }
    
    .nav-item { display: flex; align-items: center; gap: 10px; padding: 12px 16px; border-radius: 10px; cursor: pointer; transition: all 0.2s; white-space: nowrap; font-size: 14px; font-weight: 600; color: var(--text-muted); border: 1px solid transparent; }
    .nav-item:hover { background: var(--bg-hover); color: var(--text-main); }
    .nav-item.active { background: rgba(56, 189, 248, 0.1); color: var(--accent); border-color: rgba(56, 189, 248, 0.3); }
    .nav-item svg { width: 20px; height: 20px; flex-shrink: 0; }

    /* Content Area */
    main { flex: 1; overflow-y: auto; padding: 20px; scroll-behavior: smooth; }
    @media (min-width: 768px) { main { padding: 40px; } }
    .section { display: none; max-width: 800px; margin: 0 auto; animation: fadeIn 0.3s ease-out; }
    .section.active { display: block; }
    @keyframes fadeIn { from { opacity: 0; transform: translateY(10px); } to { opacity: 1; transform: translateY(0); } }

    /* Typography & Components */
    h2 { font-size: 28px; margin-bottom: 10px; color: var(--text-main); display: flex; align-items: center; gap: 10px; }
    p { font-size: 15px; line-height: 1.6; color: var(--text-muted); margin-bottom: 20px; }
    
    .card { background: var(--bg-panel); border: 1px solid #334155; border-radius: 12px; padding: 20px; margin-bottom: 20px; box-shadow: 0 4px 6px rgba(0,0,0,0.1); }
    .card h3 { font-size: 18px; color: var(--accent); margin-bottom: 10px; display: flex; align-items: center; gap: 8px; }
    .card ul { margin-left: 20px; color: var(--text-muted); font-size: 14px; line-height: 1.6; }
    .card li { margin-bottom: 8px; }
    
    .step-grid { display: grid; grid-template-columns: 1fr; gap: 15px; margin-top: 15px; }
    @media (min-width: 600px) { .step-grid { grid-template-columns: 1fr 1fr; } }
    .step { background: rgba(0,0,0,0.2); border-left: 3px solid var(--accent); padding: 15px; border-radius: 0 8px 8px 0; }
    .step-num { font-size: 12px; color: var(--accent); font-weight: 900; text-transform: uppercase; letter-spacing: 1px; margin-bottom: 5px; }
    .step-text { font-size: 14px; color: #cbd5e1; }

    code { background: #000; padding: 2px 6px; border-radius: 4px; font-family: monospace; color: var(--warning); font-size: 13px; }
    pre { background: #000; padding: 15px; border-radius: 8px; overflow-x: auto; font-family: monospace; color: var(--success); font-size: 13px; border: 1px solid #334155; margin-bottom: 15px; }

    .hero-svg { width: 100%; max-width: 300px; height: auto; margin: 0 auto 30px auto; display: block; }
  </style>
</head>
<body>

  <header>
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 2L2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5"/></svg>
    <h1>AcornixOS</h1>
    <span class="badge">MANUAL</span>
  </header>

  <div class="layout">
    <nav>
      <div class="nav-item active" onclick="show('intro', this)">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"></path><polyline points="9 22 9 12 15 12 15 22"></polyline></svg>
        Welcome
      </div>
      <div class="nav-item" onclick="show('aistudio', this)">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="18" height="18" rx="2" ry="2"></rect><line x1="9" y1="9" x2="15" y2="15"></line><line x1="15" y1="9" x2="9" y2="15"></line></svg>
        1. AI Studio (No API)
      </div>
      <div class="nav-item" onclick="show('creator', this)">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="16 18 22 12 16 6"></polyline><polyline points="8 6 2 12 8 18"></polyline></svg>
        2. App Creator (API)
      </div>
      <div class="nav-item" onclick="show('evolve', this)">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 2v20M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6"></path></svg>
        3. System Evolve
      </div>
      <div class="nav-item" onclick="show('manage', this)">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="2" y="3" width="20" height="14" rx="2" ry="2"></rect><line x1="8" y1="21" x2="16" y2="21"></line><line x1="12" y1="17" x2="12" y2="21"></line></svg>
        4. Management
      </div>
      <div class="nav-item" onclick="show('tips', this)">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"></circle><line x1="12" y1="16" x2="12" y2="12"></line><line x1="12" y1="8" x2="12.01" y2="8"></line></svg>
        Tips & Shortcuts
      </div>
    </nav>

    <main>
      <section id="intro" class="section active">
        <svg class="hero-svg" viewBox="0 0 200 200" xmlns="http://www.w3.org/2000/svg">
          <circle cx="100" cy="100" r="80" fill="none" stroke="#334155" stroke-width="2"/>
          <circle cx="100" cy="100" r="60" fill="none" stroke="#38bdf8" stroke-width="4" stroke-dasharray="20 10"/>
          <circle cx="100" cy="100" r="20" fill="#38bdf8"/>
          <path d="M100 20 L100 40 M100 160 L100 180 M20 100 L40 100 M160 100 L180 100" stroke="#38bdf8" stroke-width="4" stroke-linecap="round"/>
        </svg>
        
        <h2>Welcome to AcornixOS</h2>
        <p>Acornix is a dynamic, expandable operating environment built for Termux. It uses a modular plugin architecture that allows it to grow, write its own code, and evolve over time.</p>
        
        <div class="card">
          <h3>🚀 Core Philosophy</h3>
          <p>You don't just use Acornix; you shape it. Whether you are creating Web Apps (HTML/JS) or internal OS Plugins (Python), everything is modular. If a tool doesn't exist, you can create it in seconds using Artificial Intelligence.</p>
        </div>
        <p><i>Select a topic from the menu to learn how to master your OS.</i></p>
      </section>

      <section id="aistudio" class="section">
        <h2>🤖 AI Studio (Create without API)</h2>
        <p>Don't have an API key (OpenAI, Anthropic, Gemini...)? No problem. The <strong>AI Studio</strong> lets you create apps and plugins by acting as a bridge between Acornix and free external AIs like ChatGPT or Claude.</p>
        
        <div class="card">
          <h3>How it works:</h3>
          <div class="step-grid">
            <div class="step">
              <div class="step-num">Step 1: Init</div>
              <div class="step-text">Open "AI Studio" from the terminal. Choose HTML App or Python Plugin and give it a name.</div>
            </div>
            <div class="step">
              <div class="step-num">Step 2: Copy Prompt</div>
              <div class="step-text">The browser opens with a template. Click the green <b>Copy for AI</b> button. It contains hidden rules for the AI.</div>
            </div>
            <div class="step">
              <div class="step-num">Step 3: Ask AI</div>
              <div class="step-text">Paste it into ChatGPT, Gemini, Claude, or any other AI. Find the <code>[ WRITE YOUR IDEA HERE ]</code> bracket and type what you want the app to do.</div>
            </div>
            <div class="step">
              <div class="step-num">Step 4: Save & Apply</div>
              <div class="step-text">Copy the code ChatGPT, Gemini, Claude, or any other AI gives you, paste it back into the Acornix Studio editor, and click <b>Save & Apply</b>.</div>
            </div>
          </div>
        </div>
        <p>Once saved, return to the terminal and press ENTER. Your new creation is instantly compiled and ready to use!</p>
      </section>

      <section id="creator" class="section">
        <h2>🏗️ App Creator (With API)</h2>
        <p>If you have an API key configured, Acornix can write applications completely autonomously inside the terminal.</p>
        
        <div class="card">
          <h3>⚡ Fully Automatic Workflow</h3>
          <ul>
            <li>Select the App Creator plugin from the menu.</li>
            <li>Type your idea (e.g., <i>"A neon-style calculator with glassmorphism"</i>).</li>
            <li>The AI communicates directly with Acornix. It formats the output inside <code>---CODE---</code> blocks.</li>
            <li>Acornix parses these blocks and automatically saves the files into the <code>my_apps/</code> directory.</li>
          </ul>
        </div>
        <p>Once finished, Acornix can automatically start the local server and open your new WebApp in your mobile browser.</p>
      </section>

      <section id="evolve" class="section">
        <h2>🧬 System Evolve & Auto-Update</h2>
        <p>Acornix is self-aware. It can read its own source code and generate new Python plugins to extend its core functionality.</p>
        
        <div class="card">
          <h3>How the OS learns:</h3>
          <p>The system reads a file called <code>totalcode.txt</code>. This file acts as the "memory" or "context" for the AI, letting it know exactly how your OS routes, menus, and APIs function.</p>
          <pre><code># Always run this command if you change core files:
# It updates the memory for the AI
cat main.py plugins/*.py > totalcode.txt</code></pre>
        </div>
        <div class="card">
          <h3>⚠️ Warning</h3>
          <p>Plugins generated here are saved in the <code>plugins/</code> folder and have direct access to your OS. Always review the generated code before executing a new plugin to ensure it doesn't break the system loop.</p>
        </div>
      </section>

      <section id="manage" class="section">
        <h2>🗂️ App & Project Management</h2>
        <p>Your creations need a home. Acornix includes built-in managers to handle your files safely.</p>
        
        <div class="step-grid">
          <div class="card" style="margin-bottom:0;">
            <h3>📱 Visual Desktop</h3>
            <p>Generates an iOS/Android style launcher grid in your browser. You can tap icons to launch your HTML apps, or trigger Python scripts directly.</p>
          </div>
          <div class="card" style="margin-bottom:0;">
            <h3>🗑️ Safe Deletion</h3>
            <p>The Delete Apps module safely removes project folders from <code>my_apps/</code>, ensuring you don't accidentally wipe core OS files.</p>
          </div>
        </div>
      </section>

      <section id="tips" class="section">
        <h2>💡 Tips & Shortcuts</h2>
        <p>Master Acornix by understanding its architecture and knowing the right Termux commands.</p>
        
        <div class="card">
          <h3>📂 Directory Structure</h3>
          <ul>
            <li><code>main.py</code>: The core bootloader and OS loop.</li>
            <li><code>plugins/</code>: System tools (Python). Loaded dynamically at boot.</li>
            <li><code>my_apps/</code>: Your created WebApps and standalone Python scripts.</li>
          </ul>
        </div>

        <div class="card">
          <h3>⚙️ Termux Essentials</h3>
          <ul>
            <li><b>Manual Server:</b> <code>python -m http.server 8080</code> (Runs a server in the current folder)</li>
            <li><b>Open URL:</b> <code>termux-open-url http://localhost:8080</code></li>
            <li><b>Stop execution:</b> Press <code>CTRL + C</code> in the terminal to force-stop a stuck plugin.</li>
          </ul>
        </div>

        <div class="card">
          <h3>🔄 Updating Acornix</h3>
          <p>When the main menu shows <b>🆕 Update available</b>, open <b>🔄 Update acornix</b> in ⚙️ SETTINGS. Your own apps, plugins and settings are kept; the sample apps and system files get the new version. A backup ZIP is saved first in <code>backups/updates/</code>.</p>
        </div>
      </section>

    </main>
  </div>

  <script>
    function show(sectionId, element) {
      // Update nav styles
      document.querySelectorAll('.nav-item').forEach(el => el.classList.remove('active'));
      element.classList.add('active');
      
      // Update content
      document.querySelectorAll('.section').forEach(sec => sec.classList.remove('active'));
      document.getElementById(sectionId).classList.add('active');

      // Scroll to top on mobile
      if(window.innerWidth < 768) {
        document.querySelector('main').scrollTop = 0;
      }
    }
  </script>
</body>
</html>"""

class HelpHandler(http.server.SimpleHTTPRequestHandler):
    def log_message(self, format, *args):
        pass # Silencia los logs en la terminal

    def do_GET(self):
        if self.path == '/' or self.path == '/index.html':
            self.send_response(200)
            self.send_header('Content-Type', 'text/html; charset=utf-8')
            self.end_headers()
            self.wfile.write(HTML_CONTENT.encode('utf-8'))
            return
        self.send_error(404)

def run():
    print("==========================================")
    print("        📘 ACORNIX MANUAL STARTED       ")
    print("==========================================")
    
    # Iniciar servidor local
    socketserver.ThreadingTCPServer.allow_reuse_address = True
    httpd = socketserver.ThreadingTCPServer(("", PORT), HelpHandler)
    server_thread = threading.Thread(target=httpd.serve_forever, daemon=True)
    server_thread.start()

    url = f"http://localhost:{PORT}/"
    print(f"\n🌐 Manual is running on: {url}")
    
    termux = shutil.which("termux-open-url")
    if termux:
        os.system(f'{termux} "{url}"')
    else:
        print(f"👉 Please open this URL in your browser: {url}")

    input("\n⏸️  Press [ENTER] to close the manual and return to Acornix...")
    
    print("Shutting down manual...")
    httpd.shutdown()

if __name__ == "__main__":
    run()