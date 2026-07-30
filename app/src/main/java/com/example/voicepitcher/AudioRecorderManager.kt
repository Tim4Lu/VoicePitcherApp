package com.example.voicepitcher

import android.content.Context
import android.media.MediaRecorder
import android.util.Log
import java.io.File
import java.io.IOException

class AudioRecorderManager(private val context: Context) {
    private val TAG = "AudioRecorderLog"
    private var mediaRecorder: MediaRecorder? = null
    var isRecording = false
        private set

    fun startRecording(): String {
        val cacheDir = context.externalCacheDir?.absolutePath ?: context.cacheDir.absolutePath
        val outputPath = "$cacheDir/raw_record.m4a"
        
        mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(320000)
            setAudioSamplingRate(44100)
            setOutputFile(outputPath)

            try {
                prepare()
                start()
                isRecording = true
            } catch (e: IOException) {
                Log.e(TAG, "Помилка старт запису: ${e.message}")
            }
        }
        return outputPath
    }

    fun stopRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Помилка зупинки запису: ${e.message}")
        } finally {
            mediaRecorder = null
            isRecording = false
        }
    }
}
