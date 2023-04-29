import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.tyeng.serialfiletransfer.helpers.SerialPortHelper
import com.tyeng.serialfiletransfer.usbserial.driver.UsbSerialPort

class SerialConnectionService : Service() {
    companion object {
        val TAG = "mike_" + Thread.currentThread().stackTrace[2].className + " "
        private val BAUD_RATE = 115200
    }
    private val serialPortHelper = SerialPortHelper(this)
    private var usbSerialPort: UsbSerialPort? = null

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        usbSerialPort = serialPortHelper.openSerialPort()
        usbSerialPort?.let { port ->
            serialPortHelper.startListening(port) { receivedData ->
                // Process the received data here
                Log.i(TAG + Throwable().stackTrace[0].lineNumber, "Received data: ${String(receivedData)}")
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serialPortHelper.stopListening()
        usbSerialPort?.close()
    }
}

