package com.example.assistive_touch_android

import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import android.provider.Settings
import android.net.Uri
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.os.Build
import android.content.ComponentName
import android.text.TextUtils

class MainActivity: FlutterActivity() {
    private val CHANNEL = "assistive_touch_channel"
    private val REQUEST_OVERLAY_PERMISSION = 1234
    private var overlayReceiver: BroadcastReceiver? = null
    private var channel: MethodChannel? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        channel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)

        channel?.setMethodCallHandler { call, result ->
            when (call.method) {
                "startService" -> {
                    if (!Settings.canDrawOverlays(this)) {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName")
                        )
                        startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION)
                        result.error("PERMISSION_DENIED", "Izin Overlay diperlukan", null)
                    } else {
                        startFloatingService()
                        
                        if (!isAccessibilityServiceEnabled(this, FloatingService::class.java)) {
                            val accessibilityIntent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            startActivity(accessibilityIntent)
                        }
                        result.success(true)
                    }
                }
                "stopService" -> {
                    sendCommandToService("ACTION_STOP_SERVICE") 
                    result.success(null)
                }
                "executeAction" -> {
                    val actionLabel = call.arguments as String
                    actionLabel?.let { sendCommandToService("ACTION_EXECUTE", it) }
                    result.success(null)
                }
                "checkServiceStatus" -> {
                    val isRunning = Settings.canDrawOverlays(this) && 
                                    isAccessibilityServiceEnabled(this, FloatingService::class.java)
                    result.success(isRunning)
                }
                else -> result.notImplemented()
            }
        }

        setupBroadcastReceiver()
    }

    private fun startFloatingService() {
        val intent = Intent(this, FloatingService::class.java)
                        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun sendCommandToService(action: String, command: String? = null) {
        val intent = Intent(this, FloatingService::class.java).apply {
            this.action = action
            command?.let { putExtra("command", it) }
        }
        startService(intent)
    }

    private fun setupBroadcastReceiver() {
        overlayReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "ASSISTIVE_TOUCH_ACTION") {
                    val actionType = intent?.getStringExtra("action")
                    
                    if (actionType == "serviceStatus") {
                        val isRunning = intent.getBooleanExtra("status", false)
                        channel?.invokeMethod("serviceStatus", isRunning)
                    }
                }
            }
        }

        val filter = IntentFilter("ASSISTIVE_TOUCH_ACTION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(overlayReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(overlayReceiver, filter)
        }
    }

    private fun isAccessibilityServiceEnabled(context: Context, service: Class<*>): Boolean {
        val expectedId = ComponentName(context, service).flattenToString()
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )

        if (enabledServices == null) return false
        
        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServices)
        while (colonSplitter.hasNext()) {
            if (colonSplitter.next().equals(expectedId, ignoreCase = true)) return true
        }
        return false
    }

    override fun onDestroy() {
        overlayReceiver?.let { unregisterReceiver(it) }
        super.onDestroy()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_OVERLAY_PERMISSION) {
            if (Settings.canDrawOverlays(this)) {
                startFloatingService()
            }
        }
    }
}
