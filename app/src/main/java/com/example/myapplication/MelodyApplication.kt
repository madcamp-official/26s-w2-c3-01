package com.example.myapplication

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.example.myapplication.data.local.SecureTokenStore
import com.example.myapplication.data.presence.PresenceSyncCoordinator
import com.example.myapplication.data.realtime.RealtimeSystemNotifier
import com.example.myapplication.data.realtime.RealtimeInboxStore
import com.example.myapplication.data.realtime.RealtimeEvent
import com.example.myapplication.data.realtime.StompRealtimeClient
import com.example.myapplication.data.remote.ApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class MelodyApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var realtimeClient: StompRealtimeClient
        private set
    lateinit var realtimeInboxStore: RealtimeInboxStore
        private set

    @Volatile
    var isAppInForeground: Boolean = false
        private set

    private var startedActivityCount = 0

    override fun onCreate() {
        super.onCreate()
        val coordinator = PresenceSyncCoordinator.get(this)
        val tokenStore = SecureTokenStore(this)
        realtimeInboxStore = RealtimeInboxStore(this)
        ApiClient.configureSession(tokenStore) {
            coordinator.onSessionCleared()
            realtimeClient.disconnect()
        }
        realtimeClient = StompRealtimeClient(BuildConfig.STOMP_WS_URL.trim())
        ApiClient.addAccessTokenListener { refreshedToken ->
            realtimeInboxStore.activate(refreshedToken)
            coordinator.onSessionAvailable(refreshedToken)
            realtimeClient.connect(refreshedToken)
        }
        ApiClient.addSessionExpiredListener {
            realtimeInboxStore.clear()
            coordinator.onSessionCleared()
            realtimeClient.disconnect()
        }
        registerActivityLifecycleCallbacks(ForegroundCallbacks())
        val notifier = RealtimeSystemNotifier(this)
        applicationScope.launch {
            realtimeClient.events.collect { event ->
                realtimeInboxStore.record(event)
                if (!isAppInForeground || event is RealtimeEvent.NearbyReactionCreated) {
                    notifier.present(event)
                }
            }
        }
        coordinator.restoreSession()
        runCatching { tokenStore.load()?.accessToken }.getOrNull()?.let { storedToken ->
            realtimeInboxStore.activate(storedToken)
            realtimeClient.connect(storedToken)
        }
    }

    private inner class ForegroundCallbacks : ActivityLifecycleCallbacks {
        override fun onActivityStarted(activity: Activity) {
            startedActivityCount += 1
            isAppInForeground = startedActivityCount > 0
        }

        override fun onActivityStopped(activity: Activity) {
            startedActivityCount = (startedActivityCount - 1).coerceAtLeast(0)
            isAppInForeground = startedActivityCount > 0
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }
}
