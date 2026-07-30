package com.example.voicepitcher

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.media.audiofx.Equalizer
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream

class MainActivity : Activity() {

    private val TAG = "AudioRecorderLog"
    private val PERMISSION_REQUEST_CODE = 200
    private val PICK_AUDIO_REQUEST_CODE = 1001

    private val recorderManager by lazy { AudioRecorderManager(this) }
    private val playerManager by lazy { AudioPlayerManager() }
    private val presetManager by lazy { PresetManager(this) }

    private var currentAudioPath: String = ""

    private lateinit var statusText: TextView
    private lateinit var recordButton: Button
    private lateinit var playButton: Button
    private lateinit var importButton: Button
    private lateinit var saveButton: Button
    private lateinit var pitchSeekBar: SeekBar
    private lateinit var volumeSeekBar: SeekBar
    private lateinit var eqContainer: LinearLayout
    private lateinit var fileNameInput: EditText
    private lateinit var presetNameInput: EditText
    private lateinit var savePresetButton: Button
    private lateinit var presetSpinner: Spinner

    private val eqBandsEnabled = mutableMapOf<Int, Boolean>()
    private val eqBandsProgress = mutableMapOf<Int, Int>()
    private var currentVolume = 1.0f
    private var currentPitch = 1.0f
    private var currentEqualizer: Equalizer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupUI()
        checkPermissions()
    }

    private fun setupUI() {
        val scrollView = ScrollView(this).apply { setPadding(30, 40, 30, 40) }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        statusText = TextView(this).apply {
            text = "Готовий до роботи"
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 20)
        }
        layout.addView(statusText)

        recordButton = Button(this).apply {
            text = "🔴 Почати запис"
            setOnClickListener { toggleRecording() }
        }
        layout.addView(recordButton)

        importButton = Button(this).apply {
            text = "📂 Вибрати аудіофайл"
            setPadding(0, 10, 0, 10)
            setOnClickListener { openFilePicker() }
        }
        layout.addView(importButton)

        playButton = Button(this).apply {
            text = "▶️ Прослухати"
            isEnabled = false
            setOnClickListener { playAudio() }
        }
        layout.addView(playButton)

        val presetLabel = TextView(this).apply { text = "📂 Керування пресетами:"; setPadding(0, 20, 0, 5) }
        layout.addView(presetLabel)

        val presetLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        presetSpinner = Spinner(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        presetLayout.addView(presetSpinner)

        val loadPresetBtn = Button(this).apply {
            text = "Завантажити"
            setOnClickListener { loadSelectedPreset() }
        }
        presetLayout.addView(loadPresetBtn)
        layout.addView(presetLayout)

        val savePresetLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 10, 0, 10)
        }
        presetNameInput = EditText(this).apply {
            hint = "Назва пресету..."
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        savePresetLayout.addView(presetNameInput)

        savePresetButton = Button(this).apply {
            text = "Зберегти пресет"
            setOnClickListener { saveCurrentPreset() }
        }
        savePresetLayout.addView(savePresetButton)
        layout.addView(savePresetLayout)

        val pitchLabel = TextView(this).apply { text = "Висота голосу (Pitch): 1.0x"; setPadding(0, 20, 0, 5) }
        layout.addView(pitchLabel)

        pitchSeekBar = SeekBar(this).apply {
            max = 150
            progress = 50
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    currentPitch = (progress + 50) / 100f
                    pitchLabel.text = "Висота голосу (Pitch): ${currentPitch}x"
                    playerManager.setPitch(currentPitch)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        layout.addView(pitchSeekBar)

        val volumeLabel = TextView(this).apply { text = "Гучність (Volume): 1.0x"; setPadding(0, 20, 0, 5) }
        layout.addView(volumeLabel)

        volumeSeekBar = SeekBar(this).apply {
            max = 200
            progress = 100
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    currentVolume = progress / 100f
                    volumeLabel.text = "Гучність (Volume): ${String.format("%.2f", currentVolume)}x"
                    playerManager.setVolume(currentVolume)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        layout.addView(volumeSeekBar)

        val eqTitle = TextView(this).apply { text = "🎛 Еквалайзер голосу"; textSize = 16f; setPadding(0, 30, 0, 10) }
        layout.addView(eqTitle)

        eqContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        layout.addView(eqContainer)
        buildDefaultEqualizerUI()

        val fileLabel = TextView(this).apply { text = "📝 Назва фінального файлу:"; setPadding(0, 30, 0, 5) }
        layout.addView(fileLabel)

        fileNameInput = EditText(this).apply { setText("MyVoiceProcessed") }
        layout.addView(fileNameInput)

        saveButton = Button(this).apply {
            text = "💾 Зберегти файл у Downloads"
            isEnabled = false
            setPadding(0, 20, 0, 20)
            setOnClickListener { saveProcessedAudio() }
        }
        layout.addView(saveButton)

        scrollView.addView(layout)
        setContentView(scrollView)
        updatePresetsSpinner()
    }

    private fun toggleRecording() {
        if (recorderManager.isRecording) {
            recorderManager.stopRecording()
            recordButton.text = "🔴 Почати запис"
            playButton.isEnabled = true
            saveButton.isEnabled = true
            statusText.text = "Запис готовий."
        } else {
            currentAudioPath = recorderManager.startRecording()
            recordButton.text = "⏹ Зупинити запис"
            statusText.text = "Йде запис..."
        }
    }

    private fun getFrequencyDescription(centerHz: Int): String {
        return when {
            centerHz < 150 -> "Гул / Низькі частоти"
            centerHz < 400 -> "Тіло / Теплота голосу"
            centerHz < 1000 -> "Середина / Мутність"
            centerHz < 3000 -> "Чіткість / Розбірливість (Adobe Podcast)"
            else -> "Дзвінкість / Повітря"
        }
    }

    private fun buildDefaultEqualizerUI() {
        eqContainer.removeAllViews()
        val defaultBands = listOf(60, 230, 910, 3000, 12000)
        for ((index, freq) in defaultBands.withIndex()) {
            val bandLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 5, 0, 10) }
            val isEnabled = eqBandsEnabled.getOrPut(index) { true }
            val progressVal = eqBandsProgress.getOrPut(index) { 50 }
            val freqLabelStr = if (freq >= 1000) "${freq / 1000}kHz" else "${freq}Hz"

            val checkBox = CheckBox(this).apply {
                text = "Смуга: $freqLabelStr (${getFrequencyDescription(freq)})"
                isChecked = isEnabled
                setOnCheckedChangeListener { _, checked ->
                    eqBandsEnabled[index] = checked
                    currentEqualizer?.let { playerManager.applyEqualizerBands(it, eqBandsProgress, eqBandsEnabled) }
                }
            }
            bandLayout.addView(checkBox)

            val seekBar = SeekBar(this).apply {
                max = 100
                progress = progressVal
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        eqBandsProgress[index] = progress
                        currentEqualizer?.let { playerManager.applyEqualizerBands(it, eqBandsProgress, eqBandsEnabled) }
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
        currentEqualizer = eq
        eqContainer.removeAllViews()
        val numBands = eq.numberOfBands.toInt()

        for (i in 0 until numBands) {
            val centerFreqHz = eq.getCenterFreq(i.toShort()) / 1000
            val bandLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 5, 0, 10) }
            val isEnabled = eqBandsEnabled.getOrPut(i) { true }
            val progressVal = eqBandsProgress.getOrPut(i) { 50 }

            val checkBox = CheckBox(this).apply {
                text = "Смуга ${i + 1}: ${centerFreqHz}Hz"
                isChecked = isEnabled
                setOnCheckedChangeListener { _, checked ->
                    eqBandsEnabled[i] = checked
                    playerManager.applyEqualizerBands(eq, eqBandsProgress, eqBandsEnabled)
                }
            }
            bandLayout.addView(checkBox)

            val seekBar = SeekBar(this).apply {
                max = 100
                progress = progressVal
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        eqBandsProgress[i] = progress
                        playerManager.applyEqualizerBands(eq, eqBandsProgress, eqBandsEnabled)
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                })
            }
            bandLayout.addView(seekBar)
            eqContainer.addView(bandLayout)
        }
        playerManager.applyEqualizerBands(eq, eqBandsProgress, eqBandsEnabled)
    }

    private fun saveCurrentPreset() {
        val name = presetNameInput.text.toString().trim()
        if (name.isEmpty()) {
            Toast.makeText(this, "Введіть назву пресету!", Toast.LENGTH_SHORT).show()
            return
        }
        presetManager.savePreset(name, currentPitch, currentVolume, eqBandsProgress, eqBandsEnabled)
        Toast.makeText(this, "Пресет '$name' збережено!", Toast.LENGTH_SHORT).show()
        presetNameInput.setText("")
        updatePresetsSpinner()
    }

    private fun updatePresetsSpinner() {
        val names = presetManager.getPresetNames()
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, names)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        presetSpinner.adapter = adapter
    }

    private fun loadSelectedPreset() {
        val selected = presetSpinner.selectedItem?.toString() ?: return
        if (selected == "Немає пресетів") return

        val data = presetManager.loadPreset(selected) ?: return
        currentPitch = data.pitch
        currentVolume = data.volume

        pitchSeekBar.progress = ((currentPitch * 100) - 50).toInt()
        volumeSeekBar.progress = (currentVolume * 100).toInt()

        eqBandsProgress.clear()
        eqBandsProgress.putAll(data.bandsProgress)

        eqBandsEnabled.clear()
        eqBandsEnabled.putAll(data.bandsEnabled)

        currentEqualizer?.let { playerManager.applyEqualizerBands(it, eqBandsProgress, eqBandsEnabled) }
        Toast.makeText(this, "Пресет '$selected' завантажено!", Toast.LENGTH_SHORT).show()
    }

    private fun checkPermissions() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), PERMISSION_REQUEST_CODE)
        }
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "audio/*" }
        startActivityForResult(intent, PICK_AUDIO_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_AUDIO_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            val uri = data.data ?: return
            val tempFile = File(externalCacheDir, "imported_audio.wav")
            try {
                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempFile).use { output -> input.copyTo(output) }
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
        if (currentAudioPath.isEmpty()) return
        playerManager.playAudio(
            path = currentAudioPath,
            pitch = currentPitch,
            volume = currentVolume,
            onEqualizerReady = { eq -> initEqualizerUI(eq) },
            onReplayNeeded = { playAudio() }
        )
    }

    private fun saveProcessedAudio() {
        try {
            val sourceFile = File(currentAudioPath)
            if (!sourceFile.exists()) {
                Toast.makeText(this, "Немає файлу для збереження!", Toast.LENGTH_SHORT).show()
                return
            }

            val customName = fileNameInput.text.toString().trim()
            val fileName = if (customName.isNotEmpty()) "$customName.wav" else "VoicePitcher_${System.currentTimeMillis()}.wav"

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "audio/x-wav")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }

            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                contentResolver.openOutputStream(uri).use { output ->
                    sourceFile.inputStream().use { input -> input.copyTo(output!!) }
                }
                statusText.text = "✅ Збережено у Downloads: $fileName"
                Toast.makeText(this, "Успішно збережено!", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Помилка збереження: ${e.message}")
            statusText.text = "❌ Помилка збереження"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        recorderManager.stopRecording()
        playerManager.release()
    }
}
