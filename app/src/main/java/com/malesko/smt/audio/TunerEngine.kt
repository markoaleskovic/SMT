package com.malesko.smt.audio

import android.media.*
import kotlin.concurrent.thread
import androidx.annotation.RequiresPermission
import android.Manifest


//TODO Automatic check for the microphone format aka the use float boolean.
class TunerEngine(
    private val sampleRate: Int = 44100,
    private val frameSize: Int = 4096,
    private val useFloat: Boolean = false,
    private val onPitch: (Float) -> Unit
) {
    @Volatile
    private var running = false
    private var worker: Thread? = null
    private var audioRecord: AudioRecord? = null
    private var yin: NativeYin? = null

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
        // Unblock read() quickly
        audioRecord?.stop()
        worker?.join(500) // wait briefly for thread to exit
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

        // initial fill
        var filled = 0
        while (running && filled < frameSize) {
            val n = rec.read(temp, 0, minOf(hop, frameSize - filled), AudioRecord.READ_BLOCKING)
            if (n > 0) {
                System.arraycopy(temp, 0, rolling, filled, n)
                filled += n
            }
        }


        //var frameCount = 0
        while (running) {
//
//            if (frameCount++ % 20 == 0) {
//                var peak = 0f
//                for (v in rolling) peak = maxOf(peak, kotlin.math.abs(v))
//                android.util.Log.d("TunerEngine", "peak=$peak")
//            }

            val pitch = yin?.process(rolling) ?: -1f
//            android.util.Log.d("TunerEngine", "pitchRaw=$pitch")
            if (pitch > 0f) onPitch(pitch)

            // shift + read hop
            System.arraycopy(rolling, hop, rolling, 0, frameSize - hop)

            var got = 0
            while (running && got < hop) {
                val n = rec.read(temp, got, hop - got, AudioRecord.READ_BLOCKING)
//                android.util.Log.d("TunerEngine", "read n=$n")
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

        var filled = 0
        while (running && filled < frameSize) {
            val n = rec.read(tempS, 0, minOf(hop, frameSize - filled))
            if (n > 0) {
                for (i in 0 until n) rolling[filled + i] = tempS[i] / 32768f
                filled += n
            }
        }

//        var frameCount = 0
        while (running) {
//
//            if (frameCount++ % 20 == 0) {
//                var peak = 0f
//                for (v in rolling) peak = maxOf(peak, kotlin.math.abs(v))
//                android.util.Log.d("TunerEngine", "peak=$peak")
//            }
            val pitch = yin?.process(rolling) ?: -1f
//            android.util.Log.d("TunerEngine", "pitchRaw=$pitch")
            if (pitch > 0f) onPitch(pitch)
            System.arraycopy(rolling, hop, rolling, 0, frameSize - hop)

            var got = 0
            while (running && got < hop) {
                val n = rec.read(tempS, got, hop - got)
//                android.util.Log.d("TunerEngine", "read n=$n")
                if (n > 0) got += n
            }
            for (i in 0 until hop) rolling[frameSize - hop + i] = tempS[i] / 32768f
        }
    }

    private fun cleanup() {
        audioRecord?.release()
        audioRecord = null
        yin?.close()
        yin = null
    }
}
