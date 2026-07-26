package com.example.voicepitcher

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.media.PlaybackParams
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class MainActivity : Activity() {

    private val TAG = "AudioRecorderLog"
    private val PERMISSION_REQUEST_CODE = 200
    private val PICK_AUDIO_REQUEST_CODE = 1001

    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    private var currentAudioPath: String = ""
    private var isRecording = false

    private lateinit var statusText: TextView
    private lateinit var recordButton: Button
    private lateinit var playButton: Button
    private lateinit var importButton: Button
    private lateinit var saveButton: Button
    private lateinit var pitchSeekBar: SeekBar
    private lateinit var claritySeekBar: SeekBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupUI()
        checkPermissions()
    }

    private fun setupUI() {
        val scrollView = ScrollView(this).apply {
            setPadding(30, 50, 30, 50)
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }

        statusText = TextView(this).apply {
            text = "Готовий до запису"
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 40)
        }
        layout.addView(statusText)

        recordButton = Button(this).apply {
            text = "🔴 Почати запис"
            setOnClickListener {
                if (isRecording) stopRecording() else startRecording()
            }
        }
        layout.addView(recordButton)

        importButton = Button(this).apply {
            text = "📂 Вибрати аудіофайл"
            setPadding(0, 20, 0, 20)
            setOnClickListener { openFilePicker() }
        }
        layout.addView(importButton)

        playButton = Button(this).apply {
            text = "▶️ Прослухати"
            isEnabled = false
            setOnClickListener { playAudio() }
        }
        layout.addView(playButton)

        val pitchLabel = TextView(this).apply {
            text = "Висота голосу (Pitch): 1.0x"
            setPadding(0, 40, 0, 10)
        }
        layout.addView(pitchLabel)

        pitchSeekBar = SeekBar(this).apply {
            max = 150
            progress = 50
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val pitchValue = (progress + 50) / 100f
                    pitchLabel.text = "Висота голосу (Pitch): ${pitchValue}x"
                    try {
                        mediaPlayer?.let {
                            if (it.isPlaying) {
                                val params = PlaybackParams()
                                params.pitch = pitchValue
                                it.playbackParams = params
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Помилка зміни пітчу: ${e.message}")
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        layout.addView(pitchSeekBar)

        val clarityLabel = TextView(this).apply {
            text = "Чіткість/Гучність (Gain): 1.0x"
            setPadding(0, 40, 0, 10)
        }
        layout.addView(clarityLabel)

        claritySeekBar = SeekBar(this).apply {
            max = 200
            progress = 100
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val gainValue = progress / 100f
                    clarityLabel.text = "Чіткість/Гучність (Gain): ${String.format("%.2f", gainValue)}x"
                    try {
                        mediaPlayer?.let {
                            it.setVolume(gainValue, gainValue)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Помилка зміни гучності: ${e.message}")
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        layout.addView(claritySeekBar)

        saveButton = Button(this).apply {
            text = "💾 Зберегти результат"
            isEnabled = false
            setOnClickListener { saveProcessedAudio() }
        }
        layout.addView(saveButton)

        scrollView.addView(layout)
        setContentView(scrollView)
    }

    private fun checkPermissions() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), PERMISSION_REQUEST_CODE)
        }
    }

    private fun startRecording() {
        currentAudioPath = "${externalCacheDir?.absolutePath}/raw_record.m4a"
        mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(320000)
            setAudioSamplingRate(44100)
            setOutputFile(currentAudioPath)

            try {
                prepare()
                start()
                isRecording = true
                recordButton.text = "⏹ Зупинити запис"
                statusText.text = "Йде запис високої якості..."
            } catch (e: IOException) {
                Log.e(TAG, "startRecording: ${e.message}")
            }
        }
    }

    private fun stopRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            isRecording = false
            recordButton.text = "🔴 Почати запис"
            playButton.isEnabled = true
            saveButton.isEnabled = true
            statusText.text = "Запис готовий."
        } catch (e: Exception) {
            Log.e(TAG, "stopRecording: ${e.message}")
        }
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "audio/*"
        }
        startActivityForResult(intent, PICK_AUDIO_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_AUDIO_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            val uri = data.data ?: return
            val tempFile = File(externalCacheDir, "imported_audio.wav")
            try {
                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
                currentAudioPath = tempFile.absolutePath
                statusText.text = "Файл завантажено!"
                playButton.isEnabled = true
                saveButton.isEnabled = true
            } catch (e: Exception) {
                Log.e(TAG, "Помилка імпорту: ${e.message}")
                Toast.makeText(this, "Помилка імпорту файлу", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun playAudio() {
        val file = File(currentAudioPath)
        if (!file.exists()) return

        try {
            mediaPlayer?.release()

            mediaPlayer = MediaPlayer().apply {
                setDataSource(currentAudioPath)
                prepare()

                val pitchValue = (pitchSeekBar.progress + 50) / 100f
                val params = PlaybackParams()
                params.pitch = pitchValue
                playbackParams = params

                val gainValue = claritySeekBar.progress / 100f
                setVolume(gainValue, gainValue)

                start()
            }

        } catch (e: IOException) {
            Log.e(TAG, "playAudio: ${e.message}")
        }
    }

    private fun saveProcessedAudio() {
        exportToDownloads(currentAudioPath)
    }

    private fun exportToDownloads(sourcePath: String) {
        try {
            val fileName = "VoicePitcher_${System.currentTimeMillis()}.wav"
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "audio/x-wav")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }

            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                contentResolver.openOutputStream(uri).use { output ->
                    File(sourcePath).inputStream().use { input ->
                        input.copyTo(output!!)
                    }
                }
                statusText.text = "✅ Збережено в Downloads: $fileName"
                Toast.makeText(this, "Збережено в Завантаження!", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Помилка експорту: ${e.message}")
            statusText.text = "❌ Помилка збереження"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaRecorder?.release()
        mediaPlayer?.release()
    }
}
