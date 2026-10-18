package com.hfmelt.virtualgps

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.*
import android.provider.Settings
import android.text.InputType
import android.view.*
import android.widget.*
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.CopyrightOverlay
import java.util.Locale
import java.io.File

class MainActivity : Activity() {
    private lateinit var map: MapView
    private lateinit var marker: Marker
    private lateinit var lat: EditText
    private lateinit var lon: EditText
    private lateinit var status: TextView
    private lateinit var store: LocationStore
    private lateinit var point: Coordinates
    private var notificationPrompted = false
    private val handler = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() {
            status.text = MockLocationService.lastError ?: if (MockLocationService.running) {
                val p = MockLocationService.activePoint
                "● المحاكاة فعّالة: ${p?.latitude}, ${p?.longitude}"
            } else "○ المحاكاة متوقفة"
            handler.postDelayed(this, 800)
        }
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().userAgentValue = "com.hfmelt.virtualgps/1.0 (Android; https://github.com/hfmelt-blip/VirtualGPS-Android)"
        Configuration.getInstance().osmdroidBasePath = File(filesDir, "osmdroid").apply { mkdirs() }
        Configuration.getInstance().osmdroidTileCache = File(cacheDir, "tiles").apply { mkdirs() }
        store = LocationStore(this)
        point = store.selected()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setBackgroundColor(Color.rgb(244, 248, 250))
            setPadding(dp(12), 0, dp(12), 0)
            // Android 15 edge-to-edge: keep controls clear of system bars.
            setOnApplyWindowInsetsListener { v, insets ->
                v.setPadding(dp(12), insets.systemWindowInsetTop, dp(12), insets.systemWindowInsetBottom)
                insets
            }
        }
        setContentView(root)
        root.requestApplyInsets()
        root.addView(TextView(this).apply {
            text = "VirtualGPS  |  الموقع الافتراضي"
            textSize = 22f; setTextColor(Color.rgb(18, 56, 73)); setPadding(0, dp(10), 0, dp(4))
        })
        root.addView(TextView(this).apply { text = "اضغط على الخريطة أو أدخل الإحداثيات. المواقع المحاكية قابلة للاكتشاف."; textSize = 13f })
        map = MapView(this).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(14.0)
        }
        root.addView(map, LinearLayout.LayoutParams(-1, 0, 1f))
        marker = Marker(map).apply { setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM); title = "الموقع المختار" }
        map.overlays.add(MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean { select(Coordinates(p.latitude, p.longitude)); return true }
            override fun longPressHelper(p: GeoPoint): Boolean { select(Coordinates(p.latitude, p.longitude)); return true }
        }))
        map.overlays.add(marker)
        map.overlays.add(CopyrightOverlay(this).apply { setCopyrightNotice("© OpenStreetMap contributors") })
        // Scrollable controls remain accessible with larger fonts and small screens.
        val scroll = ScrollView(this)
        val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(panel)
        root.addView(scroll, LinearLayout.LayoutParams(-1, dp(270)))
        val coords = LinearLayout(this)
        lat = coordinateField("خط العرض")
        lon = coordinateField("خط الطول")
        coords.addView(lat, LinearLayout.LayoutParams(0, -2, 1f))
        coords.addView(lon, LinearLayout.LayoutParams(0, -2, 1f))
        panel.addView(coords)
        row(panel, "عرض الإحداثيات" to { readPoint()?.let { select(it) } }, "حفظ بالمفضلة" to { saveFavorite() })
        row(panel, "المفضلة" to { showFavorites() }, "إعدادات المطوّر" to { openDeveloperSettings() })
        row(panel, "بدء / تحديث المحاكاة" to { startSimulation() }, "إيقاف المحاكاة" to { stopService(Intent(this, MockLocationService::class.java)) })
        status = TextView(this).apply { textSize = 14f; setTextColor(Color.rgb(8, 100, 115)); setPadding(0, dp(4), 0, dp(8)); accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }
        panel.addView(status)
        select(point)
    }
    private fun coordinateField(label: String) = EditText(this).apply {
        hint = label; contentDescription = label
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
        textDirection = View.TEXT_DIRECTION_LTR
        isSingleLine = true; textSize = 15f
    }
    private fun row(parent: LinearLayout, vararg items: Pair<String, () -> Unit>) {
        val row = LinearLayout(this)
        items.forEach { (label, action) ->
            row.addView(Button(this).apply { text = label; textSize = 13f; setOnClickListener { action() } }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        parent.addView(row)
    }
    private fun select(p: Coordinates) {
        point = p; store.select(p)
        lat.setText(String.format(Locale.US, "%.6f", p.latitude))
        lon.setText(String.format(Locale.US, "%.6f", p.longitude))
        marker.position = GeoPoint(p.latitude, p.longitude)
        marker.snippet = "${p.latitude}, ${p.longitude}"
        map.controller.setCenter(marker.position); map.invalidate()
    }
    private fun readPoint(): Coordinates? = try { Coordinates.parse(lat.text.toString(), lon.text.toString()) }
        catch (e: IllegalArgumentException) { Toast.makeText(this, e.message, Toast.LENGTH_LONG).show(); null }
    private fun saveFavorite() {
        val p = readPoint() ?: return
        val name = EditText(this).apply { hint = "اسم الموقع"; maxLines = 1 }
        val dialog = AlertDialog.Builder(this).setTitle("حفظ موقع مفضل").setView(name)
            .setPositiveButton("حفظ", null).setNegativeButton("إلغاء", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val text = name.text.toString().trim()
                if (text.isEmpty()) name.error = "أدخل اسماً للموقع"
                else { store.save(store.favorites() + Favorite(text.take(80), p)); select(p); dialog.dismiss() }
            }
        }
        dialog.show()
    }
    private fun showFavorites() {
        val items = store.favorites()
        if (items.isEmpty()) { Toast.makeText(this, "لا توجد مواقع محفوظة بعد", Toast.LENGTH_SHORT).show(); return }
        AlertDialog.Builder(this).setTitle("المواقع المفضلة").setItems(items.map { it.name }.toTypedArray()) { _, index ->
            val item = items[index]
            AlertDialog.Builder(this).setTitle(item.name).setMessage("${item.coordinates.latitude}, ${item.coordinates.longitude}")
                .setPositiveButton("اختيار") { _, _ -> select(item.coordinates) }
                .setNeutralButton("حذف") { _, _ -> store.save(items.filterIndexed { i, _ -> i != index }) }
                .setNegativeButton("إلغاء", null).show()
        }.setNegativeButton("إغلاق", null).show()
    }
    private fun openDeveloperSettings() {
        runCatching { startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) }
            .onFailure { Toast.makeText(this, "فعّل خيارات المطوّر من إعدادات الهاتف أولاً", Toast.LENGTH_LONG).show() }
    }
    private fun startSimulation() {
        val p = readPoint() ?: return
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 10)
            Toast.makeText(this, "اختر الموقع الدقيق، ثم اضغط بدء مجدداً", Toast.LENGTH_LONG).show()
            return
        }
        if (Build.VERSION.SDK_INT >= 33 && !notificationPrompted && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPrompted = true
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 11)
            Toast.makeText(this, "بعد اختيار إذن الإشعارات، اضغط بدء مجدداً", Toast.LENGTH_LONG).show()
            return
        }
        select(p)
        try {
            startForegroundService(Intent(this, MockLocationService::class.java).putExtra("lat", p.latitude).putExtra("lon", p.longitude))
        } catch (e: Exception) { Toast.makeText(this, "تعذّر بدء الخدمة: ${e.message}", Toast.LENGTH_LONG).show() }
    }
    override fun onResume() { super.onResume(); map.onResume(); handler.post(refresh) }
    override fun onPause() { handler.removeCallbacks(refresh); map.onPause(); super.onPause() }
    override fun onDestroy() { handler.removeCallbacksAndMessages(null); map.onDetach(); super.onDestroy() }
}
