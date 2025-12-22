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
    private val useFloat: Boolean = false,
    private val onState: (TunerState) -> Unit
) {
    var frameCount = 0
    @Volatile
    private var running = false
    private var worker: Thread? = null
    private var audioRecord: AudioRecord? = null
    private var yin: NativeYin? = null

    private val lastStates = ArrayDeque<TunerState>()

    private companion object {
        private const val SMOOTH_WINDOW = 5
    }
    private val levelThreshold = 0.005f

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start() {
        if (running) return
        running = true

        worker = Thread {
            android.util.Log.d("TunerEngine", "worker thread started")
            try {
                setupAudioRecord()
                android.util.Log.d("TunerEngine", "after setupAudioRecord")
                yin = NativeYin(sampleRate, frameSize)
                android.util.Log.d("TunerEngine", "after NativeYin create")

                audioRecord!!.startRecording()
                android.util.Log.d("TunerEngine", "after startRecording")

                if (useFloat) loopFloat() else loop16Bit()
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
        } catch (_: Throwable) { }
        worker?.join(500)
        worker = null
        cleanup()
    }


    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun setupAudioRecord() {
        android.util.Log.d("TunerEngine", "setupAudioRecord() enter")
        val encoding =
            if (useFloat) AudioFormat.ENCODING_PCM_FLOAT else AudioFormat.ENCODING_PCM_16BIT
        val minBufBytes = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, encoding
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

        android.util.Log.d("TunerEngine", "format =$format")

        audioRecord = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.MIC)
            .setAudioFormat(format)
            .setBufferSizeInBytes(minBufBytes * 2)
            .build()
    }

    private fun loopFloat() {
        val rec = audioRecord ?: return
        val hop = frameSize / 2
        val rolling = FloatArray(frameSize)
        val temp = FloatArray(hop)

        // Initial fill
        var filled = 0
        while (running && filled < frameSize) {
            val n = rec.read(temp, 0, minOf(hop, frameSize - filled), AudioRecord.READ_BLOCKING)
            if (n > 0) {
                System.arraycopy(temp, 0, rolling, filled, n)
                filled += n
            }
        }

        while (running) {
            frameCount++
            // 1) Level gate – ignore frames that are essentially silent
            var peak = 0f
            for (v in rolling) {
                val a = kotlin.math.abs(v)
                if (a > peak) peak = a
            }
            if (frameCount % 50 == 0) {
                android.util.Log.d("TunerEngine", "loopFloat frame=$frameCount peak=$peak")
            }
            if (peak < levelThreshold) {
                if (frameCount % 50 == 0) {
                    android.util.Log.d("TunerEngine", "peak below threshold, treating as silence")
                }
                // Shift + read next hop, but don’t call detector
                System.arraycopy(rolling, hop, rolling, 0, frameSize - hop)

                var got = 0
                while (running && got < hop) {
                    val n = rec.read(temp, got, hop - got, AudioRecord.READ_BLOCKING)
                    if (frameCount % 50 == 0) {
                        android.util.Log.d("TunerEngine", "read hop n=$n")
                    }
                    if (n > 0) got += n
                }
                System.arraycopy(temp, 0, rolling, frameSize - hop, hop)
                continue
            }

            // 2) Pitch detection
            val pitch = yin?.process(rolling) ?: -1f
            if (frameCount % 50 == 0) {
                android.util.Log.d("TunerEngine", "pitchRaw=$pitch")
            }
            if (pitch > 0f) {
                val state = buildTunerState(pitch)
                val smoothed = smoothState(state)
                onState(smoothed)
            }

            // 3) Advance buffer: shift left by hop, read hop new samples
            System.arraycopy(rolling, hop, rolling, 0, frameSize - hop)

            var got = 0
            while (running && got < hop) {
                val n = rec.read(temp, got, hop - got, AudioRecord.READ_BLOCKING)
                if (n > 0) got += n
            }
            System.arraycopy(temp, 0, rolling, frameSize - hop, hop)
        }
    }


    private fun loop16Bit() {
        val rec = audioRecord ?: return
        val hop = frameSize / 2
        val rolling = FloatArray(frameSize)
        val tempS = ShortArray(hop)

        // Initial fill
        var filled = 0
        while (running && filled < frameSize) {
            val n = rec.read(tempS, 0, minOf(hop, frameSize - filled))
            if (n > 0) {
                for (i in 0 until n) {
                    rolling[filled + i] = tempS[i] / 32768.0f
                }
                filled += n
            }
        }

        while (running) {
            // 1) Level gate
            var peak = 0f
            for (v in rolling) {
                val a = kotlin.math.abs(v)
                if (a > peak) peak = a
            }
            if (frameCount % 50 == 0) {
                android.util.Log.d("TunerEngine", "loop16Bit frame=$frameCount peak=$peak")
            }
            if (peak < levelThreshold) {
                if (frameCount % 50 == 0) {
                    android.util.Log.d("TunerEngine", "peak below threshold, treating as silence")
                }
                // Shift + read next hop, but don’t call detector
                System.arraycopy(rolling, hop, rolling, 0, frameSize - hop)

                var got = 0
                while (running && got < hop) {
                    val n = rec.read(tempS, got, hop - got)
                    if (frameCount % 50 == 0) {
                        android.util.Log.d("TunerEngine", "read hop n=$n")
                    }
                    if (n > 0) got += n
                }
                for (i in 0 until hop) {
                    rolling[frameSize - hop + i] = tempS[i] / 32768.0f
                }
                continue
            }

            // 2) Pitch detection
            val pitch = yin?.process(rolling) ?: -1f
            if (frameCount % 50 == 0) {
                android.util.Log.d("TunerEngine", "pitchRaw=$pitch")
            }
            if (pitch > 0f) {
                val state = buildTunerState(pitch)
                val smoothed = smoothState(state)
                onState(smoothed)
            }

            // 3) Advance buffer: shift left by hop, read hop new samples
            System.arraycopy(rolling, hop, rolling, 0, frameSize - hop)

            var got = 0
            while (running && got < hop) {
                val n = rec.read(tempS, got, hop - got)
                if (n > 0) got += n
            }
            for (i in 0 until hop) {
                rolling[frameSize - hop + i] = tempS[i] / 32768.0f
            }
        }
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
