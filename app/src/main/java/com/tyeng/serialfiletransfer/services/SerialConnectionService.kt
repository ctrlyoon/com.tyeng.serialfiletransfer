import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
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
        usbSerialPort?.let { port ->
            serialPortHelper!!.startListening(port) { receivedData ->
                // Process the received data here
                Log.i(TAG, "Received data: ${String(receivedData)}")
            }
            // Send a broadcast when the connection is established
            val connectionIntent = Intent(ACTION_CONNECTION_ESTABLISHED)
            LocalBroadcastManager.getInstance(this).sendBroadcast(connectionIntent)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serialPortHelper?.stopListening()
        usbSerialPort?.close()
    }
}

