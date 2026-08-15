package com.geolandmarks.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.geolandmarks.app.di.AppContainer
import org.osmdroid.config.Configuration

class LandmarkApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = packageName
        container = AppContainer(this)
        createVisitChannel()
        container.repository.scheduleBackgroundSync()
        registerNetworkCallback()
    }

    private fun registerNetworkCallback() {
        val cm = getSystemService(android.net.ConnectivityManager::class.java)
        cm.registerDefaultNetworkCallback(object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) {
                container.repository.enqueueSync(expedited = false)
            }
        })
    }

    private fun createVisitChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                VISIT_CHANNEL_ID,
                "Visit updates",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    companion object {
        const val VISIT_CHANNEL_ID = "visit_updates"
    }
}
