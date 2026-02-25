package com.morse.master.audio

import android.content.Context
import android.media.AudioManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.morse.master.domain.MorseTiming
import com.morse.master.ui.DitDaSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

class MorsePracticePlayer(
    private val context: Context,
    private val sampleRate: Int = 44100,
    private val planner: MorseSymbolPlanner = MorseSymbolPlanner(),
    private val pcmRenderer: MorsePcmRenderer = MorsePcmRenderer(sampleRate = sampleRate)
) {
    suspend fun playCharacters(characters: List<Char>, settings: DitDaSettings) = withContext(Dispatchers.Default) {
        val timing = MorseTiming(settings.characterWpm, settings.effectiveWpm)
        characters.forEachIndexed { index, char ->
            playCharacterInternal(char, settings)
            if (index < characters.lastIndex) {
                Thread.sleep(timing.interCharGapMs.toLong())
            }
        }
    }

    suspend fun playCharacter(character: Char, settings: DitDaSettings) = withContext(Dispatchers.Default) {
        playCharacterInternal(character, settings)
    }

    private fun playCharacterInternal(character: Char, settings: DitDaSettings) {
        val plan = planner.planFor(character, settings)
        if (plan.isEmpty()) return

        val totalDurationMs = plan.sumOf {
            when (it) {
                is MorseSegment.Gap -> it.durationMs
                is MorseSegment.Tone -> it.durationMs
            }
        }

        if (settings.vibrationEnabled) {
            vibrate(plan)
        }

        if (settings.soundEnabled) {
            val pcm = pcmRenderer.render(plan, settings)
            playPcm(pcm)
        } else if (totalDurationMs > 0) {
            Thread.sleep(totalDurationMs.toLong())
        }
    }

    private fun playPcm(pcm: ShortArray) {
        if (pcm.isEmpty()) return
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(max(pcm.size * 2, 1))
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setSessionId(AudioManager.AUDIO_SESSION_ID_GENERATE)
            .build()

        try {
            track.write(pcm, 0, pcm.size)
            track.play()
            waitForPlaybackCompletion(track, pcm.size)
        } finally {
            track.stop()
            track.release()
        }
    }

    private fun waitForPlaybackCompletion(track: AudioTrack, totalSamples: Int) {
        val expectedMs = (totalSamples * 1000L) / sampleRate
        val deadline = SystemClock.elapsedRealtime() + expectedMs + 250L
        while (SystemClock.elapsedRealtime() < deadline) {
            if (track.playbackHeadPosition >= totalSamples) {
                return
            }
            Thread.sleep(4)
        }
    }

    private fun vibrate(plan: List<MorseSegment>) {
        val vibrator = resolveVibrator() ?: return
        if (!vibrator.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val timings = LongArray(plan.size + 1)
            val amplitudes = IntArray(plan.size + 1)
            timings[0] = 0L
            amplitudes[0] = 0
            plan.forEachIndexed { index, segment ->
                timings[index + 1] = when (segment) {
                    is MorseSegment.Gap -> segment.durationMs.toLong()
                    is MorseSegment.Tone -> segment.durationMs.toLong()
                }
                amplitudes[index + 1] = when (segment) {
                    is MorseSegment.Gap -> 0
                    is MorseSegment.Tone -> VibrationEffect.DEFAULT_AMPLITUDE
                }
            }
            vibrator.vibrate(
                VibrationEffect.createWaveform(timings, amplitudes, -1)
            )
        }
    }

    private fun resolveVibrator(): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(VibratorManager::class.java)
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }
}
