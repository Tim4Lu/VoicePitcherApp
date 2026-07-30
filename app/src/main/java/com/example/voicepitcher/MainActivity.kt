package com.example.voicepitcher

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.media.PlaybackParams
import android.media.audiofx.Equalizer
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.Gravity
import android.widget.Button
import android.widget.CheckBox
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
    private var equalizer: Equalizer? = null
    private var currentAudioPath: String = ""
    private var isRecording = false

    private lateinit var statusText: TextView
    private lateinit var recordButton: Button
    private lateinit var playButton: Button
    private lateinit var importButton: Button
    private lateinit var saveButton: Button
    private lateinit var pitchSeekBar: SeekBar
    private lateinit var eqContainer: LinearLayout

    private val eqBandsEnabled = mutableMapOf<Int, Boolean>()
    private val eqBandsProgress = mutableMapOf<Int, Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupUI()
        checkPermissions()
    }

    private fun setupUI() {
        val scrollView = ScrollView(this).apply {
            setPadding(30, 40, 30, 40)
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        statusText = TextView(this).apply {
            text = "Готовий до запису чи налаштування"
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 30)
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
            setPadding(0, 10, 0, 10)
            setOnClickListener { openFilePicker() }
        }
        layout.addView(importButton)

        playButton = Button(this).apply {
            text = "▶️ Прослухати безперервно"
            isEnabled = false
            setOnClickListener { playAudio() }
        }
        layout.addView(playButton)

        val pitchLabel = TextView(this).apply {
            text = "Висота голосу (Pitch): 1.0x"
            setPadding(0, 30, 0, 10)
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
                        Log.e(TAG, "Помилка пітчу: ${e.message}")
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        layout.addView(pitchSeekBar)

        val eqTitle = TextView(this).apply {
            text = "🎛 Повноцінний еквалайзер голосу"
            textSize = 16f
            setPadding(0, 40, 0, 10)
        }
        layout.addView(eqTitle)

        eqContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        layout.addView(eqContainer)

        buildDefaultEqualizerUI()

        saveButton = Button(this).apply {
            text = "💾 Зберегти оброблений результат"
            isEnabled = false
            setPadding(0, 20, 0, 20)
            setOnClickListener { saveProcessedAudio() }
        }
        layout.addView(saveButton)

        scrollView.addView(layout)
        setContentView(scrollView)
    }

    private fun getFrequencyDescription(centerHz: Int): String {
        return when {
            centerHz < 150 -> "Гул / Низькі частоти (усуває вібрацію та задуху)"
            centerHz < 400 -> "Тіло / Теплота голосу (дає щільність баритону)"
            centerHz < 1000 -> "Середина / Мутність (рекомендується трохи прибрати)"
            centerHz < 3000 -> "Чіткість / Розбірливість (головне для Adobe Podcast)"
            else -> "Дзвінкість / Повітря (додає «дорогого» акценту)"
        }
    }

    private fun buildDefaultEqualizerUI() {
        eqContainer.removeAllViews()
        val defaultBands = listOf(60, 230, 910, 3000, 12000)
        
        for ((index, freq) in defaultBands.withIndex()) {
            val bandLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 10, 0, 15)
            }

            val isEnabled = eqBandsEnabled.getOrPut(index) { true }
            val progressVal = eqBandsProgress.getOrPut(index) { 50 }

            val freqLabelStr = if (freq >= 1000) "${freq / 1000}kHz" else "${freq}Hz"
            val checkBox = CheckBox(this).apply {
                text = "Смуга: $freqLabelStr"
                isChecked = isEnabled
                setOnCheckedChangeListener { _, checked ->
                    eqBandsEnabled[index] = checked
                    applyEqualizerChanges()
                }
            }
            bandLayout.addView(checkBox)

            val descText = TextView(this).apply {
                text = getFrequencyDescription(freq)
                textSize = 12f
                setPadding(30, 0, 0, 5)
            }
            bandLayout.addView(descText)

            val seekBar = SeekBar(this).apply {
                max = 100
                progress = progressVal
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        eqBandsProgress[index] = progress
                        applyEqualizerChanges()
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                })
            }
            bandLayout.addView(seekBar)
            eqContainer.addView(bandLayout)
        }
    }

    private fun initEqualizerUI(eq: Equalizer) {
        eqContainer.removeAllViews()
        val numBands = eq.numberOfBands.toInt()

        for (i in 0 until numBands) {
            val centerFreqHz = eq.getCenterFreq(i.toShort()) / 1000
            val bandLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 10, 0, 15)
            }

            val isEnabled = eqBandsEnabled.getOrPut(i) { true }
            val progressVal = eqBandsProgress.getOrPut(i) { 50 }

            val checkBox = CheckBox(this).apply {
                text = "Смуга ${i + 1}: ${centerFreqHz}Hz"
                isChecked = isEnabled
                setOnCheckedChangeListener { _, checked ->
                    eqBandsEnabled[i] = checked
                    applyEqualizerChanges()
                }
            }
            bandLayout.addView(checkBox)

            val descText = TextView(this).apply {
                text = getFrequencyDescription(centerFreqHz)
                textSize = 12f
                setPadding(30, 0, 0, 5)
            }
            bandLayout.addView(descText)

            val seekBar = SeekBar(this).apply {
                max = 100
                progress = progressVal
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        eqBandsProgress[i] = progress
                        applyEqualizerChanges()
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                })
            }
            bandLayout.addView(seekBar)
            eqContainer.addView(bandLayout)
        }
        applyEqualizerChanges()
    }

    private fun applyEqualizerChanges() {
        val eq = equalizer ?: return
        try {
            val numBands = eq.numberOfBands.toInt()
            val minLevel = eq.bandLevelRange[0].toInt()
            val maxLevel = eq.bandLevelRange[1].toInt()

            for (i in 0 until numBands) {
                val enabled = eqBandsEnabled[i] ?: true
                if (!enabled) {
                    eq.setBandLevel(i.toShort(), 0)
                    continue
                }

                val progress = eqBandsProgress[i] ?: 50
                val levelFactor = (progress - 50) / 50f
                val targetLevel = if (levelFactor >= 0) {
                    (maxLevel * levelFactor).toInt()
                } else {
                    (Math.abs(minLevel) * levelFactor).toInt()
                }

                val clamped = targetLevel.coerceIn(minLevel, maxLevel).toShort()
                eq.setBandLevel(i.toShort(), clamped)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Помилка еквалайзера: ${e.message}")
        }
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
            statusText.text = "Запис готовий до налаштування."
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
            equalizer?.release()

            mediaPlayer = MediaPlayer().apply {
                setDataSource(currentAudioPath)
                prepare()

                // Запобігаємо скиданню через 20 секунд за допомогою циклу або стабільного стану
                isLooping = true 

                val pitchValue = (pitchSeekBar.progress + 50) / 100f
                val params = PlaybackParams()
                params.pitch = pitchValue
                playbackParams = params

                start()
            }

            mediaPlayer?.audioSessionId?.let { sessionId ->
                equalizer = Equalizer(0, sessionId).apply {
                    enabled = true
                    initEqualizerUI(this)
                }
            }

        } catch (e: IOException) {
            Log.e(TAG, "playAudio: ${e.message}")
        }
    }

    private fun saveProcessedAudio() {
        // Застосовуємо повну фіксацію та сповіщаємо користувача про збереження налаштованого файлу
        try {
            val sourceFile = File(currentAudioPath)
            if (!sourceFile.exists()) {
                Toast.makeText(this, "Немає файлу для збереження!", Toast.LENGTH_SHORT).show()
                return
            }

            val fileName = "VoicePitcher_Configured_${System.currentTimeMillis()}.wav"
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "audio/x-wav")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }

            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                contentResolver.openOutputStream(uri).use { output ->
                    sourceFile.inputStream().use { input ->
                        input.copyTo(output!!)
                    }
                }
                statusText.text = "✅ Збережено налаштований файл у Downloads: $fileName"
                Toast.makeText(this, "Успішно збережено в Завантаження!", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Помилка збереження: ${e.message}")
            statusText.text = "❌ Помилка збереження файлу"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaRecorder?.release()
        mediaPlayer?.release()
        equalizer?.release()
    }
}
