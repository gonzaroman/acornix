

---

## NUEVO PROYECTO

## 🧠 IHMT: Memoria Infinita a Largo Plazo para IA

¿Te imaginas que Claude, ChatGPT o Gemini recordaran todo lo que has hablado con ellos sin importar el tiempo que pase?

¿Te imaginas que lo que hablas con un modelo lo supieran todos los demás sin tener que explicarlo todo de nuevo?

¿Te imaginas ahorrar hasta un 90% de tokens en cada consulta?

**[IHMT (Infinite Hierarchical Memory Tree)](https://github.com/gonzaroman/IHMT-MEMORY)** es un sistema de memoria a largo plazo escrito en Python que hace esto posible directamente en tu ordenador:

* **Arquitectura en árbol eficiente:** Organiza la información en pequeñas estructuras de texto y JSON. La IA navega solo hacia la información precisa que necesita, ahorrando hasta un 90% de tokens al no tener que leer todo el historial.
* **Memoria única y compartida:** Conecta tus herramientas para que cualquier modelo acceda a lo que hablaste en otras sesiones o proyectos.
* **100% Local y Privado:** Todo se guarda en tu propia máquina y puedes exportarlo fácilmente cuando quieras.

Esto cambia por completo la forma de trabajar con IA y soluciona las limitaciones de los sistemas de memoria actuales.

👉 **Mira el repositorio y pruébalo aquí:** [github.com/gonzaroman/IHMT-MEMORY](https://github.com/gonzaroman/IHMT-MEMORY)

---


---
## NEW PROYECT

## 🧠 IHMT: Infinite Long-Term Memory for AI

Imagine if Claude, ChatGPT, or Gemini remembered everything you ever discussed with them, no matter how much time has passed.

Imagine if what you shared with one model was instantly known by all the others—without ever having to explain yourself again.

Imagine saving up to 90% on token usage every time you interact with AI.

**[IHMT (Infinite Hierarchical Memory Tree)](https://github.com/gonzaroman/IHMT-MEMORY)** is an open-source Python system that makes this happen right on your local computer:

* **Efficient Tree Architecture:** Organizes knowledge using JSON and text nodes. The AI traverses only the specific branches it needs, saving up to 90% in tokens instead of re-reading massive contexts.
* **Unified Shared Memory:** Connect your AI setup so any model can access past conversations across different projects and sessions.
* **100% Local & Private:** Stored entirely on your machine, fully exportable at any time.

A true game-changer that goes far beyond traditional, limited memory setups.

👉 **Check out the repo and try it here:** [github.com/gonzaroman/IHMT-MEMORY](https://github.com/gonzaroman/IHMT-MEMORY)

---




# 🌳 acornix

**The intelligent seed for your Termux ecosystem.**

`acornix` is an AI-powered assistant designed to generate apps, manage plugins, and automate workflows directly from your terminal. Built with a nod to the legacy of **Acorn Computers** and the power of **ARM** architecture, it transforms your phone into a portable development powerhouse.

---

[Watch acornix create app Video DEMO](https://github.com/user-attachments/assets/5c12029a-2aed-4e9f-90ef-2feb5fdfcc4f)



---


## 1. Environment Setup (Android)
Before touching the code, you need the base tools on your mobile device:

* **Install Termux:** Download it preferably from F-Droid or its official GitHub (the Google Play version is obsolete). [Termux](https://f-droid.org/es/packages/com.termux/)
* **Install Termux:API:** Download the Termux:API extension (this allows the AI to control phone functions). [Termux:API](https://f-droid.org/es/packages/com.termux/)


Open Termux and update everything is up to date before you begin:

```bash
pkg update && pkg upgrade
```

---

## 2. Initial Configuration
Open Termux and make sure everything is updated before starting:

## 🚀 Quick Install

Run this command in Termux to set up everything automatically (dependencies, permissions, and alias):

```bash
pkg update && pkg upgrade -y && pkg install python git termux-api binutils -y && termux-setup-storage && git clone https://github.com/gonzaroman/acornix.git && cd acornix && pip install requests python-dotenv psutil && echo "alias acornix='cd ~/acornix && python main.py'" >> ~/.bashrc && source ~/.bashrc && python main.py

```



Note: You'll be asked for storage permissions. Please grant them to allow acornix to save your projects.

✨ Key Features
AI App Generator: Create web apps and scripts using natural language.

Plugin System: Extend acornix capabilities with modular plugins.

Termux Native: Fully optimized for mobile environments and ARM processors.

Zero Config: The one-liner takes care of everything, including your custom acornix command.

🛠 Usage
After installation, you can launch the system from anywhere by simply typing:

```bash
acornix
```
Main Menu Options:
Generate App: Describe what you want, and acornix will build it.

Manage Plugins: Install or update system extensions.

Launch Server: Preview your generated apps on your local network.

if you have a problem with the server, reebot the server:
```bash
pkill -f "python -m http.server"
python -m http.server 8080
```

# 🌳 acornix: The AI-Powered OS for Termux
> "Transform your mobile terminal into a neural development hub."
<p align="center">
<img width="401" alt="Captura de pantalla 2026-02-12 113642" src="https://github.com/user-attachments/assets/6f9498ca-f214-4751-a9da-d033bc5828ff" />
</p>


**acornix** isn't just a script; it's a procedural engine designed to bridge the gap between AI intelligence and mobile hardware. It allows you to orchestrate complex applications, automate local environments, and extend your terminal capabilities using natural language.
Markdown
> "Transform your mobile terminal into a neural development hub."

**acornix** isn't just a script; it's a procedural engine designed to bridge the gap between AI intelligence and mobile hardware. It allows you to orchestrate complex applications, automate local environments, and extend your terminal capabilities using natural language.


You can create games, applications, or in agent mode, ask it to control your phone, such as opening YouTube and searching for a Nirvana video, or ask your phone to do anything, such as sending an S.O.S. signal with the flashlight. The only limit is your imagination.
You can create whatever you want and share it with whoever you want. The system has a simple tool for exporting and importing creations, apps, etc.
# 🌳 acornixOS: The AI-Powered 
> "Transform your mobile in a PC." Press and hold an app to add it to the dock.
<p align="center">
<img src="https://github.com/user-attachments/assets/c9f7bddf-83df-4872-b6dd-b313b4b6b688" width="400" alt="acornixOS">
</p>

<br />

[Watch acornixOS Video Demo](https://github.com/user-attachments/assets/1e701c97-90d0-450a-881e-680bba5aad70)

<br />



### 📸 Showcase
*Create a simple game 3d aeroplane for mobile

*create a piano for mobile

*create a arkanoid with tactile buttons

*in mode agent: open youtube and search Nirvana videos

| | |
| :---: | :---: |
| <img src="https://github.com/user-attachments/assets/3c93ac34-93e7-438c-8c14-f37b43ccabb2" width="300" alt="avion"> | <img src="https://github.com/user-attachments/assets/beb859cc-bf43-4861-ad6a-e8254adeb0b8" width="300" alt="piano"> |
| <img src="https://github.com/user-attachments/assets/06667fcb-43b4-4098-839f-09383ce8aeb4" width="300" alt="arckanoid"> | <img src="https://github.com/user-attachments/assets/bca05911-68cf-4a70-be84-abc4e4fa1b23" width="300" alt="nirvana"> |


<p align="center">
<br> OPTION 1: Create APP <br>
  Create an app that is a canvas for painting with your fingers on a mobile device, with color selection, brush size, and the option to erase the canvas:<br>
<img width="401" alt="Paint" src="https://github.com/user-attachments/assets/555b114a-4acd-426c-a6ef-12f40dc9c9cd" />
</p>
<p align="center">
 <br> OPTION 1: Create APP <br>
  Create a castle defense game for mobile screens, where enemies attack and I can shoot with a cannon to eliminate them. The difficulty should increase as I progress, and I should receive more powerful weapons:<br>
<img width="401" alt="Castle" src="https://github.com/user-attachments/assets/19197abb-aafe-465d-bef1-dfe519fec31a" />
</p>
<p align="center">
  <br> OPTION 2: Create FUNCTIONALITY <br>
  Create a plugin or functionality that displays all the applications created in the my_apps folder. The style should be similar to how applications are displayed on a mobile phone:<br>
<img width="401" alt="Library" src="https://github.com/user-attachments/assets/d78448f8-beba-4116-9162-9c5210492109" />
</p>
<p align="center">
  <br> OPTION 1: Create APP <br>
  Create a solar system simulator that shows how the planets move. Make it mobile so I can control it with my fingers:<br>
<img width="401" alt="Solar sytem" src="https://github.com/user-attachments/assets/e3073cf5-1e1b-4a96-b82e-4d56a63c6a3a" />
</p>
<p align="center">
  <br> OPTION 1: Create APP <br>
  Create a game similar to Flappy Bird for mobile phones:<br>
<img width="401" alt="Flappy bird" src="https://github.com/user-attachments/assets/c1fc7e28-f295-4977-bdfe-77dc7d0fbbd6" />
</p>

<p align="center">
  <br> OPTION 2: Create FUNCTIONALITY <br>
  Create a plugin or feature that displays a MacOS-based operating system, where I can control everything in Acornix, and where applications and plugins are displayed. Windows should be moved with my fingers; if I touch a window, it moves on top of the others. If I hold down an application, it is added to the dock. The terminal must communicate with the Termux terminal and must execute real commands, not simulate them:<br>
<img width="401" alt="AcornixOS" src="https://github.com/user-attachments/assets/c9f7bddf-83df-4872-b6dd-b313b4b6b688" />
</p>



--- 


## 🧠 Why acornix?
Most mobile environments are limited. **acornix** breaks those barriers by using a **Context-Aware Architecture** to turn simple text prompts into fully functional, locally-hosted software.

---

## ✨ Advanced Capabilities

| Feature | Description | Technical Edge |
| :--- | :--- | :--- |
| **Neural Architect** | Generate high-performance web apps and Python tools from scratch. | Uses custom system prompts for ARM optimization. |
| **Modular Core** | Hot-swappable plugin system to add new commands without restarts. | Dynamic importing via `core/` logic. |
| **Environment Sync** | Automatic SFTP and local server management for instant previews. | Integrated `http.server` with automatic port cleanup. |
| **Hardware Mastery** | Direct access to Termux API for hardware-level interaction. | Low-level integration with `binutils` and `psutil`. |

---

## 🛠 Pro Workflow: From Idea to Execution
Don't just run code. **Build systems.** Here is how acornix handles the lifecycle of a project:

$$\text{Prompt} \xrightarrow{\text{IA Engine}} \text{Optimized Code} \xrightarrow{\text{Acornix Core}} \text{Local Deployment}$$

### Example Commands:
> *"Create a dashboard to monitor my device battery and CPU usage using a modern dark-mode UI."*

1.  **Generation:** acornix builds the HTML5/CSS3/JS bundle.
2.  **Deployment:** The system places it in `my_apps/monitor_pro`.
3.  **Hosting:** Start the local server and access it at `localhost:8080`.

---

## 📂 System Architecture
The heart of **acornix** is organized for scalability:

```text
acornix/
├── 🧠 main.py           # The Kernel: Entry point & process manager
├── 🛠 core/              # The Brain: Neural logic and prompt engineering
├── 🔌 plugins/          # The Limbs: Modular extensions for the system
├── 📦 my_apps/          # The Vault: AI-generated projects & assets
└── ⚙️ config.json       # The DNA: Secure credentials and API keys
```
Gonzalo Román Márquez
