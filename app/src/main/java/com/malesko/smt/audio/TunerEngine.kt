package com.malesko.smt.audio

import android.media.*
import androidx.annotation.RequiresPermission
import android.Manifest
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt


class TunerEngine(
    private val sampleRate: Int = 44100,
    private val frameSize: Int = 4096,
    private val preferFloat: Boolean = false,
    private var usingFloat: Boolean = false,
    private val onState: (TunerState) -> Unit
) {
    var frameCount = 0
    private var consecutiveZeroPeakFrames = 0
    private var floatRestartAttempts = 0


    @Volatile
    private var running = false
    private var worker: Thread? = null
    private var audioRecord: AudioRecord? = null
    private var yin: NativeYin? = null

    private val lastStates = ArrayDeque<TunerState>()

    private companion object {
        private const val SMOOTH_WINDOW = 5
        private const val LEVEL_THRESHOLD = 0.005f
        private const val ZERO_PEAK_FRAMES_TO_RESTART = 120
        private const val ZERO_PEAK_EPS = 1e-6f
        private const val MAX_FLOAT_RESTARTS = 2
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start() {
        if (running) return
        running = true

        worker = Thread {
            android.util.Log.d("TunerEngine", "worker thread started")
            try {
                yin = NativeYin(sampleRate, frameSize)
                android.util.Log.d("TunerEngine", "after NativeYin create")

                usingFloat = preferFloat
                consecutiveZeroPeakFrames = 0
                floatRestartAttempts = 0

                // If preferFloat=false run 16-bit as before.
                // If preferFloat=true, only restart float a few times.
                while (running) {
                    setupAudioRecord(usingFloat)
                    android.util.Log.d(
                        "TunerEngine",
                        "after setupAudioRecord usingFloat=$usingFloat state=${audioRecord?.state}"
                    )

                    audioRecord!!.startRecording()
                    android.util.Log.d(
                        "TunerEngine",
                        "after startRecording recordingState=${audioRecord?.recordingState}"
                    )

                    val shouldRestartFloat =
                        if (usingFloat) loopFloatWithRestartCheck() else runLoop16Bit()

                    safeStopReleaseAudioRecord()

                    if (!running) break

                    // Only applies to float mode.
                    if (usingFloat && shouldRestartFloat) {
                        if (floatRestartAttempts < MAX_FLOAT_RESTARTS) {
                            floatRestartAttempts++
                            android.util.Log.w(
                                "TunerEngine",
                                "Float stuck -> restarting float (${floatRestartAttempts}/$MAX_FLOAT_RESTARTS)"
                            )
                            consecutiveZeroPeakFrames = 0
                            // loop continues; we rebuild AudioRecord as float again
                            continue
                        } else {
                            android.util.Log.w(
                                "TunerEngine",
                                "Float stuck -> max restarts reached; staying silent"
                            )
                            // Give up: keep running but no more restarts. We just re-enter float with no recover attempts.
                            // Easiest: keep going, but loopFloatWithRestartCheck() will no longer request restarts.
                            // So: just keep usingFloat=true and continue.
                            consecutiveZeroPeakFrames = 0
                            continue
                        }
                    }
                    // If loop returned without requesting restart, exit.
                    break
                }
            } catch (t: Throwable) {
                android.util.Log.e("TunerEngine", "worker crashed", t)
            } finally {
                android.util.Log.d("TunerEngine", "worker finally cleanup")
                cleanup()
            }
        }.also { it.name = "TunerEngineThread"; it.start() }
    }


    fun stop() {
        running = false
        try {
            audioRecord?.stop()
        } catch (_: Throwable) {
        }
        worker?.join(500)
        worker = null
        cleanup()
    }

    private fun safeStopReleaseAudioRecord() {
        val ar = audioRecord ?: return
        try {
            if (ar.recordingState == AudioRecord.RECORDSTATE_RECORDING) ar.stop()
        } catch (_: Throwable) {
        }
        try {
            ar.release()
        } catch (_: Throwable) {
        }
        audioRecord = null
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun setupAudioRecord(useFloatEncoding: Boolean) {
        android.util.Log.d("TunerEngine", "setupAudioRecord() enter useFloat=$useFloatEncoding")

        val encoding = if (useFloatEncoding) {
            AudioFormat.ENCODING_PCM_FLOAT
        } else {
            AudioFormat.ENCODING_PCM_16BIT
        }

        val minBufBytes = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            encoding
        )
        if (minBufBytes <= 0) {
            throw IllegalStateException("getMinBufferSize failed: $minBufBytes")
        }

        android.util.Log.d(
            "TunerEngine",
            "minBufBytes=$minBufBytes encoding=$encoding sampleRate=$sampleRate"
        )

        val format = AudioFormat.Builder()
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
            .setEncoding(encoding)
            .build()

        audioRecord = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.MIC)
            .setAudioFormat(format)
            .setBufferSizeInBytes(minBufBytes * 4)
            .build()

        // Validate init
        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            throw IllegalStateException("AudioRecord not initialized, state=${audioRecord?.state}")
        }
    }

    private fun loopFloatWithRestartCheck(): Boolean {
        val rec = audioRecord ?: return false
        val hop = frameSize / 2
        val rolling = FloatArray(frameSize)
        val temp = FloatArray(hop)

        // Initial fill
        var filled = 0
        while (running && filled < frameSize) {
            val n = rec.read(temp, 0, minOf(hop, frameSize - filled), AudioRecord.READ_BLOCKING)
            if (n < 0) {
                android.util.Log.e("TunerEngine", "AudioRecord.read error=$n")
                // Treat as "needs restart" in float mode
                return true
            }
            if (n > 0) {
                System.arraycopy(temp, 0, rolling, filled, n)
                filled += n
            }
        }

        while (running) {
            frameCount++

            var peak = 0f
            for (v in rolling) {
                val a = kotlin.math.abs(v)
                if (a > peak) peak = a
            }

            // Stuck-zero detection
            if (peak <= ZERO_PEAK_EPS) {
                consecutiveZeroPeakFrames++
            } else {
                consecutiveZeroPeakFrames = 0
            }

            if (frameCount % 50 == 0) {
                android.util.Log.d(
                    "TunerEngine",
                    "loopFloat frame=$frameCount peak=$peak zeroRun=$consecutiveZeroPeakFrames restarts=$floatRestartAttempts"
                )
            }

            // If we're stuck AND we still have restart budget, request restart.
            // If restart budget is exhausted, do NOT request restart anymore -> "stay silent".
            if (consecutiveZeroPeakFrames >= ZERO_PEAK_FRAMES_TO_RESTART) {
                if (floatRestartAttempts < MAX_FLOAT_RESTARTS) {
                    return true
                } else {
                    // Give up: just treat as silence and keep going, no restart request.
                    // Keep consecutiveZeroPeakFrames capped so it doesn't overflow.
                    // later throw error
                    consecutiveZeroPeakFrames = ZERO_PEAK_FRAMES_TO_RESTART
                }
            }

            // Silence gate (works both for real silence and "given up" stuck zeros)
            if (peak < LEVEL_THRESHOLD) {
                System.arraycopy(rolling, hop, rolling, 0, frameSize - hop)

                var got = 0
                while (running && got < hop) {
                    val n = rec.read(temp, got, hop - got, AudioRecord.READ_BLOCKING)
                    if (n < 0) {
                        android.util.Log.e("TunerEngine", "AudioRecord.read error=$n")
                        // same rule: request restart only if budget remains
                        return (floatRestartAttempts < MAX_FLOAT_RESTARTS)
                    }
                    if (n > 0) got += n
                }

                System.arraycopy(temp, 0, rolling, frameSize - hop, hop)
                continue
            }

            val pitch = yin?.process(rolling) ?: -1f
            if (frameCount % 50 == 0) android.util.Log.d("TunerEngine", "pitchRaw=$pitch")

            if (pitch > 0f) {
                val smoothed = smoothState(buildTunerState(pitch))
                onState(smoothed)
            }

            System.arraycopy(rolling, hop, rolling, 0, frameSize - hop)

            var got = 0
            while (running && got < hop) {
                val n = rec.read(temp, got, hop - got, AudioRecord.READ_BLOCKING)
                if (n < 0) {
                    android.util.Log.e("TunerEngine", "AudioRecord.read error=$n")
                    return (floatRestartAttempts < MAX_FLOAT_RESTARTS)
                }
                if (n > 0) got += n
            }
            System.arraycopy(temp, 0, rolling, frameSize - hop, hop)
        }

        return false
    }


    private fun runLoop16Bit(): Boolean {
        val rec = audioRecord ?: return false
        val hop = frameSize / 2
        val rolling = FloatArray(frameSize)
        val tempS = ShortArray(hop)

        var filled = 0
        while (running && filled < frameSize) {
            val n = rec.read(tempS, 0, minOf(hop, frameSize - filled))
            if (n < 0) {
                android.util.Log.e("TunerEngine", "AudioRecord.read error=$n")
                return false
            }
            if (n > 0) {
                for (i in 0 until n) rolling[filled + i] = tempS[i] / 32768.0f
                filled += n
            }
        }

        while (running) {
            frameCount++

            var peak = 0f
            for (v in rolling) {
                val a = kotlin.math.abs(v)
                if (a > peak) peak = a
            }

            if (frameCount % 50 == 0) {
                android.util.Log.d("TunerEngine", "loop16Bit frame=$frameCount peak=$peak")
            }

            if (peak < LEVEL_THRESHOLD) {
                System.arraycopy(rolling, hop, rolling, 0, frameSize - hop)

                var got = 0
                while (running && got < hop) {
                    val n = rec.read(tempS, got, hop - got)
                    if (n < 0) {
                        android.util.Log.e("TunerEngine", "AudioRecord.read error=$n")
                        return false
                    }
                    if (n > 0) got += n
                }
                for (i in 0 until hop) {
                    rolling[frameSize - hop + i] = tempS[i] / 32768.0f
                }
                continue
            }

            val pitch = yin?.process(rolling) ?: -1f
            if (frameCount % 50 == 0) android.util.Log.d("TunerEngine", "pitchRaw=$pitch")

            if (pitch > 0f) {
                val smoothed = smoothState(buildTunerState(pitch))
                onState(smoothed)
            }

            System.arraycopy(rolling, hop, rolling, 0, frameSize - hop)

            var got = 0
            while (running && got < hop) {
                val n = rec.read(tempS, got, hop - got)
                if (n < 0) {
                    android.util.Log.e("TunerEngine", "AudioRecord.read error=$n")
                    return false
                }
                if (n > 0) got += n
            }
            for (i in 0 until hop) {
                rolling[frameSize - hop + i] = tempS[i] / 32768.0f
            }
        }

        return false
    }


    private fun buildTunerState(pitchHz: Float): TunerState {
        // MIDI from frequency: n = 69 + 12 * log2(f/440)
        val midiExact = 69.0 + 12.0 * log2(pitchHz / 440.0)
        val midiRounded = midiExact.roundToInt()

        val names = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
        val noteIndex = ((midiRounded % 12) + 12) % 12
        val octave = (midiRounded / 12) - 1
        val noteName = "${names[noteIndex]}$octave"

        // Pure note frequency for that MIDI
        val targetFreq = 440.0 * 2.0.pow((midiRounded - 69) / 12.0)

        // Cents difference
        val centsOff = (1200.0 * log2(pitchHz / targetFreq)).toFloat()

        return TunerState(
            pitchHz = pitchHz,
            noteName = noteName,
            centsOff = centsOff
        )
    }

    private fun smoothState(newState: TunerState): TunerState {
        lastStates.addLast(newState)
        if (lastStates.size > SMOOTH_WINDOW) lastStates.removeFirst()

        if (lastStates.isEmpty()) return newState

        val avgHz = lastStates.map { it.pitchHz }.average().toFloat()

        val centsSorted = lastStates.map { it.centsOff }.sorted()
        val medianCents = centsSorted[centsSorted.size / 2]

        val noteName = lastStates.last().noteName

        return TunerState(
            pitchHz = avgHz,
            noteName = noteName,
            centsOff = medianCents
        )
    }

    private fun cleanup() {
        audioRecord?.release()
        audioRecord = null
        yin?.close()
        yin = null
    }
}
