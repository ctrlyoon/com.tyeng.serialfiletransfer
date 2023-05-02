package com.tyeng.serialfiletransfer.helpers

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager
import com.tyeng.serialfiletransfer.MainActivity
import com.tyeng.serialfiletransfer.usbserial.driver.UsbSerialPort
import com.tyeng.serialfiletransfer.usbserial.driver.UsbSerialProber
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import android.util.Log
import android.widget.Toast
import com.tyeng.serialfiletransfer.printBuffer
import com.tyeng.serialfiletransfer.services.SerialConnectionService
import org.json.JSONObject
import java.io.FileOutputStream

class SerialPortHelper(private val context: Context) {
    companion object {
        val TAG = "mike_" + Thread.currentThread().stackTrace[2].className + " "
        private val BAUD_RATE = 115200
        private var listenerThread: Thread? = null
        private var isListening = false
    }

    fun startListening(usbSerialPort: UsbSerialPort, onDataReceived: (ByteArray) -> Unit) {
        if (listenerThread != null) {
            return // Listener thread already started
        }

        isListening = true
        listenerThread = Thread {
            val buffer = ByteArray(4096)
            while (isListening) {
                try {
                    val bytesRead = usbSerialPort.read(buffer, 1000)
                    if (bytesRead > 0) {
                        val receivedData = buffer.copyOfRange(0, bytesRead)
                        onDataReceived(receivedData)
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
        listenerThread?.start()
    }

    fun stopListening() {
        isListening = false
        listenerThread?.join()
        listenerThread = null
    }

    fun openSerialPort(): UsbSerialPort? {
        val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager)
        if (availableDrivers.isEmpty()) {
            Log.i(TAG + Throwable().stackTrace[0].lineNumber, "No available drivers found")
            return null
        }

        val driver = availableDrivers[0]
        val connection = manager.openDevice(driver.device)
        if (connection == null) {
            Log.i(TAG + Throwable().stackTrace[0].lineNumber, "Connection is null")
            val mainActivityStartPendingIntent = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE
            )
            manager.requestPermission(driver.device, mainActivityStartPendingIntent)
            return null
        }

        val usbSerialPort = driver.ports[0]
        usbSerialPort.open(connection)
        usbSerialPort.setParameters(BAUD_RATE, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
        return usbSerialPort
    }

    @Throws(IOException::class)
    fun sendFile(serialPort: UsbSerialPort, file: File) {
        FileInputStream(file).use { fileInputStream ->
            val buffer = ByteArray(file.length().toInt())
            var bytesRead: Int

            // Send header: <file_name>|<file_size>|###|
            val header = "${file.name}|${file.length()}#*#"

            Log.i(TAG + Throwable().stackTrace[0].lineNumber, "Sending header: $header")
            try {
                serialPort.write(header.toByteArray(), 100) // Change the timeout value here
            } catch (e: IOException) {
                Log.e(TAG, "Error sending header: ${e.message}")
                return
            }
            Log.i(TAG + Throwable().stackTrace[0].lineNumber, "header: sent")

            while (fileInputStream.read(buffer).also { bytesRead = it } != -1) {
                try {
                    if(file.name=="command.json") {
                        Log.i(TAG + Throwable().stackTrace[0].lineNumber, "Sending buffer (ASCII): ${String(buffer, 0, bytesRead, charset("US-ASCII"))}")
                    }
                    serialPort.write(buffer, bytesRead)

                    // Add delay between chunks of data
                    Thread.sleep(50)

                } catch (e: IOException) {
                    Log.e(TAG, "Error sending file data: ${e.message}")
                    break
                }
            }

            // Send file delimiter and wait for it to be transmitted
//        serialPort.write("##FILE##".toByteArray(), 1000)
            Thread.sleep(100)
            Log.i(TAG + Throwable().stackTrace[0].lineNumber, "File transfer completed")

            // Add a delay between sending files
            Thread.sleep(500)
        }
    }

    fun sendCommandJson(context: Context, command: String, action:String) {
        val json = JSONObject().apply {
            put("command", command)
            put("action", action)
        }
        val jsonString = json.toString()
        val byteArray = jsonString.toByteArray()
        val file = File(context.cacheDir, "command.json")
        FileOutputStream(file).use { outputStream ->
            outputStream.write(byteArray)
        }
        val usbSerialPort = SerialConnectionService.usbSerialPort
        if (usbSerialPort != null) {
            SerialConnectionService.serialPortHelper?.sendFile(usbSerialPort, file)
        } else {
            Toast.makeText(context, "Serial connection not established", Toast.LENGTH_SHORT).show()
        }
    }
}
