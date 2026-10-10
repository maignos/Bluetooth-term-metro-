package com.example.termometrobluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.UUID

private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

class MainActivity : ComponentActivity() {
    private val adapter: BluetoothAdapter? by lazy { BluetoothAdapter.getDefaultAdapter() }
    private var socket: BluetoothSocket? = null
    private var readerThread: Thread? = null
    private val permissionRequest = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        askBluetoothPermission()
        setContent { AppScreen() }
    }

    private fun askBluetoothPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            permissionRequest.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }

    private fun hasPermission() = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun getPairedDevices(): List<BluetoothDevice> =
        if (hasPermission()) adapter?.bondedDevices?.sortedBy { it.name ?: it.address } ?: emptyList() else emptyList()

    @SuppressLint("MissingPermission")
    private fun connect(device: BluetoothDevice, status: (String) -> Unit, received: (String) -> Unit) {
        Thread {
            try {
                adapter?.cancelDiscovery()
                val s = device.createRfcommSocketToServiceRecord(SPP_UUID)
                s.connect()
                socket = s
                runOnUiThread { status("Conectado a ${device.name ?: device.address}") }
                readerThread = Thread {
                    try {
                        val reader = BufferedReader(InputStreamReader(s.inputStream, Charsets.UTF_8))
                        while (!Thread.currentThread().isInterrupted) {
                            val line = reader.readLine() ?: break
                            runOnUiThread { received(line) }
                        }
                    } catch (e: Exception) {
                        runOnUiThread { status("Conexión finalizada: ${e.message ?: "sin datos"}") }
                    }
                }.also { it.start() }
            } catch (e: Exception) {
                runOnUiThread { status("No se pudo conectar. Revisa el emparejamiento y permisos.") }
            }
        }.start()
    }

    private fun sendMessage(message: String) {
        Thread {
            try {
                socket?.outputStream?.apply {
                    write((message + "\n").toByteArray(Charsets.UTF_8))
                    flush()
                }
            } catch (_: Exception) { }
        }.start()
    }

    private fun disconnect(status: (String) -> Unit) {
        try { readerThread?.interrupt() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
        socket = null
        runOnUiThread { status("Desconectado") }
    }

    @SuppressLint("MissingPermission")
    @Composable
    private fun AppScreen() {
        var devices by remember { mutableStateOf(emptyList<BluetoothDevice>()) }
        var selected by remember { mutableStateOf("") }
        var status by remember { mutableStateOf("Empareja el HC-05/HC-06 y búscalo aquí") }
        var connected by remember { mutableStateOf(false) }
        var latest by remember { mutableStateOf("-- °C") }
        var message by remember { mutableStateOf("") }
        val logs = remember { mutableStateListOf<String>() }

        MaterialTheme(colorScheme = darkColorScheme(
            primary = Color(0xFF1688FF), secondary = Color(0xFF42E8BE),
            background = Color(0xFF0D1725), surface = Color(0xFF142337)
        )) {
            Surface(Modifier.fillMaxSize(), color = Color(0xFF0D1725)) {
                Column(Modifier.fillMaxSize().padding(16.dp)) {
                    Text("Termómetro Bluetooth", color = Color.White, fontSize = 25.sp)
                    Text("Lecturas del Arduino Uno por HC-05 / HC-06", color = Color(0xFF9BB7D8))
                    Spacer(Modifier.height(14.dp))
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF142337))) {
                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                            Button(onClick = {
                                if (hasPermission()) {
                                    devices = getPairedDevices()
                                    if (devices.isEmpty()) status = "No hay dispositivos emparejados. Hazlo desde Ajustes > Bluetooth."
                                } else askBluetoothPermission()
                            }) { Text("Buscar emparejados") }
                            devices.forEach { d ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected == d.address, { selected = d.address })
                                    Text("${d.name ?: "Bluetooth"} (${d.address})", color = Color.White, fontSize = 12.sp)
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(enabled = !connected && selected.isNotBlank(), onClick = {
                                    devices.firstOrNull { it.address == selected }?.let { d ->
                                        connect(d, { status = it }, { line ->
                                            val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                                            logs.add("$time  $line")
                                            if (logs.size > 300) logs.removeAt(0)
                                            val regex = Regex("(-?\\d+(?:\\.\\d+)?)\\s*C", RegexOption.IGNORE_CASE)
                                            regex.find(line)?.let { latest = "${it.groupValues[1]} °C" }
                                        })
                                        connected = true
                                    }
                                }) { Text("Conectar") }
                                OutlinedButton(enabled = connected, onClick = {
                                    disconnect { status = it }; connected = false
                                }) { Text("Desconectar") }
                            }
                            Text(status, color = if (connected) Color(0xFF42E8BE) else Color(0xFFB6C7DB), fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF142337))) {
                        Column(Modifier.fillMaxWidth().padding(14.dp)) {
                            Text("Temperatura actual", color = Color(0xFF9BB7D8))
                            Text(latest, color = Color(0xFF42E8BE), fontSize = 34.sp)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Mensajes recibidos", color = Color.White, fontSize = 18.sp)
                    Card(Modifier.weight(1f).fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF08111D))) {
                        if (logs.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Aquí aparecerán los datos recibidos…", color = Color(0xFF8FA6C1))
                            }
                        } else {
                            LazyColumn(Modifier.fillMaxSize().padding(10.dp)) {
                                items(logs) { line ->
                                    Text(line, color = Color(0xFF42E8BE), fontSize = 13.sp, modifier = Modifier.padding(vertical = 3.dp))
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = message, onValueChange = { message = it },
                            modifier = Modifier.weight(1f), singleLine = true, label = { Text("Mensaje opcional") })
                        Button(enabled = connected && message.isNotBlank(), onClick = {
                            sendMessage(message)
                            logs.add("TX  $message")
                            message = ""
                        }) { Text("Enviar") }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("Bluetooth clásico SPP. Empareja el módulo en los ajustes del teléfono primero.",
                        color = Color(0xFF8FA6C1), fontSize = 10.sp)
                }
            }
        }
    }
}
