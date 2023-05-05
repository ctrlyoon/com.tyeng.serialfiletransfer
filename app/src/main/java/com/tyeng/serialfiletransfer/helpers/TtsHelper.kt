package com.tyeng.serialfiletransfer.helpers

import android.content.Context
import android.media.AudioManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.tyeng.serialfiletransfer.MainActivity
import java.io.File
import java.util.*

class TtsHelper(
    private val context: Context,
    private val tts: TextToSpeech,
    private val locale: Locale
) {
    companion object {
        val TAG = "mike_" + Thread.currentThread().stackTrace[2].className + " "
    }
    init {
        // Set TTS language and properties
        tts.language = locale
        tts.setSpeechRate(1.0f)
        tts.setPitch(1.0f)
    }

    fun writeTextToFile(
        text: String,
        fileName: String,
        onDone: () -> Unit
    ) {
        Log.i(TAG + Throwable().stackTrace[0].lineNumber, "greetings sentence : ${text}")
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                Log.i(TAG + Throwable().stackTrace[0].lineNumber, "TTS onStart")
            }

            override fun onDone(utteranceId: String?) {
                Log.i(TAG + Throwable().stackTrace[0].lineNumber, "TTS onDone")
                onDone()
            }

            override fun onError(utteranceId: String?) {
                Log.i(TAG + Throwable().stackTrace[0].lineNumber, "TTS onError")
            }
        })
        Log.i(TAG + Throwable().stackTrace[0].lineNumber, "fileName: $fileName")
        val file = File(context.cacheDir, "$fileName.wav")
        val bundle = Bundle()
        bundle.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
        tts.synthesizeToFile(text, bundle, file, "greeting_request")

        Log.i(TAG + Throwable().stackTrace[0].lineNumber, "File created at: ${file.absolutePath}")
    }

}
