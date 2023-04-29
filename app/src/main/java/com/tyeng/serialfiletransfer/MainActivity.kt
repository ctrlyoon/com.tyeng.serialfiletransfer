package com.tyeng.serialfiletransfer

import SerialConnectionService
import android.app.*
import android.content.*
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.*
import android.util.Log
import android.view.*
import android.webkit.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.tyeng.serialfiletransfer.usbserial.driver.UsbSerialPort
import com.tyeng.serialfiletransfer.usbserial.driver.UsbSerialProber
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
  companion object {
    val TAG = "mike_" + Thread.currentThread().stackTrace[2].className + " "
    private val BAUD_RATE = 115200
  }

  private val connectionReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
      if (intent?.action == SerialConnectionService.ACTION_CONNECTION_ESTABLISHED) {
        // The serial connection is established, enable the buttons here
      }
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)
    LocalBroadcastManager.getInstance(this).registerReceiver(connectionReceiver, IntentFilter(SerialConnectionService.ACTION_CONNECTION_ESTABLISHED))
    Intent(this, SerialConnectionService::class.java).also { intent ->
      ContextCompat.startForegroundService(this, intent)
    }
  }

  fun sendCommandJson(view: View) {
    val internalStoragePath = filesDir.absolutePath
    val filePath = "$internalStoragePath/command.json"

    val file = File(filePath)
    if (!file.exists()) {
      createCommandJsonFile(file)
    }

    // Retrieve the usbSerialPort instance from the SerialConnectionService
    val usbSerialPort = SerialConnectionService.usbSerialPort

    if (usbSerialPort != null) {
      sendFile(usbSerialPort, File(filePath))
    } else {
      Toast.makeText(this, "Serial connection not established", Toast.LENGTH_SHORT).show()
    }
  }



  override fun onDestroy() {
    super.onDestroy()
    LocalBroadcastManager.getInstance(this).unregisterReceiver(connectionReceiver)
  }

  fun sendGreetingsMp3(view: View) {
    // Add your code to send greetings.mp3 here
  }

  private fun createCommandJsonFile(file: File) {
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