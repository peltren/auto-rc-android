package com.example.myblinker

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.switchmaterial.SwitchMaterial

class MainActivity : AppCompatActivity() {

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

        val swLed = findViewById<SwitchMaterial>(R.id.swLed)
        val btnBlink = findViewById<Button>(R.id.btnBlink)
        val btnFade = findViewById<Button>(R.id.btnFade)
        val tvStatus = findViewById<TextView>(R.id.tvStatus)

        // Start scanning if permissions are granted, otherwise request them
        if (!checkPermissions()) {
            requestPermissions()
        } else {
            bleManager.startScan()
        }

        // Handle LED state switch
        swLed.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                bleManager.send("1") // ON command
            } else {
                bleManager.send("0") // OFF command
            }
        }

        // Handle Blink button click
        btnBlink.setOnClickListener { bleManager.send("2") } // BLINK command

        // Handle Fade button click
        btnFade.setOnClickListener { bleManager.send("3") } // FADE command

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
        super.onDestroy()
        bleManager.disconnect()
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
