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
            Log.i(TAG, "No available drivers found")
            return null
        }

        val driver = availableDrivers[0]
        val connection = manager.openDevice(driver.device)
        if (connection == null) {
            Log.i(TAG, "Connection is null")
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
            val buffer = ByteArray(4096)
            var bytesRead: Int

            // Send header: <file_name>|<file_size>
            val header = "${file.name}|${file.length()}"

            Log.i("FileTransferExample", "Sending header: $header")
            serialPort.write(header.toByteArray(), 1000)
            Log.i("FileTransferExample", "header: sent")
            // Send file data
            while (fileInputStream.read(buffer).also { bytesRead = it } != -1) {
                serialPort.write(buffer, bytesRead)
            }

            // Send end marker: <END_OF_FILE>
            val endMarker = "<END_OF_FILE>"
            serialPort.write(endMarker.toByteArray(), 1000)
            serialPort.write("\n".toByteArray(), 1000)

            Log.i("FileTransferExample", "File transfer completed")
        }
    }
}
