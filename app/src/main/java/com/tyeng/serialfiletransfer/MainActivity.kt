package com.tyeng.serialfiletransfer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.tyeng.serialfiletransfer.services.SerialConnectionService
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
  companion object {
    val TAG = "mike_" + Thread.currentThread().stackTrace[2].className + " "
  }
  private lateinit var sendCommandButton: Button
  private val connectionReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
      if (intent?.action == SerialConnectionService.ACTION_CONNECTION_ESTABLISHED) {
        sendCommandButton.isEnabled = true
        Log.i(SerialConnectionService.TAG + Throwable().stackTrace[0].lineNumber, "Serial is initialized")
        sendCommandJson("setTime",SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))
      }
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)
    sendCommandButton = findViewById(R.id.buttonCommandJson)
    LocalBroadcastManager.getInstance(this).registerReceiver(connectionReceiver, IntentFilter(SerialConnectionService.ACTION_CONNECTION_ESTABLISHED))
    Intent(this, SerialConnectionService::class.java).also { intent ->
      ContextCompat.startForegroundService(this, intent)
    }
    createNotificationChannel()
  }

  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channelId = "SerialConnectionService"
      val channelName = "Serial Connection Service"
      val importance = NotificationManager.IMPORTANCE_LOW
      val channel = NotificationChannel(channelId, channelName, importance).apply {
        description = "Foreground service for serial connection"
      }
      val notificationManager: NotificationManager =
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      notificationManager.createNotificationChannel(channel)
    }
  }

  private fun sendCommandJson(command: String, action:String) {
    val json = JSONObject().apply {
      put("command", command)
      put("action", action)
    }
    val jsonString = json.toString()
    val byteArray = jsonString.toByteArray()
    val file = File(this.cacheDir, "command.json")
    FileOutputStream(file).use { outputStream ->
      outputStream.write(byteArray)
    }
    val usbSerialPort = SerialConnectionService.usbSerialPort
    if (usbSerialPort != null) {
      SerialConnectionService.serialPortHelper?.sendFile(usbSerialPort, file)
    } else {
      Toast.makeText(this, "Serial connection not established", Toast.LENGTH_SHORT).show()
    }
  }
  fun sendCommand(view: View) {
    sendCommandJson("test","try")
  }

  override fun onDestroy() {
    super.onDestroy()
    LocalBroadcastManager.getInstance(this).unregisterReceiver(connectionReceiver)
  }

  fun sendGreetingsMp3(view: View) {
    val internalStoragePath = filesDir.absolutePath
    val filePath = "$internalStoragePath/greetings.mp3"

    val file = File(filePath)
    if (!file.exists()) {
      createGreetings(file)
    }

    // Retrieve the usbSerialPort instance from the SerialConnectionService
    val usbSerialPort = SerialConnectionService.usbSerialPort

    if (usbSerialPort != null) {
      SerialConnectionService.serialPortHelper?.sendFile(usbSerialPort, File(filePath))
    } else {
      Toast.makeText(this, "Serial connection not established", Toast.LENGTH_SHORT).show()
    }
  }

  private fun createGreetings(file: File) {
    val currentTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
    val content = """{"commit": "setTime", "action": "$currentTime"}"""

    try {
      FileOutputStream(file).use { outputStream ->
        outputStream.write(content.toByteArray())
      }
    } catch (e: IOException) {
      e.printStackTrace()
    }
  }
}