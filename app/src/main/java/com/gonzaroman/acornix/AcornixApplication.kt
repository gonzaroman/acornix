package com.gonzaroman.acornix

import android.app.Application
import android.content.Context
import com.gonzaroman.acornix.agent.AgentTools
import com.gonzaroman.acornix.agent.AssistantController
import com.gonzaroman.acornix.core.AppGenerator
import com.gonzaroman.acornix.data.AppDataStore
import com.gonzaroman.acornix.data.AppRepository
import com.gonzaroman.acornix.data.BackupManager
import com.gonzaroman.acornix.data.ImportExport
import com.gonzaroman.acornix.data.SampleAppsInstaller
import com.gonzaroman.acornix.data.SettingsRepository
import com.gonzaroman.acornix.data.UpdateChecker
import com.gonzaroman.acornix.device.DeviceActions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Manual dependency container shared by every screen. */
class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val settings = SettingsRepository(context)
    val data = AppDataStore(context)
    val apps = AppRepository(context.filesDir) { id -> data.deleteApp(id) }
    val importExport = ImportExport(context, apps, data)
    val backups = BackupManager(context, apps, data)
    val updates = UpdateChecker(context)
    val device = DeviceActions(context)
    val generator = AppGenerator(settings, apps, data)
    val tools = AgentTools(context, device, apps, generator, data)
    val assistant = AssistantController(context, appScope, settings, apps, device, tools)
    private val samples = SampleAppsInstaller(context, apps)

    fun start() {
        appScope.launch {
            apps.refresh()
            samples.installIfNeeded()
            // Quiet daily check for a newer release on GitHub; failures (offline…) are ignored.
            delay(2500)
            runCatching { updates.check() }
        }
    }
}

class AcornixApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this).also { it.start() }
    }
}

val Context.container: AppContainer
    get() = (applicationContext as AcornixApplication).container
