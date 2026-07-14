package com.example.myapplication.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import com.example.myapplication.MainActivity
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

/**
 * A user-started foreground service that keeps nearby sharing active.
 *
 * Raw locations are deliberately never logged or persisted. Only the receipt time and a coarse
 * accuracy label are written to this app's private SharedPreferences so that the UI can describe
 * the health of sharing without retaining location history.
 */
class SharingForegroundService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationManager: LocationManager
    private lateinit var notificationManager: NotificationManager
    private var isForeground = false
    private var isReceivingLocationUpdates = false
    private var isReceivingGpsUpdates = false
    private var interactiveMode = true
    private var locationRequestGeneration = 0

    private val preferences by lazy(LazyThreadSafetyMode.NONE) {
        getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.locations.forEach { publishLocationIfUsable(it, source = "fused") }
        }
    }

    private val gpsLocationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            publishLocationIfUsable(location, source = "gps")
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationManager = getSystemService(LocationManager::class.java)
        notificationManager = getSystemService(NotificationManager::class.java)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        val isProfileUpdate = intent?.action == ACTION_UPDATE_PROFILE
        val nextInteractiveMode = intent?.getBooleanExtra(EXTRA_INTERACTIVE, interactiveMode) ?: interactiveMode
        val profileChanged = interactiveMode != nextInteractiveMode
        interactiveMode = nextInteractiveMode

        // A location foreground service cannot be promoted on Android 14+ without a currently
        // granted while-in-use location permission. The UI should request permission first and
        // invoke start() while its Activity is visible.
        if (!hasLocationPermission(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (!isForeground && !promoteToForeground()) {
            stopSelf()
            return START_NOT_STICKY
        }

        preferences.edit().putBoolean(KEY_SHARING_ACTIVE, true).apply()
        publishSharingState(active = true)
        if (isProfileUpdate && profileChanged) restartLocationUpdates() else requestLocationUpdatesIfNeeded()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        locationRequestGeneration += 1
        if (isReceivingLocationUpdates) {
            runCatching { fusedLocationClient.removeLocationUpdates(locationCallback) }
            isReceivingLocationUpdates = false
        }
        stopGpsUpdates()
        preferences.edit().putBoolean(KEY_SHARING_ACTIVE, false).apply()
        publishSharingState(active = false)
        if (isForeground) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            isForeground = false
        }
        super.onDestroy()
    }

    private fun promoteToForeground(): Boolean {
        val notification = buildNotification()
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            isForeground = true
            true
        } catch (_: RuntimeException) {
            // Covers missing manifest declarations, background-start restrictions and revoked
            // while-in-use permission. Nothing sensitive is logged from the failed start.
            false
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationUpdatesIfNeeded() {
        if (!hasLocationPermission(this)) return
        val profile = if (interactiveMode) NearbyLocationPolicy.INTERACTIVE else NearbyLocationPolicy.EFFICIENT
        if (!isReceivingLocationUpdates) {
            val request = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                profile.intervalMillis,
            )
                .setMinUpdateIntervalMillis(profile.minIntervalMillis)
                .setMinUpdateDistanceMeters(profile.minDistanceMeters)
                .setMaxUpdateAgeMillis(NearbyLocationPolicy.MAX_AGE_MILLIS)
                .build()
            try {
                fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
                    .addOnFailureListener { isReceivingLocationUpdates = false }
                isReceivingLocationUpdates = true
            } catch (_: SecurityException) {
                isReceivingLocationUpdates = false
            }
        }
        if (!isReceivingGpsUpdates && hasFineLocationPermission(this)) {
            try {
                if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        profile.intervalMillis,
                        profile.minDistanceMeters,
                        gpsLocationListener,
                        Looper.getMainLooper(),
                    )
                    isReceivingGpsUpdates = true
                }
            } catch (_: SecurityException) {
                isReceivingGpsUpdates = false
            } catch (_: IllegalArgumentException) {
                isReceivingGpsUpdates = false
            }
        }
    }

    private fun restartLocationUpdates() {
        stopGpsUpdates()
        if (!isReceivingLocationUpdates) {
            requestLocationUpdatesIfNeeded()
            return
        }
        val generation = ++locationRequestGeneration
        fusedLocationClient.removeLocationUpdates(locationCallback).addOnCompleteListener {
            if (generation != locationRequestGeneration) return@addOnCompleteListener
            isReceivingLocationUpdates = false
            requestLocationUpdatesIfNeeded()
        }
    }

    private fun stopGpsUpdates() {
        if (!isReceivingGpsUpdates) return
        runCatching { locationManager.removeUpdates(gpsLocationListener) }
        isReceivingGpsUpdates = false
    }

    private fun publishLocationIfUsable(location: Location, source: String) {
        val accuracy = location.accuracy.takeIf { location.hasAccuracy() && it.isFinite() }
        val now = System.currentTimeMillis()
        if (!NearbyLocationPolicy.isUsable(location.time, accuracy, now)) return
        android.util.Log.d(
            "MelodyLocation",
            "usable fix source=$source accuracy=${accuracy?.toInt()}m " +
                "age=${now - location.time}ms interactive=$interactiveMode",
        )
        preferences.edit()
            .putLong(KEY_LAST_LOCATION_UPDATE_EPOCH_MS, now)
            .putString(KEY_LAST_LOCATION_ACCURACY_QUALITY, accuracyQuality(location))
            .apply()
        sendBroadcast(
            Intent(ACTION_LOCATION_FIX)
                .setPackage(packageName)
                .putExtra(EXTRA_LATITUDE, location.latitude)
                .putExtra(EXTRA_LONGITUDE, location.longitude)
                .putExtra(EXTRA_ACCURACY_METERS, accuracy ?: Float.NaN)
                .putExtra(EXTRA_LOCATION_TIME_EPOCH_MS, location.time)
        )
    }

    private fun accuracyQuality(location: Location): String {
        if (!location.hasAccuracy()) return ACCURACY_UNKNOWN
        return when {
            location.accuracy <= 10f -> ACCURACY_EXCELLENT
            location.accuracy <= 30f -> ACCURACY_GOOD
            location.accuracy <= 100f -> ACCURACY_FAIR
            else -> ACCURACY_POOR
        }
    }

    private fun publishSharingState(active: Boolean) {
        sendBroadcast(
            Intent(ACTION_SHARING_STATE_CHANGED)
                .setPackage(packageName)
                .putExtra(EXTRA_SHARING_ACTIVE, active)
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            NOTIFICATION_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shows when nearby sharing is active"
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, SharingForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            STOP_REQUEST_CODE,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            CONTENT_REQUEST_CODE,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return builder
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("Melody Bubble 주변 공유 중")
            .setContentText("현재 음악과 대략적인 위치가 주변에 공유돼요")
            .setContentIntent(contentPendingIntent)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setPriority(Notification.PRIORITY_LOW)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setShowWhen(false)
            .addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "공유 중지",
                    stopPendingIntent,
                ).build(),
            )
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
                }
            }
            .build()
    }

    companion object {
        const val PREFERENCES_NAME = "melody_bubble_sharing_state"
        const val KEY_SHARING_ACTIVE = "sharing_active"
        const val KEY_LAST_LOCATION_UPDATE_EPOCH_MS = "last_location_update_epoch_ms"
        const val KEY_LAST_LOCATION_ACCURACY_QUALITY = "last_location_accuracy_quality"

        const val ACTION_SHARING_STATE_CHANGED =
            "com.example.myapplication.service.action.SHARING_STATE_CHANGED"
        const val ACTION_LOCATION_FIX =
            "com.example.myapplication.service.action.LOCATION_FIX"
        const val EXTRA_SHARING_ACTIVE = "sharing_active"
        const val EXTRA_LATITUDE = "latitude"
        const val EXTRA_LONGITUDE = "longitude"
        const val EXTRA_ACCURACY_METERS = "accuracy_meters"
        const val EXTRA_LOCATION_TIME_EPOCH_MS = "location_time_epoch_ms"

        const val ACCURACY_EXCELLENT = "excellent"
        const val ACCURACY_GOOD = "good"
        const val ACCURACY_FAIR = "fair"
        const val ACCURACY_POOR = "poor"
        const val ACCURACY_UNKNOWN = "unknown"

        private const val ACTION_START =
            "com.example.myapplication.service.action.START_SHARING"
        private const val ACTION_STOP =
            "com.example.myapplication.service.action.STOP_SHARING"
        private const val ACTION_UPDATE_PROFILE =
            "com.example.myapplication.service.action.UPDATE_LOCATION_PROFILE"
        private const val NOTIFICATION_CHANNEL_ID = "nearby_sharing"
        private const val NOTIFICATION_CHANNEL_NAME = "Nearby sharing"
        private const val NOTIFICATION_ID = 2101
        private const val CONTENT_REQUEST_CODE = 2101
        private const val STOP_REQUEST_CODE = 2102
        private const val EXTRA_INTERACTIVE = "interactive"

        /**
         * Starts sharing only when location permission is already granted. Call this directly from
         * a visible user interaction so Android 14+'s while-in-use FGS rule is satisfied.
         */
        fun start(context: Context): Boolean {
            if (!hasLocationPermission(context)) return false
            val intent = Intent(context, SharingForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_INTERACTIVE, true)
            }
            return try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                true
            } catch (_: RuntimeException) {
                false
            }
        }

        /** Stops sharing from an explicit UI action. */
        fun stop(context: Context): Boolean =
            context.stopService(Intent(context, SharingForegroundService::class.java))

        fun setInteractive(context: Context, interactive: Boolean): Boolean {
            if (!isSharingActive(context)) return false
            return runCatching {
                context.startService(Intent(context, SharingForegroundService::class.java).apply {
                    action = ACTION_UPDATE_PROFILE
                    putExtra(EXTRA_INTERACTIVE, interactive)
                })
                true
            }.getOrDefault(false)
        }

        fun hasLocationPermission(context: Context): Boolean =
            context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

        private fun hasFineLocationPermission(context: Context): Boolean =
            context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

        fun isSharingActive(context: Context): Boolean = context.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        ).getBoolean(KEY_SHARING_ACTIVE, false)
    }
}
