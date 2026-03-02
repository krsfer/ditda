package com.morse.master.coach

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.UUID

internal class CoachSpeechQueue(
    private val maxSize: Int = 8
) {
    private val items = ArrayDeque<String>()

    fun enqueue(message: String) {
        if (message.isBlank()) return
        while (items.size >= maxSize) {
            items.removeFirst()
        }
        items.addLast(message)
    }

    fun drain(consumer: (String) -> Unit) {
        while (items.isNotEmpty()) {
            consumer(items.removeFirst())
        }
    }

    fun clear() {
        items.clear()
    }
}

class AndroidTextToSpeechCoachNarrationGateway(
    context: Context,
    locale: Locale = Locale.US
) : CoachNarrationGateway {
    private val lock = Any()
    private val pending = CoachSpeechQueue()
    private val busyState = CoachNarrationBusyState()
    @Volatile
    private var ready = false

    private val textToSpeech: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        onInitialized(status, locale)
    }

    init {
        textToSpeech.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit

                override fun onDone(utteranceId: String?) {
                    markUtteranceFinished()
                }

                @Deprecated("Deprecated in SDK")
                override fun onError(utteranceId: String?) {
                    markUtteranceFinished()
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    markUtteranceFinished()
                }
            }
        )
    }

    override fun speak(message: String) {
        val normalized = message.trim()
        if (normalized.isEmpty()) return

        synchronized(lock) {
            if (!ready) {
                pending.enqueue(normalized)
                busyState.onMessageQueued()
                return
            }
            speakInternal(normalized)
        }
    }

    override fun isSpeaking(): Boolean = busyState.isBusy() || textToSpeech.isSpeaking

    fun shutdown() {
        synchronized(lock) {
            ready = false
            busyState.clear()
            pending.clear()
            textToSpeech.stop()
            textToSpeech.shutdown()
        }
    }

    private fun onInitialized(status: Int, locale: Locale) {
        synchronized(lock) {
            if (status != TextToSpeech.SUCCESS) {
                ready = false
                busyState.clear()
                pending.clear()
                return
            }

            val languageStatus = textToSpeech.setLanguage(locale)
            ready = languageStatus != TextToSpeech.LANG_MISSING_DATA &&
                languageStatus != TextToSpeech.LANG_NOT_SUPPORTED

            if (ready) {
                pending.drain { message ->
                    busyState.onMessageDequeuedForPlayback()
                    speakInternal(message)
                }
            } else {
                busyState.clear()
                pending.clear()
            }
        }
    }

    private fun speakInternal(message: String) {
        busyState.onUtteranceSubmitted()
        val speakResult = textToSpeech.speak(
            message,
            TextToSpeech.QUEUE_ADD,
            null,
            "coach-${UUID.randomUUID()}"
        )
        if (speakResult == TextToSpeech.ERROR) {
            busyState.onUtteranceFinished()
        }
    }

    private fun markUtteranceFinished() {
        busyState.onUtteranceFinished()
    }
}
