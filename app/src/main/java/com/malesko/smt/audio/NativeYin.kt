package com.malesko.smt.audio

class NativeYin(sampleRate: Int, bufferSize: Int) {
    private var handle: Long = nativeCreate(sampleRate, bufferSize)

    fun process(frame: FloatArray): Float {
        return nativeProcess(handle, frame)
    }

    fun close() {
        if (handle != 0L) {
            nativeDestroy(handle)
            handle = 0L
        }
    }

    private external fun nativeCreate(sampleRate: Int, bufferSize: Int): Long
    private external fun nativeDestroy(handle: Long)
    private external fun nativeProcess(handle: Long, frame: FloatArray): Float

    companion object {
        init { System.loadLibrary("smt") }
    }
}
