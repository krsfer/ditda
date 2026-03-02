package com.morse.master.coach

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class AndroidSpeechRecognizerGateway(
    context: Context
) : SpeechRecognizerGateway {
    private val appContext = context.applicationContext
    private val mutex = Mutex()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null

    override suspend fun listenForAnswer(timeoutMs: Long): SpeechRecognitionResult? {
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            return null
        }

        return mutex.withLock {
            withContext(Dispatchers.Main) {
                withTimeoutOrNull(timeoutMs.coerceAtLeast(250L)) {
                    suspendCancellableCoroutine { continuation ->
                        val recognizer = speechRecognizer ?: SpeechRecognizer
                        .createSpeechRecognizer(appContext)
                        .also { speechRecognizer = it }

                    val startedAt = SystemClock.elapsedRealtime()

                    val listener = object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) { android.util.Log.d("DitDaSpeech", "onReadyForSpeech") }
                        override fun onBeginningOfSpeech() { android.util.Log.d("DitDaSpeech", "onBeginningOfSpeech") }
                        override fun onRmsChanged(rmsdB: Float) = Unit
                        override fun onBufferReceived(buffer: ByteArray?) = Unit
                        override fun onEndOfSpeech() { android.util.Log.d("DitDaSpeech", "onEndOfSpeech") }
                        override fun onPartialResults(partialResults: Bundle?) = Unit
                        override fun onEvent(eventType: Int, params: Bundle?) = Unit

                        override fun onError(error: Int) {
                            android.util.Log.e("DitDaSpeech", "onError: $error")
                            if (continuation.isActive) {
                                continuation.resume(null)
                            }
                        }

                        override fun onResults(results: Bundle?) {
                            if (!continuation.isActive) return
                            val transcripts = results
                                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                .orEmpty()
                            val token = transcripts.firstOrNull()?.trim().orEmpty()
                            android.util.Log.d("DitDaSpeech", "onResults: $token")
                            if (token.isBlank()) {
                                continuation.resume(null)
                                return
                            }
                            val confidenceScores = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
                            val confidence = confidenceScores?.firstOrNull()?.toDouble() ?: 0.0
                            val latencyMs = (SystemClock.elapsedRealtime() - startedAt).toInt()
                            continuation.resume(
                                SpeechRecognitionResult(
                                    token = token,
                                    confidence = confidence.coerceIn(0.0, 1.0),
                                    latencyMs = latencyMs.coerceAtLeast(0)
                                )
                            )
                        }
                    }

                    recognizer.setRecognitionListener(listener)
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(
                            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                            RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH
                        )
                        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, appContext.packageName)
                    }

                    try {
                        android.util.Log.d("DitDaSpeech", "startListening")
                        recognizer.startListening(intent)
                    } catch (e: SecurityException) {
                        android.util.Log.e("DitDaSpeech", "SecurityException", e)
                        if (continuation.isActive) continuation.resume(null)
                        return@suspendCancellableCoroutine
                    } catch (e: IllegalStateException) {
                        android.util.Log.e("DitDaSpeech", "IllegalStateException", e)
                        if (continuation.isActive) continuation.resume(null)
                        return@suspendCancellableCoroutine
                    }

                    continuation.invokeOnCancellation {
                        postToMainThread {
                            runCatching { recognizer.cancel() }
                        }
                    }
                }
            }
        }
    }
    
    }

    fun shutdown() {
        val recognizer = speechRecognizer ?: return
        postToMainThread {
            runCatching { recognizer.cancel() }
            runCatching { recognizer.destroy() }
            speechRecognizer = null
        }
    }

    private fun postToMainThread(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            mainHandler.post { action() }
        }
    }
}
