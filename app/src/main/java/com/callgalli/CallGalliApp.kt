package com.callgalli

import android.app.Application
import android.content.Context
import com.callgalli.data.CallGalliDatabase
import com.callgalli.data.CallRepository
import com.callgalli.data.ContactsRepository
import com.callgalli.data.SettingsStore
import com.callgalli.telecom.CallNotifications
import com.callgalli.telecom.CallMonitor
import com.callgalli.telecom.CallMonitorService
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Simple hand-made dependency container. One instance per app process. */
class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val settings = SettingsStore(context)
    val database = CallGalliDatabase.create(context)
    val repository = CallRepository(database, settings)
    val contacts = ContactsRepository(context)
    val notifications = CallNotifications(context)
    val callMonitor = CallMonitor(context, repository, contacts, settings, notifications)
    private val app = context.applicationContext

    /** The main On/Off switch. */
    fun setTimerEnabled(on: Boolean) {
        settings.setEnabled(on)
        if (on) {
            CallMonitorService.start(app)
        } else {
            callMonitor.stopAll()
            app.stopService(Intent(app, CallMonitorService::class.java))
        }
    }
}

class CallGalliApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.notifications.createChannels()
    }
}

val Context.container: AppContainer get() = (applicationContext as CallGalliApp).container
