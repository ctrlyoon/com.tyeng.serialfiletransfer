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

  fun listAttachedUsbDevices(context: Context) {
    val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    val deviceList: HashMap<String, UsbDevice> = usbManager.deviceList

    if (deviceList.isEmpty()) {
      Log.i(TAG + Throwable().stackTrace[0].lineNumber,"No attached USB devices found.")
    } else {
      Log.i(TAG + Throwable().stackTrace[0].lineNumber, "List of attached USB devices:")
      for ((_, device) in deviceList) {
        Log.i(TAG + Throwable().stackTrace[0].lineNumber, "Device name: ${device.deviceName}, Product ID: ${device.productId}, Vendor ID: ${device.vendorId}")
      }
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)
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

    var message: String?
    try {
      // Find all available drivers from attached devices.
      val manager = getSystemService(USB_SERVICE) as UsbManager
      val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager)
      if (availableDrivers.isEmpty()) {
        message = getString(R.string.device_not_found)
      } else {
        // Open a connection to the first available driver.
        val driver = availableDrivers[0]
        val connection = manager.openDevice(driver.device)
        if (connection == null) {
          Log.i(TAG + Throwable().stackTrace[0].lineNumber,"startSerial")
          val mainActivityStartPendingIntent = PendingIntent.getActivity(this,0,Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
          manager.requestPermission(driver.device, mainActivityStartPendingIntent)
          message = "connection is null"
        } else {
          Log.i(TAG + Throwable().stackTrace[0].lineNumber,"startSerial connection")
          val usbSerialPort = driver.ports[0] // Most devices have just one port (port 0)
          usbSerialPort.open(connection)
          usbSerialPort.setParameters(BAUD_RATE,8,UsbSerialPort.STOPBITS_1,UsbSerialPort.PARITY_NONE)
          message = "sending file"
          Log.i(TAG + Throwable().stackTrace[0].lineNumber,"filePath   :" + filePath)
          sendFile(usbSerialPort, File(filePath))
        }
      }
    } catch (ex: Exception) {
      message = getString(R.string.error) + " " + ex.message
      ex.printStackTrace()
    }
    if (message != null) {
      val msg: String = message
      Log.i(TAG + Throwable().stackTrace[0].lineNumber,"message   :" + message)
      Handler(Looper.getMainLooper()).post {Toast.makeText(applicationContext,msg,Toast.LENGTH_SHORT).show()
      }
    }
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

  @Throws(IOException::class)
  private fun sendFile(serialPort: UsbSerialPort, file: File) {
    FileInputStream(file).use { fileInputStream ->
      val buffer = ByteArray(4096)
      var bytesRead: Int

      // Send header: <file_name>|<file_size>
      val header = "${file.name}|${file.length()}"

      Log.i(TAG + Throwable().stackTrace[0].lineNumber, "Sending header: $header")
      serialPort.write(header.toByteArray(), 1000)
      Log.i(TAG + Throwable().stackTrace[0].lineNumber, "header: sent")
      // Send file data
      while (fileInputStream.read(buffer).also { bytesRead = it } != -1) {
        serialPort.write(buffer, bytesRead)
      }

      // Send end marker: <END_OF_FILE>
      val endMarker = "<END_OF_FILE>"
      serialPort.write(endMarker.toByteArray(), 1000)
      serialPort.write("\n".toByteArray(), 1000)

      Log.i(TAG + Throwable().stackTrace[0].lineNumber, "File transfer completed")
    }
  }
}