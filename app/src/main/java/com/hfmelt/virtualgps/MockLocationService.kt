package com.hfmelt.virtualgps

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.os.*

class MockLocationService : Service() {
    companion object {
        const val STOP = "com.hfmelt.virtualgps.STOP"
        @Volatile var running = false
            private set
        @Volatile var lastError: String? = null
            private set
        @Volatile var activePoint: Coordinates? = null
            private set
    }
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var manager: LocationManager
    private var providerAdded = false
    private val publish = object : Runnable {
        override fun run() {
            val point = activePoint ?: return
            try {
                val location = Location(LocationManager.GPS_PROVIDER).apply {
                    latitude = point.latitude
                    longitude = point.longitude
                    accuracy = 3f
                    altitude = 0.0
                    speed = 0f
                    bearing = 0f
                    time = System.currentTimeMillis()
                    elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
                }
                manager.setTestProviderLocation(LocationManager.GPS_PROVIDER, location)
                handler.postDelayed(this, 1000)
            } catch (e: Exception) { fail(e) }
        }
    }
    override fun onCreate() {
        super.onCreate()
        manager = getSystemService(LocationManager::class.java)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("simulation", "محاكاة الموقع", NotificationManager.IMPORTANCE_LOW)
        )
    }
    override fun onBind(intent: Intent?) = null
    @Suppress("DEPRECATION")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP || intent == null) { stopSelf(); return START_NOT_STICKY }
        lastError = null
        try {
            check(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                "امنح إذن الموقع الدقيق ثم حاول مجدداً"
            }
            val point = Coordinates(intent.getDoubleExtra("lat", Double.NaN), intent.getDoubleExtra("lon", Double.NaN))
            val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val stop = PendingIntent.getService(this, 1, Intent(this, MockLocationService::class.java).setAction(STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val notification = Notification.Builder(this, "simulation")
                .setSmallIcon(R.drawable.ic_location)
                .setContentTitle("VirtualGPS — المحاكاة فعّالة")
                .setContentText("${point.latitude}, ${point.longitude}")
                .setContentIntent(open).setOngoing(true)
                .addAction(Notification.Action.Builder(null, "إيقاف", stop).build()).build()
            if (Build.VERSION.SDK_INT >= 29) startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            else startForeground(1, notification)
            if (!providerAdded) {
                manager.addTestProvider(LocationManager.GPS_PROVIDER, false, true, false, false, true, true, true, Criteria.POWER_LOW, Criteria.ACCURACY_FINE)
                providerAdded = true
                manager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true)
            }
            activePoint = point
            running = true
            handler.removeCallbacks(publish)
            publish.run()
        } catch (e: Exception) { fail(e) }
        return START_NOT_STICKY
    }
    private fun fail(e: Exception) {
        lastError = if (e is SecurityException) "اختر VirtualGPS كتطبيق الموقع الوهمي من خيارات المطوّر، وامنحه إذن الموقع الدقيق."
            else "تعذّر تشغيل المحاكاة: ${e.message ?: e.javaClass.simpleName}"
        running = false
        stopSelf()
    }
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (providerAdded) runCatching { manager.removeTestProvider(LocationManager.GPS_PROVIDER) }
        providerAdded = false
        running = false
        activePoint = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}
