package com.tyeng.serialfiletransfer.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.tyeng.serialfiletransfer.R
import com.tyeng.serialfiletransfer.helpers.SerialPortHelper
import com.tyeng.serialfiletransfer.usbserial.driver.UsbSerialPort
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Collections.min
import kotlin.math.min

class SerialConnectionService : Service() {
    companion object {
        val TAG = "mike_" + Thread.currentThread().stackTrace[2].className + " "
        const val ACTION_CONNECTION_ESTABLISHED = "com.tyeng.serialfiletransfer.ACTION_CONNECTION_ESTABLISHED"
        var serialPortHelper: SerialPortHelper? = null
        var usbSerialPort: UsbSerialPort? = null
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        serialPortHelper = SerialPortHelper(applicationContext)
        usbSerialPort = serialPortHelper!!.openSerialPort()
        Log.i(TAG + Throwable().stackTrace[0].lineNumber, "onStartCommand")
        val receivedDataBuffer = StringBuilder()
        val jsonFileDelimiter = "#*#"
        usbSerialPort?.let { port ->
            serialPortHelper!!.startListening(port) { receivedData ->
                // Process the received data here
                receivedDataBuffer.append(String(receivedData))
                val delimiterIndex = receivedDataBuffer.indexOf(jsonFileDelimiter)

                if (delimiterIndex != -1) {
                    val header = receivedDataBuffer.substring(0, delimiterIndex)
                    receivedDataBuffer.delete(0, delimiterIndex + jsonFileDelimiter.length)

                    val (fileName, fileSize) = header.split("|")
                    val intFileSize = fileSize.toInt()

                    if (fileName == "command.json") {
                        val jsonBytes = ByteArray(intFileSize)
                        var bytesRead = 0
                        while (bytesRead < intFileSize) {
                            bytesRead += usbSerialPort?.read(jsonBytes, bytesRead) ?: 0
                        }
                        val commandData = JSONObject(String(jsonBytes))
                        val command = commandData.getString("command")
                        val action = commandData.getString("action")
                        Log.i(TAG + Throwable().stackTrace[0].lineNumber, "Received data: ${String(receivedData)}")
                        serialPortHelper!!.processCommandData(command, action)
                    } else {
                        val savePath = File(cacheDir, fileName).absolutePath
                        try {
                            // Initialize the progress bar notification
                            val notificationId = 2
                            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                            val channelId = "FileTransferChannel"
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                val channel = NotificationChannel(channelId, "File Transfer", NotificationManager.IMPORTANCE_LOW)
                                channel.description = "File Transfer Progress"
                                channel.enableLights(true)
                                channel.lightColor = Color.BLUE
                                notificationManager.createNotificationChannel(channel)
                            }
                            val notificationBuilder = NotificationCompat.Builder(this, channelId)
                                .setSmallIcon(R.drawable.ic_file_transfer)
                                .setContentTitle("File Transfer")
                                .setContentText("Transferring $fileName")
                                .setPriority(NotificationCompat.PRIORITY_LOW)
                                .setProgress(intFileSize, 0, false)
                            startForeground(notificationId, notificationBuilder.build())

                            FileOutputStream(savePath).use { fos ->
                                var bytesRead = 0
                                while (bytesRead < intFileSize) {
                                    val chunkSize = min(4096, intFileSize - bytesRead)
                                    val data = ByteArray(chunkSize)
                                    bytesRead += usbSerialPort?.read(data, 0) ?: 0
                                    fos.write(data)
                                    // Update the progress bar notification
                                    notificationBuilder.setProgress(intFileSize, bytesRead, false)
                                    notificationManager.notify(notificationId, notificationBuilder.build())
                                }
                            }

                            // Set the notification to "File transfer completed"
                            notificationBuilder
                                .setContentText("File transfer completed")
                                .setProgress(0, 0, false)
                                .setAutoCancel(true)
                            notificationManager.notify(notificationId, notificationBuilder.build())

                            Log.i(TAG, "File transfer completed. Saved as $savePath")
                        } catch (e: IOException) {
                            Log.e(TAG, "Error writing file: $e")
                        }
                    }
                }
            }
            // Send a broadcast when the connection is established
            val connectionIntent = Intent(ACTION_CONNECTION_ESTABLISHED)
            Log.i(TAG + Throwable().stackTrace[0].lineNumber, "Sending ACTION_CONNECTION_ESTABLISHED broadcast")
            LocalBroadcastManager.getInstance(this).sendBroadcast(connectionIntent)
        }
        val channelId = "SerialConnectionService"
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(
                BitmapFactory.decodeResource(this.resources,
                R.mipmap.ic_launcher
            ))
            .setContentTitle("Serial Connection Service")
            .setContentText("Running...")
            .setPriority(NotificationCompat.PRIORITY_LOW)

        val notification = notificationBuilder.build()
        startForeground(1, notification)
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serialPortHelper?.stopListening()
        usbSerialPort?.close()
    }
}

