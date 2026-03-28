package com.example.myblinker

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.appcompat.app.AppCompatActivity
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    companion object {
        private const val CENTER_VALUE = 512
        /** BLE GATT: un mensaje J,x,y por intervalo como máximo. */
        private const val BLE_SEND_MIN_INTERVAL_MS = 50L
    }

    private lateinit var joystick: JoystickView

    private val bleSendHandler = Handler(Looper.getMainLooper())
    private var pendingBleXY: Pair<Int, Int>? = null
    private var bleFlushScheduled = false
    private var lastBleSendUptime = 0L

    private val bleFlushRunnable = Runnable { flushBleSend() }

    private fun queueBleSend(x: Int, y: Int) {
        pendingBleXY = x to y
        val now = SystemClock.uptimeMillis()
        val elapsed = now - lastBleSendUptime
        if (elapsed >= BLE_SEND_MIN_INTERVAL_MS) {
            bleSendHandler.removeCallbacks(bleFlushRunnable)
            flushBleSend()
        } else if (!bleFlushScheduled) {
            bleFlushScheduled = true
            bleSendHandler.postDelayed(bleFlushRunnable, BLE_SEND_MIN_INTERVAL_MS - elapsed)
        }
    }

    private fun flushBleSend() {
        bleSendHandler.removeCallbacks(bleFlushRunnable)
        pendingBleXY?.let { (x, y) ->
            bleManager.send("J,$x,$y\n")
            lastBleSendUptime = SystemClock.uptimeMillis()
        }
        pendingBleXY = null
        bleFlushScheduled = false
    }

    // Required permissions for BLE functionality
    private val PERMISSIONS = arrayOf(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.ACCESS_FINE_LOCATION
    )

    // Check if all required permissions are granted
    private fun checkPermissions(): Boolean {
        return PERMISSIONS.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    // Request missing permissions
    private fun requestPermissions() {
        ActivityCompat.requestPermissions(this, PERMISSIONS, 1)
    }

    private lateinit var bleManager: BleManager

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bleManager = BleManager(applicationContext)

        val tvStatus = findViewById<TextView>(R.id.tvStatus)
        val tvSliderValue = findViewById<TextView>(R.id.tvSliderValue)
        joystick = findViewById(R.id.joystick)

        // Start scanning if permissions are granted, otherwise request them
        if (!checkPermissions()) {
            requestPermissions()
        } else {
            bleManager.startScan()
        }

        joystick.onPositionChange = { x, y, fromUser ->
            tvSliderValue.text = getString(R.string.joystick_xy_format, x, y)
            if (fromUser) {
                queueBleSend(x, y)
            }
        }

        joystick.onRelease = {
            flushBleSend()
            joystick.snapToCenter(animated = true)
        }

        joystick.onSnapAnimationEnd = {
            bleManager.send("J,$CENTER_VALUE,$CENTER_VALUE\n")
        }

        // Update UI based on connection state
        bleManager.connectionListener = { connected ->
            runOnUiThread {
                if (connected) {
                    tvStatus.text = getString(R.string.status_connected)
                    tvStatus.setTextColor(getColor(android.R.color.holo_green_dark))
                } else {
                    tvStatus.text = getString(R.string.status_disconnected)
                    tvStatus.setTextColor(getColor(android.R.color.holo_red_dark))
                }
            }
        }
    }

    override fun onDestroy() {
        joystick.cancelSnapAnimation()
        bleSendHandler.removeCallbacks(bleFlushRunnable)
        bleManager.disconnect()
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 1) {
            // Start scanning if permissions were granted by the user
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                bleManager.startScan()
            }
        }
    }
}
