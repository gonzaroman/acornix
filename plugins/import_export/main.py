import os
import zipfile
import http.server
import socketserver
from email import policy
from email.parser import BytesParser
import json
import threading
import shutil
from urllib.parse import urlparse, parse_qs

# Configuration for the main menu
config = {"label": "Smart Import-Export Hub", "icon": "📦"}
PORT = 8081

def read_uploaded_file(handler, field="file"):
    """Returns (filename, bytes) of a multipart/form-data upload (the cgi module is gone since Python 3.13)."""
    length = int(handler.headers.get('Content-Length', 0))
    body = handler.rfile.read(length)
    head = f"Content-Type: {handler.headers.get('Content-Type', '')}\r\n\r\n".encode()
    message = BytesParser(policy=policy.HTTP).parsebytes(head + body)
    if not message.is_multipart():
        return None, None
    for part in message.iter_parts():
        if part.get_param('name', header='content-disposition') == field:
            return part.get_filename(), part.get_payload(decode=True)
    return None, None

def detect_and_validate_zip(zip_path):
    """
    Analyzes the ZIP structure and automatically decides the destination.
    Returns (target_folder, error_message)
    """
    try:
        with zipfile.ZipFile(zip_path, 'r') as zip_ref:
            files = zip_ref.namelist()
            
            # 1. Plugin detection (Look for main.py with a config dict)
            if "main.py" in files:
                with zip_ref.open("main.py") as f:
                    content = f.read().decode('utf-8', errors='ignore')
                    if "config =" in content or "config=" in content:
                        return "plugins", ""  # Confirmed System Plugin
                    else:
                        return "my_apps", ""  # Python-based App
            
            # 2. Web App detection (Look for index.html)
            if "index.html" in files:
                return "my_apps", ""
            
            return None, "Invalid structure: ZIP must contain 'main.py' or 'index.html' at the root."
    except Exception as e:
        return None, f"Error reading ZIP: {str(e)}"

class ManagementHandler(http.server.SimpleHTTPRequestHandler):
    def _send_alert(self, message, is_error=False):
        """Sends a Javascript alert to the browser to provide user feedback."""
        try:
            self.send_response(200)
            self.send_header('Content-type', 'text/html; charset=utf-8')
            self.end_headers()
            icon = "⚠️" if is_error else "✅"
            html = f"""
            <html><head><meta charset="utf-8"></head>
            <body><script>
                alert("{icon} {message}");
                window.location.href = "/";
            </script></body></html>
            """
            self.wfile.write(html.encode('utf-8'))
        except Exception as e:
            print(f"Feedback Error: {e}")

    def do_GET(self):
        if self.path == '/':
            self.send_response(200)
            self.send_header('Content-type', 'text/html; charset=utf-8')
            self.end_headers()
            self.wfile.write(self._get_html().encode('utf-8'))
            
        elif self.path == '/list':
            self.send_response(200)
            self.send_header('Content-type', 'application/json')
            self.end_headers()
            data = {
                "plugins": [d for d in os.listdir("plugins") if os.path.isdir(os.path.join("plugins", d))],
                "apps": [d for d in os.listdir("my_apps") if os.path.isdir(os.path.join("my_apps", d))] if os.path.exists("my_apps") else []
            }
            self.wfile.write(json.dumps(data).encode())
            
        elif self.path.startswith('/download'):
            query = parse_qs(urlparse(self.path).query)
            ctype = query.get('type', [''])[0]
            name = query.get('name', [''])[0]
            target_dir = os.path.join(ctype, name)
            
            zip_name = f"{name}.zip"
            os.makedirs("temp_exports", exist_ok=True)
            zip_path = os.path.join("temp_exports", zip_name)
            
            # Create the ZIP for download
            with zipfile.ZipFile(zip_path, 'w', zipfile.ZIP_DEFLATED) as zipf:
                for root, _, files in os.walk(target_dir):
                    for file in files:
                        file_full_path = os.path.join(root, file)
                        zipf.write(file_full_path, os.path.relpath(file_full_path, target_dir))
            
            if os.path.exists(zip_path):
                with open(zip_path, 'rb') as f:
                    self.send_response(200)
                    self.send_header('Content-Type', 'application/zip')
                    self.send_header('Content-Disposition', f'attachment; filename="{zip_name}"')
                    self.end_headers()
                    self.wfile.write(f.read())
            
        elif self.path == '/shutdown':
            self.send_response(200)
            self.send_header('Content-type', 'text/html')
            self.end_headers()
            self.wfile.write(b"Server stopped. You may return to Termux.")
            threading.Thread(target=self.server.shutdown).start()

    def do_POST(self):
        if self.path == '/import':
            try:
                filename, data = read_uploaded_file(self)

                if not filename:
                    return self._send_alert("Please select a file.", is_error=True)

                filename = os.path.basename(filename)

                if not filename.lower().endswith('.zip'):
                    return self._send_alert(f"File '{filename}' is not a ZIP archive.", is_error=True)

                # Save file to temporary import folder
                os.makedirs("temp_imports", exist_ok=True)
                temp_zip = os.path.join("temp_imports", filename)
                with open(temp_zip, 'wb') as f:
                    f.write(data)

                # Smart Auto-Detection of content type
                target_folder, error_msg = detect_and_validate_zip(temp_zip)
                
                if not target_folder:
                    if os.path.exists(temp_zip): os.remove(temp_zip)
                    return self._send_alert(error_msg, is_error=True)

                # Prepare extraction path (cleanup existing if necessary)
                folder_name = filename.rsplit('.', 1)[0]
                extract_path = os.path.join(target_folder, folder_name)
                
                if os.path.exists(extract_path):
                    shutil.rmtree(extract_path)
                
                os.makedirs(extract_path, exist_ok=True)
                with zipfile.ZipFile(temp_zip, 'r') as zip_ref:
                    zip_ref.extractall(extract_path)
                
                os.remove(temp_zip) # Cleanup temp ZIP
                label_type = "Plugin" if target_folder == "plugins" else "Application"
                return self._send_alert(f"{label_type} detected and successfully installed!")

            except Exception as e:
                return self._send_alert(f"Import Error: {str(e)}", is_error=True)

    def _get_html(self):
        return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
            <title>Smart File Hub</title>
            <style>
                body { font-family: sans-serif; background: #0f172a; color: white; padding: 20px; text-align: center; }
                .card { background: #1e293b; padding: 20px; border-radius: 12px; margin-bottom: 20px; border: 1px solid #334155; text-align: left; }
                h2 { color: #38bdf8; margin-top: 0; }
                button { background: #3b82f6; color: white; border: none; padding: 12px; border-radius: 8px; cursor: pointer; font-weight: bold; width: 100%; transition: 0.3s; }
                button:hover { background: #2563eb; }
                .btn-exit { background: #ef4444; margin-top: 20px; padding: 15px; }
                .btn-exit:hover { background: #dc2626; }
                .item { display: flex; justify-content: space-between; align-items: center; margin: 10px 0; padding: 10px; background: #334155; border-radius: 8px; }
                .item button { width: auto; padding: 8px 15px; font-size: 0.9em; }
                input { padding: 15px; margin-bottom: 15px; border-radius: 8px; background: #0f172a; color: white; border: 1px solid #475569; width: 100%; box-sizing: border-box; }
                h3 { border-bottom: 1px solid #334155; padding-bottom: 5px; color: #94a3b8; }
            </style>
        </head>
        <body>
            <h1>📦 Smart File Hub</h1>
            <p>Upload a ZIP file. The system will auto-detect if it's a <b>Plugin</b> or an <b>App</b>.</p>

            <div class="card">
                <h2>📥 Import Content</h2>
                <form action="/import" method="post" enctype="multipart/form-data">
                    <input type="file" name="file" accept=".zip" required>
                    <button type="submit">Upload & Auto-Install</button>
                </form>
            </div>

            <div class="card">
                <h2>📤 Export / Manage</h2>
                <div id="list">Loading content...</div>
            </div>

            <button class="btn-exit" onclick="shutdown()">🛑 STOP SERVER & RETURN TO MENU</button>

            <script>
                async function load() {
                    try {
                        const res = await fetch('/list');
                        const data = await res.json();
                        let h = '';
                        const render = (items, type) => {
                            if(items.length === 0) h += '<p style="font-size: 0.8em; color: #64748b;">No items found.</p>';
                            items.forEach(name => {
                                h += `<div class="item"><span>${name}</span> <button onclick="location.href='/download?type=${type}&name=${name}'">Download ZIP</button></div>`;
                            });
                        };
                        h += '<h3>System Plugins</h3>'; render(data.plugins, 'plugins');
                        h += '<h3>My Applications</h3>'; render(data.apps, 'my_apps');
                        document.getElementById('list').innerHTML = h;
                    } catch(e) {
                        document.getElementById('list').innerHTML = "Error loading list.";
                    }
                }
                async function shutdown() {
                    if(confirm("Stop the Hub and return to Termux menu?")) {
                        await fetch('/shutdown');
                        document.body.innerHTML = "<h1>Hub Closed.</h1><p>You can now close this tab and return to the terminal.</p>";
                    }
                }
                load();
            </script>
        </body>
        </html>
        """

def run():
    print(f"\n--- 📦 SMART HUB STARTING ---")
    socketserver.ThreadingTCPServer.allow_reuse_address = True
    
    # Automate opening in Termux
    os.system(f'termux-open-url "http://localhost:{PORT}"')
    
    with socketserver.ThreadingTCPServer(("", PORT), ManagementHandler) as httpd:
        try:
            print(f"📡 Web Interface active at http://localhost:{PORT}")
            print("👉 Use the web page to manage your files.")
            httpd.serve_forever()
        except KeyboardInterrupt:
            pass
        finally:
            print("\n🛑 Hub stopped. Returning to main menu...")
            httpd.server_close()
            # Optional: Cleanup export folder on exit
            if os.path.exists("temp_exports"):
                shutil.rmtree("temp_exports")