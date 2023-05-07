package com.tyeng.serialfiletransfer

import android.util.Log
import java.util.*

fun printBuffer(TAG:String, buffer: ByteArray, lineNumber: Int, msgType: String, length: Int) {
//      val HEX = charArrayOf(0',1',2',3',4',5',6',7',8',9',a',b',c',d',e',f')
    val result = StringBuilder()
    for (j in 0 until length) {
        val decimal = buffer[j].toInt() and 0xff
//            ${crc16.toString(16).uppercase()
        var hex = decimal.toString(16).uppercase()
        if (hex.length % 2 == 1) {                    // if half hex, pad with zero, e.g \t
            hex = "0$hex"
        }
        result.append(hex)
    }
    Log.i(TAG + lineNumber, "$msgType :$result")
}
