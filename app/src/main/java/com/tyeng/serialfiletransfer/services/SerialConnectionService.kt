package com.tyeng.serialfiletransfer.services

import android.app.Service
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.tyeng.serialfiletransfer.R
import com.tyeng.serialfiletransfer.helpers.SerialPortHelper
import com.tyeng.serialfiletransfer.usbserial.driver.UsbSerialPort

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
        usbSerialPort?.let { port ->
            serialPortHelper!!.startListening(port) { receivedData ->
                // Process the received data here
                Log.i(TAG + Throwable().stackTrace[0].lineNumber, "Received data: ${String(receivedData)}")
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

