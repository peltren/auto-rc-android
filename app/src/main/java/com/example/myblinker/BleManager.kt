package com.example.myblinker

import android.Manifest
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import java.util.*

class BleManager(private val context: Context) {

    private var bluetoothGatt: BluetoothGatt? = null
    private var characteristic: BluetoothGattCharacteristic? = null

    private var isConnected = false
    private var isConnecting = false

    private val SERVICE_UUID = UUID.fromString("00001234-0000-1000-8000-00805f9b34fb")
    private val CHAR_UUID = UUID.fromString("00005678-0000-1000-8000-00805f9b34fb")

    private lateinit var scanCallback: ScanCallback

    var connectionListener: ((Boolean) -> Unit)? = null

    @Suppress("MissingPermission")
    fun startScan() {
        val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
        val bluetoothAdapter = bluetoothManager.adapter
        val scanner = bluetoothAdapter.bluetoothLeScanner

        scanCallback = object : ScanCallback() {
            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device

                if (device.name == "ESP32_BLE_LED" && !isConnecting) {
                    isConnecting = true
                    scanner.stopScan(scanCallback)
                    connect(device)
                }
            }
        }

        scanner.startScan(scanCallback)
    }

    @Suppress("MissingPermission")
    private fun connect(device: BluetoothDevice) {
        bluetoothGatt?.close()

        bluetoothGatt = device.connectGatt(
            context,
            false,
            gattCallback,
            BluetoothDevice.TRANSPORT_LE
        )
    }

    @Suppress("MissingPermission")
    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            Log.d("BLE", "STATE CHANGE: $status / $newState")

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                isConnected = true
                Log.d("BLE", "CONECTADO 🔥")

                connectionListener?.invoke(true) // 🟢
                gatt.discoverServices()

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d("BLE", "DESCONECTADO ❌")

                isConnected = false
                isConnecting = false

                connectionListener?.invoke(false) // 🔴

                bluetoothGatt?.close()
                bluetoothGatt = null
                characteristic = null
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val service = gatt.getService(SERVICE_UUID)
            characteristic = service?.getCharacteristic(CHAR_UUID)

            if (characteristic != null) {
                Log.d("BLE", "CARACTERISTICA OK ✅")
            } else {
                Log.d("BLE", "❌ No encontrada")
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    @Suppress("MissingPermission")
    fun send(data: String) {
        if (!isConnected) {
            Log.d("BLE", "❌ No conectado")
            return
        }

        val value = data.toByteArray(Charsets.UTF_8)

        characteristic?.let {
            bluetoothGatt?.writeCharacteristic(
                it,
                value,
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            )
        }
    }

    @Suppress("MissingPermission")
    fun disconnect() {
        bluetoothGatt?.close()
        bluetoothGatt = null
    }
}