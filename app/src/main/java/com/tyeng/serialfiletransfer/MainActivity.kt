package com.tyeng.serialfiletransfer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.provider.Settings.Global.getString
import android.speech.tts.TextToSpeech
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.tyeng.serialfiletransfer.helpers.TtsHelper
import com.tyeng.serialfiletransfer.services.SerialConnectionService
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
  companion object {
    val TAG = "mike_" + Thread.currentThread().stackTrace[2].className + " "
    var greetingsSentence = ""
  }
  private lateinit var sendCommandButton: Button
  private lateinit var editTextGreetings: EditText
  private lateinit var editTextCommand: EditText
  private lateinit var editTextAction: EditText
  private lateinit var tts: TextToSpeech // Declare TextToSpeech variable

  private val connectionReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
      if (intent?.action == SerialConnectionService.ACTION_CONNECTION_ESTABLISHED) {
        sendCommandButton.isEnabled = true
        Log.i(TAG + Throwable().stackTrace[0].lineNumber, "Serial is initialized")
        SerialConnectionService.serialPortHelper?.sendCommandJson(applicationContext, "setTime",SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))
      }
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)
    greetingsSentence = getString(R.string.default_greeting)
    sendCommandButton = findViewById(R.id.buttonCommandJson)
    LocalBroadcastManager.getInstance(this).registerReceiver(connectionReceiver, IntentFilter(SerialConnectionService.ACTION_CONNECTION_ESTABLISHED))
    Intent(this, SerialConnectionService::class.java).also { intent ->
      ContextCompat.startForegroundService(this, intent)
    }
    createNotificationChannel()
    tts = TextToSpeech(applicationContext, TextToSpeech.OnInitListener { status ->
      if (status != TextToSpeech.ERROR) {
        tts.language = Locale.KOREAN
        val fileName = "greetings"
        val selectedLocale = resources.configuration.locale
        tts.language = selectedLocale
        val ttsHelper = TtsHelper(applicationContext, tts, selectedLocale)
        ttsHelper.writeTextToFile(greetingsSentence, fileName) {
          Log.i(TAG + Throwable().stackTrace[0].lineNumber, "makeGreetings created greetings")
        }
      }
    })
    editTextGreetings = findViewById(R.id.editTextGreetings)
    editTextGreetings.setText(greetingsSentence)
    editTextGreetings.addTextChangedListener(object : TextWatcher {
      override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
      override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
        val makeGreetingsButton = findViewById<Button>(R.id.buttonWriteGreetings)
        makeGreetingsButton.isEnabled = s.isNotEmpty()
      }

      override fun afterTextChanged(s: Editable) {}
    })
    editTextCommand = findViewById(R.id.editTextCommand)
    editTextCommand.setText("play")
    editTextCommand.addTextChangedListener(object : TextWatcher {
      override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
      override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
        val commandJsonButton = findViewById<Button>(R.id.buttonCommandJson)
        commandJsonButton.isEnabled = s.isNotEmpty() && editTextAction.text.isNotEmpty()
      }

      override fun afterTextChanged(s: Editable) {}
    })

    editTextAction = findViewById(R.id.editTextAction)
    editTextAction.setText("greetings")
    editTextAction.addTextChangedListener(object : TextWatcher {
      override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
      override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
        val commandJsonButton = findViewById<Button>(R.id.buttonCommandJson)
        commandJsonButton.isEnabled = s.isNotEmpty() && editTextCommand.text.isNotEmpty()
      }

      override fun afterTextChanged(s: Editable) {}
    })
  }

  fun makeGreetings(view: View) {
    val textGreetings = editTextGreetings.text.toString()
    if (textGreetings != greetingsSentence) {
      val fileName = "greetings"
      val selectedLocale = resources.configuration.locale
      tts.language = selectedLocale
      val ttsHelper = TtsHelper(applicationContext, tts, selectedLocale)
      ttsHelper.writeTextToFile(textGreetings, fileName) {
        greetingsSentence = textGreetings
        Log.i(TAG + Throwable().stackTrace[0].lineNumber, "makeGreetings created greetings")
      }
    } else {
      Log.i(TAG + Throwable().stackTrace[0].lineNumber, "greetingsSentence not changed")
    }
  }

  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channelId = "SerialConnectionService"
      val channelName = "Serial Connection Service"
      val importance = NotificationManager.IMPORTANCE_LOW
      val channel = NotificationChannel(channelId, channelName, importance).apply {
        description = "Foreground service for serial connection"
      }
      val notificationManager: NotificationManager =
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      notificationManager.createNotificationChannel(channel)
    }
  }

  fun sendCommand(view: View) {
    val command = editTextCommand.text.toString()
    val action = editTextAction.text.toString()
   SerialConnectionService.serialPortHelper?.sendCommandJson(this, command,action)
  }

  override fun onDestroy() {
    super.onDestroy()
    LocalBroadcastManager.getInstance(this).unregisterReceiver(connectionReceiver)
  }

  fun sendGreetings(view: View?) {
    val fileName = "greetings.wav"
    val filePath = "${cacheDir}/$fileName"
    Log.i(TAG + Throwable().stackTrace[0].lineNumber, "sending file $filePath")
    val file = File(filePath)
    if (file.exists()) {
      val usbSerialPort = SerialConnectionService.usbSerialPort
      if (usbSerialPort != null) {
        Log.i(TAG + Throwable().stackTrace[0].lineNumber, "sending file $filePath")
        SerialConnectionService.serialPortHelper?.sendFile(usbSerialPort, File(filePath))
      } else {
        Toast.makeText(this, "Serial connection not established", Toast.LENGTH_SHORT).show()
      }
    } else {
      Toast.makeText(this, "Greetings file not found", Toast.LENGTH_SHORT).show()
    }
  }

  override fun onCreateOptionsMenu(menu: Menu): Boolean {
    menuInflater.inflate(R.menu.language_menu, menu)
    return true
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    when (item.itemId) {
      R.id.lang_ko -> {
        setLocale("ko")
        recreate()
        return true
      }
      R.id.lang_en -> {
        setLocale("en")
        recreate()
        return true
      }
      R.id.lang_zh -> {
        setLocale("zh")
        recreate()
        return true
      }
      else -> return super.onOptionsItemSelected(item)
    }
  }

  private fun setLocale(lang: String) {
    val myLocale = Locale(lang)
    val res = resources
    val dm = res.displayMetrics
    val conf = res.configuration
    conf.setLocale(myLocale)
    res.updateConfiguration(conf, dm)
    greetingsSentence = getString(R.string.default_greeting)
    editTextGreetings.setText(greetingsSentence)
  }

}