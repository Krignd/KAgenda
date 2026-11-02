package com.kstudio.agenda.data

import kotlinx.coroutines.Job

internal class LocalAiCancellation(private val job: Job) {
    @Suppress("unused") // Called from JNI on the inference thread.
    fun isCancelled(): Boolean = !job.isActive
}

internal object LocalAiNative {
    init { System.loadLibrary("kagenda_ai") }
    external fun generate(modelPath: ByteArray, prompt: ByteArray, opsGrammar: Boolean, signal: LocalAiCancellation, timeoutMillis: Int): ByteArray
}
