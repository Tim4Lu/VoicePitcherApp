package com.example.voicepitcher

import android.media.MediaPlayer
import android.media.PlaybackParams
import android.media.audiofx.Equalizer
import android.util.Log
import java.io.File
import java.io.IOException

class AudioPlayerManager {
    private val TAG = "AudioPlayerLog"
    private var mediaPlayer: MediaPlayer? = null
    private var equalizer: Equalizer? = null

    fun playAudio(path: String, pitch: Float, volume: Float, onEqualizerReady: (Equalizer) -> Unit, onReplayNeeded: () -> Unit) {
        val file = File(path)
        if (!file.exists()) return

        try {
            release()

            mediaPlayer = MediaPlayer().apply {
                setDataSource(path)
                prepare()

                isLooping = false
                setOnCompletionListener {
                    onReplayNeeded()
                }

                val params = PlaybackParams()
                params.pitch = pitch
                playbackParams = params

                // Фіксуємо гучність відразу при старті
                setVolume(volume, volume)
                start()
            }

            mediaPlayer?.audioSessionId?.let { sessionId ->
                equalizer = Equalizer(0, sessionId).apply {
                    enabled = true
                    onEqualizerReady(this)
                }
            }

        } catch (e: IOException) {
            Log.e(TAG, "Помилка playAudio: ${e.message}")
        }
    }

    fun setPitch(pitch: Float) {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    val params = PlaybackParams()
                    params.pitch = pitch
                    it.playbackParams = params
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Помилка зміни pitch: ${e.message}")
        }
    }

    fun setVolume(volume: Float) {
        try {
            // Забезпечуємо підтримку підсилення (Gain)
            val clampedVol = volume.coerceIn(0.0f, 2.0f)
            mediaPlayer?.setVolume(clampedVol, clampedVol)
        } catch (e: Exception) {
            Log.e(TAG, "Помилка зміни volume: ${e.message}")
        }
    }

    fun applyEqualizerBands(eq: Equalizer, progressMap: Map<Int, Int>, enabledMap: Map<Int, Boolean>) {
        try {
            val numBands = eq.numberOfBands.toInt()
            val minLevel = eq.bandLevelRange[0].toInt()
            val maxLevel = eq.bandLevelRange[1].toInt()

            for (i in 0 until numBands) {
                val enabled = enabledMap[i] ?: true
                if (!enabled) {
                    eq.setBandLevel(i.toShort(), 0)
                    continue
                }

                val progress = progressMap[i] ?: 50
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
            Log.e(TAG, "Помилка applyEqualizerBands: ${e.message}")
        }
    }

    fun release() {
        mediaPlayer?.release()
        mediaPlayer = null
        equalizer?.release()
        equalizer = null
    }
}
