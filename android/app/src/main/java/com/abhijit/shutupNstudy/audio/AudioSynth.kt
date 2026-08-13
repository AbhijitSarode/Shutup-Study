package com.abhijit.shutupNstudy.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

object AudioSynth {
    private const val TAG = "AudioSynth"
    private const val SAMPLE_RATE = 22050
    private val scope = CoroutineScope(Dispatchers.Default)

    fun playSound(type: String) {
        scope.launch {
            try {
                when (type) {
                    "focus" -> playFocusChime()
                    "shortBreak" -> playShortBreakChime()
                    "longBreak" -> playLongBreakChime()
                    "click" -> playClickChirp()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error playing sound type: $type", e)
            }
        }
    }

    private fun playFocusChime() {
        // Bright, motivating ascending chime: C5, E5, G5, C6
        val frequencies = doubleArrayOf(523.25, 659.25, 783.99, 1046.50)
        val noteDelayMs = 120
        val noteDurationSecs = 0.35
        playSequence(frequencies, noteDelayMs, noteDurationSecs, isSine = true)
    }

    private fun playShortBreakChime() {
        // Soothing, calm descending tone (D5 to A4)
        val frequencies = doubleArrayOf(587.33, 440.00)
        val noteDelayMs = 180
        val noteDurationSecs = 0.45
        playSequence(frequencies, noteDelayMs, noteDurationSecs, isSine = false) // Triangle wave
    }

    private fun playLongBreakChime() {
        // Uplifting arpeggio (F4, C5, F5, A5)
        val frequencies = doubleArrayOf(349.23, 523.25, 698.46, 880.00)
        val noteDelayMs = 100
        val noteDurationSecs = 0.45
        playSequence(frequencies, noteDelayMs, noteDurationSecs, isSine = true)
    }

    private fun playClickChirp() {
        // Soft button click chirp (A4 triangle)
        val frequencies = doubleArrayOf(440.00)
        playSequence(frequencies, 0, 0.12, isSine = false)
    }

    private fun playSequence(frequencies: DoubleArray, delayMs: Int, durationSecs: Double, isSine: Boolean) {
        val totalSamples = (durationSecs * SAMPLE_RATE).toInt()
        val numNotes = frequencies.size
        
        // Compute total duration of the buffer
        // Note idx starts at delayMs * idx
        val delaySamples = (delayMs.toDouble() / 1000.0 * SAMPLE_RATE).toInt()
        val bufferLength = totalSamples + (numNotes - 1) * delaySamples
        val buffer = ShortArray(bufferLength)

        for (idx in frequencies.indices) {
            val freq = frequencies[idx]
            val offset = idx * delaySamples
            for (i in 0 until totalSamples) {
                val t = i.toDouble() / SAMPLE_RATE
                val envelope = if (t < 0.02) {
                    t / 0.02 // Attack
                } else {
                    // Exponential decay from 1.0 to 0.001
                    val decayRate = 5.0
                    val factor = Math.exp(-decayRate * (t - 0.02) / (durationSecs - 0.02))
                    factor.coerceIn(0.0, 1.0)
                }

                val angle = 2.0 * PI * freq * t
                val amplitude = 0.15 // Volume ceiling to keep it pleasant

                val rawValue = if (isSine) {
                    sin(angle)
                } else {
                    // Triangle wave: goes from -1 to 1 linearly
                    val fraction = (t * freq) % 1.0
                    if (fraction < 0.25) {
                        fraction * 4.0
                    } else if (fraction < 0.75) {
                        2.0 - fraction * 4.0
                    } else {
                        fraction * 4.0 - 4.0
                    }
                }

                val sampleVal = (rawValue * Short.MAX_VALUE * amplitude * envelope).toInt()
                val targetIndex = offset + i
                if (targetIndex < bufferLength) {
                    // Add samples to mix overlapping notes
                    buffer[targetIndex] = (buffer[targetIndex] + sampleVal).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                }
            }
        }

        // Play short array via AudioTrack
        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferLength * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        audioTrack.write(buffer, 0, bufferLength)
        audioTrack.play()
        
        // Clean up when playing is complete
        scope.launch {
            val waitTime = (bufferLength.toDouble() / SAMPLE_RATE * 1000).toLong() + 200
            kotlinx.coroutines.delay(waitTime)
            try {
                audioTrack.stop()
                audioTrack.release()
            } catch (e: Exception) {
                // Ignore stop/release errors
            }
        }
    }
}
