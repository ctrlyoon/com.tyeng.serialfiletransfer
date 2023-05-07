package com.tyeng.serialfiletransfer.helpers

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.media.audiofx.Visualizer
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
import com.tyeng.serialfiletransfer.services.SerialConnectionService.Companion.usbSerialPort
import kotlinx.coroutines.*
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
//                    e.printStackTrace()
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
                writeAsync(header.toByteArray(), 200) { exception ->
                    if (exception != null) {
                    } else {
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG + Throwable().stackTrace[0].lineNumber, "Error sending header: ${e.message}")
                return
            }
            Log.i(TAG + Throwable().stackTrace[0].lineNumber, "header: sent")

            while (fileInputStream.read(buffer).also { bytesRead = it } != -1) {
                try {
                    if(file.name=="command.json") {
                        Log.i(TAG + Throwable().stackTrace[0].lineNumber, "Sending buffer (ASCII): ${String(buffer, 0, bytesRead, charset("US-ASCII"))}")
                    }
                    writeAsync(buffer, 200) { exception ->
                        if (exception != null) {
                        } else {
                        }
                    }
                    Thread.sleep(50)

                } catch (e: IOException) {
                    Log.e(TAG + Throwable().stackTrace[0].lineNumber, "Error sending file data: ${e.message}")
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

    private val scope = CoroutineScope(Dispatchers.Main + Job())

    fun writeAsync(data: ByteArray, timeout: Int, onCompletion: (exception: Exception?) -> Unit) {
        scope.launch {
            val exception = withContext(Dispatchers.IO) {
                try {
                    usbSerialPort?.write(data, timeout)
                    null
                } catch (e: IOException) {
                    Log.e(TAG + Throwable().stackTrace[0].lineNumber, "Error writing data: ${e.message}")
                    e
                }
            }
            onCompletion(exception)
        }
    }

    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    private var isRecording = false
    private var silencePeriods = 0
    private val silenceThreshold = 300 // Adjust this value based on your desired silence threshold

    fun processCommandData(command: String, action: String) {
        when (command) {
            "1", "2", "3", "4", "5", "6", "7", "8", "charge" -> {
                Log.i(TAG + Throwable().stackTrace[0].lineNumber, "Turning ${if (action == "on") "on" else "off"} port $command")
                // TODO: Implement TYUtils.runCommand() in Kotlin
            }
            "record" -> {
                val outputFile = File(context.cacheDir, "${action}.wav")
                mediaRecorder = MediaRecorder().apply {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                    setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                    setOutputFile(outputFile.absolutePath)
                }
                try {
                    mediaRecorder?.prepare()
                    mediaRecorder?.start()
                    Log.i(TAG + Throwable().stackTrace[0].lineNumber, "Recording $action with silence detection")

                    // Start monitoring the amplitude
                    isRecording = true
                    scope.launch {
                        while (isRecording) {
                            withContext(Dispatchers.IO) {
                                val maxAmplitude = mediaRecorder?.maxAmplitude ?: 0
                                if (shouldStopRecording(maxAmplitude)) {
                                    processCommandData("stopRecord", "")
                                }
                            }
                            delay(100) // Adjust the delay as needed
                        }
                    }
                } catch (e: IOException) {
                    Log.e(TAG + Throwable().stackTrace[0].lineNumber, "Error preparing or starting the recording: ${e.message}")
                }
            }
            "play" -> {
                val inputFile = File(context.cacheDir, "${action}.wav")
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(inputFile.absolutePath)
                    prepare()
                    start()
                    setOnCompletionListener {
                        it.release()
                    }
                }
                Log.i(TAG + Throwable().stackTrace[0].lineNumber, "Playing $action")
            }
            else -> {
                Log.i(TAG + Throwable().stackTrace[0].lineNumber, "Invalid command $command")
            }
        }
    }
    private fun shouldStopRecording(amplitude: Int): Boolean {
        if (amplitude < silenceThreshold) {
            silencePeriods++
            if (silencePeriods >= 10) { // 10 * 100 ms = 1 second
                return true
            }
        } else {
            silencePeriods = 0
        }
        return false
    }

}
