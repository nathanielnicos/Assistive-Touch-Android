package com.example.assistive_touch_android

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ImageView
import android.widget.TextView
import android.view.Gravity
import android.graphics.Color
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import android.content.res.Resources
import android.media.AudioManager
import android.content.Context
import android.provider.Settings

class FloatingService : android.accessibilityservice.AccessibilityService() {
    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var menuView: View
    private lateinit var floatingParams: WindowManager.LayoutParams
    private lateinit var menuParams: WindowManager.LayoutParams
    private var isAccessibilityConnected = false

    override fun onCreate() {
        super.onCreate()

        setupNotification()
        setupWindowManager()
    }

    private fun setupNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "assistive_touch_channel",
                "Assistive Touch",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
        
        val notification = NotificationCompat.Builder(this, "assistive_touch_channel")
            .setContentTitle("Assistive Touch Android")
            .setContentText("Floating button berjalan")
            .setSmallIcon(R.drawable.ic_touch) // pastikan ada icon
            .build()

        startForeground(1, notification)
    }

    private fun setupWindowManager() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    private fun setupFloatingButton() {
        floatingParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        floatingParams.gravity = Gravity.TOP or Gravity.START
        floatingParams.y = 500

        floatingView = LayoutInflater.from(this).inflate(R.layout.assistive_touch, null)

        val imageView = floatingView.findViewById<ImageView>(R.id.assistive_touch)
        imageView.alpha = 0.4f

        val screenWidth = resources.displayMetrics.widthPixels
        val edgeMargin = 20   // jarak dari pinggir layar

        // Atur posisi awal di kanan dengan margin setelah ukuran view valid
        floatingView.post {
            floatingParams.x = screenWidth - floatingView.width - edgeMargin
            windowManager.updateViewLayout(floatingView, floatingParams)
        }

        floatingView.setOnTouchListener { _, event ->
            handleFloatingTouch(event, imageView, screenWidth, edgeMargin)
        }
    }

    private var initialX: Int = 0
    private var initialY: Int = 0
    private var initialTouchX: Float = 0f
    private var initialTouchY: Float = 0f

    private fun handleFloatingTouch(
        event: MotionEvent,
        imageView: ImageView,
        screenWidth: Int,
        edgeMargin: Int
    ) : Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialX = floatingParams.x
                initialY = floatingParams.y
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                
                imageView.animate().alpha(1.0f).setDuration(100).start()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                floatingParams.x = initialX + (event.rawX - initialTouchX).toInt()
                floatingParams.y = initialY + (event.rawY - initialTouchY).toInt()
                windowManager.updateViewLayout(floatingView, floatingParams)
                return true
            }
            MotionEvent.ACTION_UP -> {
                val deltaX = (event.rawX - initialTouchX).toInt()
                val deltaY = (event.rawY - initialTouchY).toInt()
                if (Math.abs(deltaX) < 10 && Math.abs(deltaY) < 10) {
                    if (menuView.parent == null) {
                        windowManager.removeView(floatingView)
                        windowManager.addView(menuView, menuParams)
                    }
                }

                val centerX = floatingParams.x + (floatingView.width / 2)
                if (centerX < screenWidth / 2) {
                    floatingParams.x = edgeMargin // snap kiri dengan jarak
                } else {
                    floatingParams.x = screenWidth - floatingView.width - edgeMargin // snap kanan dengan jarak
                }
                windowManager.updateViewLayout(floatingView, floatingParams)
                imageView.animate().alpha(0.4f).setDuration(4000).start()
                return true
            }
        }
        return false
    }

    private fun setupMenuOverlay() {
        menuView = LayoutInflater.from(this).inflate(R.layout.assistive_touch_menu, null)
        val menuContainer = menuView.findViewById<FrameLayout>(R.id.assistive_touch_menu)

        val sizeDp = 280
        val sizePx = (sizeDp * Resources.getSystem().displayMetrics.density).toInt()

        menuParams = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
        )

        menuParams.gravity = Gravity.CENTER

        val actions = listOf("Volume Up", "Lock", "Control Center", "Home", "Volume Down", "Power", "Recents", "Screenshot")
        val radius = sizePx / 3
        val centerX = sizePx / 2
        val centerY = sizePx / 2
        val angleStep = 360f / actions.size
        
        actions.forEachIndexed { index, label ->
            val itemContainer = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER

                layoutParams = FrameLayout.LayoutParams(
                    (80 * Resources.getSystem().displayMetrics.density).toInt(),
                    (80 * Resources.getSystem().displayMetrics.density).toInt(),
                )
            }

            itemContainer.setOnClickListener {
                executeAction(label) // Jalankan aksi sistem
            }

            val iconView = ImageView(this).apply {
                layoutParams = FrameLayout.LayoutParams(
                    (40 * Resources.getSystem().displayMetrics.density).toInt(),
                    (40 * Resources.getSystem().displayMetrics.density).toInt(),
                )

                setImageResource(when(label) {
                    "Lock" -> R.drawable.ic_lock
                    "Screenshot" -> R.drawable.ic_screenshot
                    "Volume Up" -> R.drawable.ic_volume_up
                    "Volume Down" -> R.drawable.ic_volume_down
                    "Power" -> R.drawable.ic_power
                    "Home" -> R.drawable.ic_home
                    "Control Center" -> R.drawable.ic_control_center
                    "Recents" -> R.drawable.ic_recents
                    else -> android.R.drawable.ic_menu_info_details
                })

                setColorFilter(Color.WHITE)
            }

            val textView = TextView(this).apply {
                text = label
                textSize = 12f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
            }

            itemContainer.addView(iconView)
            itemContainer.addView(textView)

            // hitung posisi polar
            val angle = Math.toRadians((angleStep * index - 90).toDouble())

            itemContainer.post {
                val containerWidth = itemContainer.width
                val containerHeight = itemContainer.height

                val x = (centerX + radius * Math.cos(angle)).toInt() - (containerWidth / 2)
                val y = (centerY + radius * Math.sin(angle)).toInt() - (containerHeight / 2)

                val params = itemContainer.layoutParams as FrameLayout.LayoutParams
                params.leftMargin = x
                params.topMargin = y
                params.gravity = Gravity.TOP or Gravity.START
                itemContainer.layoutParams = params
            }
        
            menuContainer.addView(itemContainer)
        }

        menuView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_OUTSIDE) {
                handleStopMenu()
                true
            } else {
                false
            }
        }
    }

    private fun executeAction(label: String) {
        when (label.lowercase().trim()) {
            "screenshot" -> {
                handleStopMenu()
                
                if (::floatingView.isInitialized && floatingView.parent != null) {
                    floatingView.visibility = View.GONE
                }
                
                floatingView.postDelayed({
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
                    }
                    floatingView.postDelayed({
                        floatingView.visibility = View.VISIBLE
                    }, 500)
                }, 500)
            }
            "lock" -> {
                handleStopMenu()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
                }
            }
            "volume up" -> {
                val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
            }
            "volume down" -> {
                val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
            }
            "power" -> {
                handleStopMenu()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
                }
            }
            "home" -> {
                handleStopMenu()
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
            "control center" -> {
                handleStopMenu()
                performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
            }
            "recents" -> {
                handleStopMenu()
                performGlobalAction(GLOBAL_ACTION_RECENTS)
            }
        }
    }

    private fun handleStopMenu() {
        try {
            if (::menuView.isInitialized && menuView.parent != null) {
                windowManager.removeView(menuView)
            }
            if (::floatingView.isInitialized && floatingView.parent == null) {
                windowManager.addView(floatingView, floatingParams)
            }
        } catch (e: Exception) {
            android.util.Log.e("FloatingService", "Gagal menutup menu: ${e.message}")
        }
    }

    override fun onDestroy() {
        isAccessibilityConnected = false
        super.onDestroy()
        // Kirim sinyal ke MainActivity bahwa service sudah MATI
        val intent = Intent("ASSISTIVE_TOUCH_ACTION")
        intent.putExtra("action", "serviceStatus")
        intent.putExtra("status", false)
        sendBroadcast(intent)

        if (::floatingView.isInitialized && floatingView.parent != null) {
            windowManager.removeView(floatingView)
        }
        if (::menuView.isInitialized && menuView.parent != null) {
            windowManager.removeView(menuView)
        }
    }

    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "ACTION_STOP_SERVICE" -> {
                handleStopServiceUI()
            }
            "ACTION_EXECUTE" -> {
                val command = intent.getStringExtra("command")
                if (command != null) {
                    executeAction(command)
                }
            }
            else -> {
                if (isAccessibilityConnected) {
                    checkAndShowFloatingButton()
                } else {
                    sendStatusToFlutter(false)
                }
            }
        }
        return START_STICKY
    }

    private fun handleStopServiceUI() {
        sendStatusToFlutter(false)

        if (::floatingView.isInitialized && floatingView.parent != null) {
            windowManager.removeView(floatingView)
        }
        
        if (::menuView.isInitialized && menuView.parent != null) {
            windowManager.removeView(menuView)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isAccessibilityConnected = true
        checkAndShowFloatingButton()
    }

    private fun checkAndShowFloatingButton() {
        if (Settings.canDrawOverlays(this) && isServiceActuallyRunning()) {
            if (!::floatingView.isInitialized ) {
                setupFloatingButton()
                setupMenuOverlay()
            }
            showFloatingButton()
            sendStatusToFlutter(true)
        } else {
            handleStopServiceUI()
            sendStatusToFlutter(false)
        }
    }

    private fun isServiceActuallyRunning(): Boolean {
        return isAccessibilityConnected
    }

    private fun sendStatusToFlutter(isActive: Boolean) {
        val intent = Intent("ASSISTIVE_TOUCH_ACTION")
        intent.putExtra("action", "serviceStatus") // Sesuaikan key-nya
        intent.putExtra("status", isActive)
        sendBroadcast(intent)
    }

    private fun showFloatingButton() {
        if (!::floatingView.isInitialized || !isAccessibilityConnected) return
        
        try {
            val isFloatingVisible = floatingView.parent != null
            val isMenuVisible = if (::menuView.isInitialized) menuView.parent != null else false
            
            if (!isFloatingVisible && !isMenuVisible) {
                windowManager.addView(floatingView, floatingParams)
            }
        } catch (e: WindowManager.BadTokenException) {
            android.util.Log.e("FloatingService", "Token tidak valid: ${e.message}")
        } catch (e: Exception) {
            android.util.Log.e("FloatingService", "Gagal menambah view: ${e.message}")
            sendStatusToFlutter(false)
        }
    }
}
