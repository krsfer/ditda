@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.morse.master.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.PowerManager
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.morse.master.audio.MorsePracticePlayer
import com.morse.master.ai.CoachDecisionEngine
import com.morse.master.coach.AndroidSpeechRecognizerGateway
import com.morse.master.coach.AndroidTextToSpeechCoachNarrationGateway
import com.morse.master.coach.CoachState
import com.morse.master.coach.CoachVoiceCommand
import com.morse.master.coach.CommandParser
import com.morse.master.coach.PhoneticVocabulary
import com.morse.master.coach.PrefixWakePhraseGateway
import com.morse.master.coach.VoiceCoachCoordinator
import com.morse.master.coach.VoiceCoachSettings
import com.morse.master.domain.KochSequence
import com.morse.master.domain.MorseTiming
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val REPEAT_SLIDER_ENDLESS_VALUE = 11
private const val TEXT_PLAYBACK_PAUSE_POLL_MS = 60L
private const val TRAINING_SET_RESPONSE_TIMEOUT_MS = 1_500L
private const val TRAINING_SET_RESPONSE_POLL_MS = 40L
private const val PARTIAL_WAKE_LOCK_TAG = "com.morse.master:session"

internal fun maxTrainingLevels(): Int = KochSequence.full().size

internal fun totalTrainingSetIterations(repeatCount: Int): Int? = when (repeatCount) {
    TRAINING_SET_REPEAT_ENDLESS -> null
    else -> repeatCount.coerceIn(0, 10) + 1
}

internal fun interIterationPauseMs(timing: MorseTiming): Long =
    timing.interCharGapMs.toLong() * 2L

internal fun trainingSetRepeatSliderValue(repeatCount: Int): Float = when (repeatCount) {
    TRAINING_SET_REPEAT_ENDLESS -> REPEAT_SLIDER_ENDLESS_VALUE.toFloat()
    else -> repeatCount.coerceIn(0, 10).toFloat()
}

internal fun sliderValueToTrainingSetRepeatCount(sliderValue: Int): Int = when (sliderValue) {
    REPEAT_SLIDER_ENDLESS_VALUE -> TRAINING_SET_REPEAT_ENDLESS
    else -> sliderValue.coerceIn(0, 10)
}

internal fun trainingSetRepeatLabel(repeatCount: Int): String = when (repeatCount) {
    TRAINING_SET_REPEAT_ENDLESS -> "Endless"
    else -> "${repeatCount.coerceIn(0, 10)}x"
}

internal fun tabToPage(tab: AppTab): Int = when (tab) {
    AppTab.PRACTICE -> 0
    AppTab.TEXT -> 1
    AppTab.SETTINGS -> 2
}

internal fun pageToTab(page: Int): AppTab = when (page) {
    1 -> AppTab.TEXT
    2 -> AppTab.SETTINGS
    else -> AppTab.PRACTICE
}

internal fun normalizeTextPlaybackInput(
    input: String,
    allowedChars: Set<Char> = KochSequence.full().map { it.uppercaseChar() }.toSet()
): String {
    val normalized = StringBuilder()
    var shouldInsertSpace = false

    input.forEach { raw ->
        val char = raw.uppercaseChar()
        when {
            char in allowedChars -> {
                if (shouldInsertSpace && normalized.isNotEmpty()) {
                    normalized.append(' ')
                }
                normalized.append(char)
                shouldInsertSpace = false
            }

            raw.isWhitespace() -> {
                shouldInsertSpace = normalized.isNotEmpty()
            }
        }
    }

    return normalized.toString().trim()
}

internal fun isTextPlaybackStartEnabled(
    input: String,
    isPlaying: Boolean,
    textPlaybackActive: Boolean
): Boolean {
    return !isPlaying &&
        !textPlaybackActive &&
        normalizeTextPlaybackInput(input).isNotEmpty()
}

internal fun textPlaybackWordPauseMs(timing: MorseTiming): Long =
    timing.interCharGapMs.toLong() * 2L

internal fun textPlaybackHighlightedIndex(
    normalizedInput: String,
    currentIndex: Int?
): Int? {
    val safeIndex = currentIndex ?: return null
    if (safeIndex !in normalizedInput.indices) return null
    return safeIndex.takeIf { normalizedInput[it] != ' ' }
}

internal fun highlightedTextPlaybackPreview(
    normalizedInput: String,
    highlightedIndex: Int?,
    playedUpTo: Int,
    highlightColor: androidx.compose.ui.graphics.Color,
    playedColor: androidx.compose.ui.graphics.Color
): AnnotatedString {
    return buildAnnotatedString {
        append(normalizedInput)
        val rawPlayedEnd = highlightedIndex?.let { minOf(playedUpTo, it) } ?: playedUpTo
        val playedEnd = rawPlayedEnd.coerceIn(0, normalizedInput.length)
        for (i in 0 until playedEnd) {
            if (normalizedInput[i] != ' ') {
                addStyle(SpanStyle(background = playedColor), i, i + 1)
            }
        }
        if (highlightedIndex != null) {
            addStyle(
                style = SpanStyle(background = highlightColor),
                start = highlightedIndex,
                end = highlightedIndex + 1
            )
        }
    }
}

internal fun shouldRunVoiceCommandListener(
    activeTab: AppTab,
    handsFreeEnabled: Boolean,
    micPermissionGranted: Boolean,
    coachState: CoachState,
    voiceControlArmed: Boolean
): Boolean {
    return activeTab == AppTab.PRACTICE &&
        handsFreeEnabled &&
        micPermissionGranted &&
        voiceControlArmed &&
        coachState != CoachState.ROUND_ACTIVE
}

internal fun shouldRunVoiceRoundLoop(
    activeTab: AppTab,
    handsFreeEnabled: Boolean,
    micPermissionGranted: Boolean,
    coachState: CoachState
): Boolean {
    return activeTab == AppTab.PRACTICE &&
        handsFreeEnabled &&
        micPermissionGranted &&
        coachState == CoachState.ROUND_ACTIVE
}

internal fun shouldKeepScreenAwake(
    activeTab: AppTab,
    isPlaying: Boolean,
    coachState: CoachState
): Boolean {
    return shouldHoldPartialWakeLock(
        activeTab = activeTab,
        isPlaying = isPlaying,
        coachState = coachState
    )
}

internal fun shouldHoldPartialWakeLock(
    activeTab: AppTab,
    isPlaying: Boolean,
    coachState: CoachState
): Boolean {
    if (isPlaying) {
        return true
    }
    val coachSessionActive = coachState != CoachState.IDLE && coachState != CoachState.STOPPED
    return activeTab == AppTab.PRACTICE && coachSessionActive
}

internal enum class ExpertPathPhase {
    LEARNING,
    CLOSING,
    MASTERY,
    ULTRA,
    CUSTOM
}

internal fun isThirtyWpmExpertPresetActive(settings: DitDaSettings): Boolean {
    return settings.characterWpm == Expert30WpmPreset.characterWpm &&
        settings.effectiveWpm == Expert30WpmPreset.effectiveWpm &&
        settings.toneHz == Expert30WpmPreset.toneHz
}

internal fun expertPathPhase(settings: DitDaSettings): ExpertPathPhase {
    return when {
        settings.characterWpm == 30 && settings.effectiveWpm == 8 -> ExpertPathPhase.LEARNING
        settings.characterWpm == 30 && settings.effectiveWpm in 9..29 -> ExpertPathPhase.CLOSING
        settings.characterWpm == settings.effectiveWpm && settings.characterWpm >= 40 -> ExpertPathPhase.ULTRA
        settings.characterWpm == settings.effectiveWpm && settings.characterWpm >= 30 -> ExpertPathPhase.MASTERY
        else -> ExpertPathPhase.CUSTOM
    }
}

private fun expertPathPhaseSummary(phase: ExpertPathPhase): String {
    return when (phase) {
        ExpertPathPhase.LEARNING -> "Learning Phase (30/8)"
        ExpertPathPhase.CLOSING -> "Closing Phase (30/9 -> 30/29)"
        ExpertPathPhase.MASTERY -> "Mastery Phase (30/30)"
        ExpertPathPhase.ULTRA -> "Ultra Phase (40/40+)"
        ExpertPathPhase.CUSTOM -> "Custom Speed Profile"
    }
}

private fun gestaltLettersUnlocked(characters: List<Char>): Int {
    return characters
        .map { it.uppercaseChar() }
        .distinct()
        .count { it in 'A'..'Z' }
}

@Composable
fun DitDaApp(viewModel: DitDaViewModel = rememberDitDaViewModel()) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val player = remember(context) { MorsePracticePlayer(context) }
    val keepScreenAwake = shouldKeepScreenAwake(
        activeTab = state.activeTab,
        isPlaying = state.isPlaying,
        coachState = state.coachState
    )
    val holdPartialWakeLock = shouldHoldPartialWakeLock(
        activeTab = state.activeTab,
        isPlaying = state.isPlaying,
        coachState = state.coachState
    )
    val powerManager = remember(context) {
        context.getSystemService(PowerManager::class.java)
    }
    val partialWakeLock = remember(powerManager) {
        powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, PARTIAL_WAKE_LOCK_TAG)?.apply {
            setReferenceCounted(false)
        }
    }
    var micPermissionGranted by remember(context) {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        micPermissionGranted = granted
    }
    val pagerState = rememberPagerState(
        initialPage = tabToPage(state.activeTab),
        pageCount = { AppTab.entries.size }
    )

    val colorScheme = if (state.settings.darkMode) {
        darkColorScheme()
    } else {
        lightColorScheme()
    }

    LaunchedEffect(state.activeTab) {
        val targetPage = tabToPage(state.activeTab)
        if (pagerState.currentPage != targetPage) {
            pagerState.animateScrollToPage(targetPage)
        }
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { page ->
                viewModel.selectTab(pageToTab(page))
            }
    }

    LaunchedEffect(state.settings.handsFreeEnabled, micPermissionGranted) {
        if (state.settings.handsFreeEnabled && !micPermissionGranted) {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    SideEffect {
        view.keepScreenOn = keepScreenAwake
    }
    DisposableEffect(view) {
        onDispose {
            view.keepScreenOn = false
        }
    }
    DisposableEffect(partialWakeLock, holdPartialWakeLock) {
        if (holdPartialWakeLock && partialWakeLock != null && !partialWakeLock.isHeld) {
            try {
                partialWakeLock.acquire()
            } catch (_: SecurityException) {
                // App keeps running without a wake lock if permission is unavailable.
            }
        }
        onDispose {
            if (partialWakeLock != null && partialWakeLock.isHeld) {
                partialWakeLock.release()
            }
        }
    }

    LaunchedEffect(state.activeTab, state.settings.handsFreeEnabled, micPermissionGranted, state.coachState) {
        if (!shouldRunVoiceCommandListener(
                activeTab = state.activeTab,
                handsFreeEnabled = state.settings.handsFreeEnabled,
                micPermissionGranted = micPermissionGranted,
                coachState = state.coachState,
                voiceControlArmed = state.voiceControlArmed
            )
        ) return@LaunchedEffect

        while (isActive) {
            val current = viewModel.state.value
            if (!shouldRunVoiceCommandListener(
                    activeTab = current.activeTab,
                    handsFreeEnabled = current.settings.handsFreeEnabled,
                    micPermissionGranted = micPermissionGranted,
                    coachState = current.coachState,
                    voiceControlArmed = current.voiceControlArmed
                )
            ) {
                break
            }
            val command = viewModel.pollCoachVoiceCommand(timeoutMs = 4_000L)
            if (command == null) {
                delay(350L)
            }
        }
    }

    LaunchedEffect(state.activeTab, state.settings.handsFreeEnabled, micPermissionGranted, state.coachState) {
        if (!shouldRunVoiceRoundLoop(
                activeTab = state.activeTab,
                handsFreeEnabled = state.settings.handsFreeEnabled,
                micPermissionGranted = micPermissionGranted,
                coachState = state.coachState
            )
        ) return@LaunchedEffect

        while (isActive) {
            val current = viewModel.state.value
            if (!shouldRunVoiceRoundLoop(
                    activeTab = current.activeTab,
                    handsFreeEnabled = current.settings.handsFreeEnabled,
                    micPermissionGranted = micPermissionGranted,
                    coachState = current.coachState
                )
            ) {
                break
            }

            val timing = MorseTiming(
                characterWpm = current.settings.characterWpm,
                effectiveWpm = current.settings.effectiveWpm
            )
            val playSequence = viewModel.nextRandomizedTrainingSet()
            val attempts = mutableListOf<com.morse.master.coach.VoiceAttempt>()

            for ((index, char) in playSequence.withIndex()) {
                var loopState = viewModel.state.value
                if (loopState.coachState != CoachState.ROUND_ACTIVE) break

                viewModel.awaitCoachNarrationIdle()
                loopState = viewModel.state.value
                if (loopState.coachState != CoachState.ROUND_ACTIVE) break

                if (loopState.coachState != CoachState.ROUND_ACTIVE) break

                attempts += viewModel.captureCoachAttempt(char) { assistLevel ->
                    if (loopState.settings.soundEnabled) {
                        val playSettings = if (assistLevel == 2) {
                            loopState.settings.copy(
                                effectiveWpm = (loopState.settings.effectiveWpm - 2).coerceAtLeast(5)
                            )
                        } else {
                            loopState.settings
                        }
                        player.playCharacter(char, playSettings)
                    }
                }

                if (loopState.coachState != CoachState.ROUND_ACTIVE) break
                if (index < playSequence.lastIndex) {
                    delay(timing.interCharGapMs.toLong())
                }
            }

            if (attempts.isNotEmpty()) {
                viewModel.onCoachRoundCompleted(attempts)
            } else {
                break
            }
        }
    }

    MaterialTheme(colorScheme = colorScheme) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                Column {
                    Text(
                        text = "DitDa",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                    TabRow(
                        selectedTabIndex = pagerState.currentPage
                    ) {
                        Tab(
                            selected = pagerState.currentPage == tabToPage(AppTab.PRACTICE),
                            onClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage(tabToPage(AppTab.PRACTICE))
                                }
                            },
                            text = { Text("Practice") }
                        )
                        Tab(
                            selected = pagerState.currentPage == tabToPage(AppTab.TEXT),
                            onClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage(tabToPage(AppTab.TEXT))
                                }
                            },
                            text = { Text("Text") }
                        )
                        Tab(
                            selected = pagerState.currentPage == tabToPage(AppTab.SETTINGS),
                            onClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage(tabToPage(AppTab.SETTINGS))
                                }
                            },
                            text = { Text("Settings") }
                        )
                    }
                }
            }
        ) { paddingValues ->
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxSize()
            ) { page ->
                when (pageToTab(page)) {
                    AppTab.PRACTICE -> SessionScreen(
                        characters = state.currentCharacters,
                        modifier = Modifier.fillMaxSize(),
                        maxTrainingLevels = maxTrainingLevels(),
                        settings = state.settings,
                        nextCharacter = state.nextCharacter,
                        playbackMode = state.playbackMode,
                        currentIteration = state.currentIteration,
                        highlightedCharacter = state.highlightedCharacter,
                        problemCharacters = state.problemCharacters,
                        easyCharacters = state.easyCharacters,
                        adaptationDebugLine = state.adaptationDebugLine,
                        coachState = state.coachState,
                        sessionElapsedMs = state.sessionElapsedMs,
                        lastCoachMessage = state.lastCoachMessage,
                        voiceControlArmed = state.voiceControlArmed,
                        micPermissionGranted = micPermissionGranted,
                        onCharacterPressed = { char ->
                            scope.launch {
                                val current = viewModel.state.value
                                if (current.playbackMode == PlaybackMode.TRAINING_SET) {
                                    viewModel.onTrainingSetTap(
                                        actual = char,
                                        nowMs = System.currentTimeMillis()
                                    )
                                    return@launch
                                }

                                viewModel.setPlaybackMode(PlaybackMode.SINGLE_CHAR)
                                try {
                                    player.playCharacter(char, current.settings)
                                } finally {
                                    viewModel.setPlaybackMode(PlaybackMode.IDLE)
                                }
                            }
                        },
                        onPlaySetPressed = {
                            if (state.playbackMode == PlaybackMode.TRAINING_SET) {
                                viewModel.requestPlaybackStop()
                            } else {
                                val settingsSnapshot = state.settings
                                viewModel.clearPlaybackStopRequest()
                                viewModel.resetCurrentIteration()
                                viewModel.resetTrainingSetAdaptiveSession()
                                viewModel.setPlaybackMode(PlaybackMode.TRAINING_SET)
                                scope.launch {
                                    try {
                                        val totalIterations = totalTrainingSetIterations(
                                            settingsSnapshot.trainingSetRepeatCount
                                        )
                                        var repetition = 0
                                        playback@ while (totalIterations == null || repetition < totalIterations) {
                                            if (viewModel.isPlaybackStopRequested()) break@playback

                                            repetition += 1
                                            viewModel.setCurrentIteration(repetition)

                                            val playSequence = viewModel.nextTrainingSetForPlayback()
                                            for ((index, char) in playSequence.withIndex()) {
                                                if (viewModel.isPlaybackStopRequested()) break@playback
                                                val loopState = viewModel.state.value
                                                val loopSettings = loopState.settings
                                                val timing = MorseTiming(
                                                    characterWpm = loopSettings.characterWpm,
                                                    effectiveWpm = loopSettings.effectiveWpm
                                                )
                                                val preCorrection = viewModel.consumePendingCorrectionCharacter()
                                                if (preCorrection != null && loopSettings.soundEnabled) {
                                                    val correctionSettings = loopSettings.copy(
                                                        effectiveWpm = (loopSettings.effectiveWpm - 2).coerceAtLeast(5)
                                                    )
                                                    if (loopSettings.highlightPlaybackEnabled) {
                                                        viewModel.setHighlightedCharacter(preCorrection)
                                                    }
                                                    player.playCharacter(preCorrection, correctionSettings)
                                                    if (loopSettings.highlightPlaybackEnabled) {
                                                        viewModel.setHighlightedCharacter(null)
                                                    }
                                                }
                                                viewModel.clearExpectedTrainingCharacter()
                                                val aiHighlightTarget =
                                                    viewModel.shouldHighlightTrainingCharacter(char)

                                                if (aiHighlightTarget) {
                                                    viewModel.setHighlightedCharacter(char)
                                                }
                                                player.playCharacter(char, loopSettings)
                                                viewModel.beginExpectedTrainingCharacter(
                                                    character = char,
                                                    startedAtMs = System.currentTimeMillis()
                                                )

                                                while (
                                                    isActive &&
                                                    !viewModel.isPlaybackStopRequested() &&
                                                    !viewModel.shouldAdvanceTrainingSetCharacter(
                                                        nowMs = System.currentTimeMillis(),
                                                        timeoutMs = TRAINING_SET_RESPONSE_TIMEOUT_MS
                                                    )
                                                ) {
                                                    val pendingCorrection = viewModel.consumePendingCorrectionCharacter()
                                                    if (pendingCorrection != null && loopSettings.soundEnabled) {
                                                        val correctionSettings = loopSettings.copy(
                                                            effectiveWpm = (loopSettings.effectiveWpm - 2).coerceAtLeast(5)
                                                        )
                                                        if (aiHighlightTarget) {
                                                            viewModel.setHighlightedCharacter(pendingCorrection)
                                                        }
                                                        player.playCharacter(pendingCorrection, correctionSettings)
                                                        if (aiHighlightTarget) {
                                                            viewModel.setHighlightedCharacter(null)
                                                        }
                                                    } else {
                                                        delay(TRAINING_SET_RESPONSE_POLL_MS)
                                                    }
                                                }

                                                val correction = viewModel.consumePendingCorrectionCharacter()
                                                if (
                                                    correction != null &&
                                                    loopSettings.soundEnabled &&
                                                    !viewModel.isPlaybackStopRequested()
                                                ) {
                                                    val correctionSettings = loopSettings.copy(
                                                        effectiveWpm = (loopSettings.effectiveWpm - 2).coerceAtLeast(5)
                                                    )
                                                    if (aiHighlightTarget) {
                                                        viewModel.setHighlightedCharacter(correction)
                                                    }
                                                    player.playCharacter(correction, correctionSettings)
                                                    if (aiHighlightTarget) {
                                                        viewModel.setHighlightedCharacter(null)
                                                    }
                                                }
                                                if (aiHighlightTarget) {
                                                    viewModel.setHighlightedCharacter(null)
                                                }
                                                viewModel.clearExpectedTrainingCharacter()
                                                if (viewModel.isPlaybackStopRequested()) break@playback
                                                if (index < playSequence.lastIndex) {
                                                    delay(timing.interCharGapMs.toLong())
                                                }
                                            }
                                            viewModel.evaluateTrainingSetAdaptation()

                                            if ((totalIterations == null || repetition < totalIterations) &&
                                                !viewModel.isPlaybackStopRequested()
                                            ) {
                                                val postIterationState = viewModel.state.value
                                                val postIterationTiming = MorseTiming(
                                                    characterWpm = postIterationState.settings.characterWpm,
                                                    effectiveWpm = postIterationState.settings.effectiveWpm
                                                )
                                                delay(interIterationPauseMs(postIterationTiming))
                                            }
                                        }
                                    } finally {
                                        viewModel.clearPlaybackStopRequest()
                                        viewModel.resetCurrentIteration()
                                        viewModel.clearExpectedTrainingCharacter()
                                        viewModel.setHighlightedCharacter(null)
                                        viewModel.setPlaybackMode(PlaybackMode.IDLE)
                                    }
                                }
                            }
                        },
                        onAdvancePressed = {
                            viewModel.advanceToNextCharacter()
                        },
                        onRemovePressed = {
                            viewModel.removeLatestCharacter()
                        },
                        onCoachStart = {
                            scope.launch {
                                if (viewModel.state.value.isPlaying) {
                                    viewModel.requestPlaybackStop()
                                    while (isActive && viewModel.state.value.isPlaying) {
                                        delay(20L)
                                    }
                                }
                                viewModel.handleCoachCommand(CoachVoiceCommand.START_SESSION)
                            }
                        },
                        onCoachPause = {
                            viewModel.handleCoachCommand(CoachVoiceCommand.PAUSE)
                        },
                        onCoachResume = {
                            viewModel.handleCoachCommand(CoachVoiceCommand.RESUME)
                        },
                        onCoachStop = {
                            viewModel.handleCoachCommand(CoachVoiceCommand.STOP)
                        }
                    )

                    AppTab.TEXT -> TextPlaybackScreen(
                        input = state.textPlaybackInput,
                        normalizedInput = normalizeTextPlaybackInput(state.textPlaybackInput),
                        isPlaying = state.isPlaying,
                        textPlaybackActive = state.textPlaybackActive,
                        textPlaybackPaused = state.textPlaybackPaused,
                        progress = state.textPlaybackProgress,
                        currentIndex = state.textPlaybackCurrentIndex,
                        textPlaybackLoopEnabled = state.textPlaybackLoopEnabled,
                        modifier = Modifier.fillMaxSize(),
                        onInputChange = viewModel::updateTextPlaybackInput,
                        onLoopEnabledChange = viewModel::updateTextPlaybackLoopEnabled,
                        onPlayPressed = {
                            if (!isTextPlaybackStartEnabled(
                                    input = state.textPlaybackInput,
                                    isPlaying = state.isPlaying,
                                    textPlaybackActive = state.textPlaybackActive
                                )
                            ) {
                                return@TextPlaybackScreen
                            }
                            val sequence = normalizeTextPlaybackInput(state.textPlaybackInput).toList()
                            if (sequence.isEmpty()) return@TextPlaybackScreen

                            viewModel.clearPlaybackStopRequest()
                            viewModel.resetTextPlaybackProgress()
                            viewModel.setTextPlaybackPaused(false)
                            viewModel.clearTextPlaybackCurrentIndex()
                            viewModel.setTextPlaybackActive(true)
                            viewModel.setPlaybackMode(PlaybackMode.TEXT)

                            scope.launch {
                                try {
                                    playback@ while (isActive && !viewModel.isPlaybackStopRequested()) {
                                        for ((index, token) in sequence.withIndex()) {
                                            if (viewModel.isPlaybackStopRequested()) break@playback

                                            while (isActive) {
                                                val loopState = viewModel.state.value
                                                if (viewModel.isPlaybackStopRequested() || !loopState.textPlaybackActive) {
                                                    break
                                                }
                                                if (!loopState.textPlaybackPaused) {
                                                    break
                                                }
                                                delay(TEXT_PLAYBACK_PAUSE_POLL_MS)
                                            }
                                            if (viewModel.isPlaybackStopRequested()) break@playback

                                            val loopState = viewModel.state.value
                                            val loopSettings = loopState.settings
                                            val timing = MorseTiming(
                                                characterWpm = loopSettings.characterWpm,
                                                effectiveWpm = loopSettings.effectiveWpm
                                            )

                                            if (token == ' ') {
                                                viewModel.clearTextPlaybackCurrentIndex()
                                                delay(textPlaybackWordPauseMs(timing))
                                            } else {
                                                viewModel.setTextPlaybackCurrentIndex(index)
                                                player.playCharacter(token, loopSettings)
                                            }
                                            viewModel.setTextPlaybackProgress(index + 1)

                                            val nextToken = sequence.getOrNull(index + 1)
                                            if (!viewModel.isPlaybackStopRequested() &&
                                                nextToken != null &&
                                                token != ' ' &&
                                                nextToken != ' '
                                            ) {
                                                val dynamicSettings = viewModel.state.value.settings
                                                val dynamicTiming = MorseTiming(
                                                    characterWpm = dynamicSettings.characterWpm,
                                                    effectiveWpm = dynamicSettings.effectiveWpm
                                                )
                                                delay(dynamicTiming.interCharGapMs.toLong())
                                            }
                                        }

                                        if (!viewModel.state.value.textPlaybackLoopEnabled) {
                                            break@playback
                                        }
                                        if (!viewModel.isPlaybackStopRequested()) {
                                            viewModel.resetTextPlaybackProgress()
                                        }
                                    }
                                } finally {
                                    viewModel.clearPlaybackStopRequest()
                                    viewModel.setTextPlaybackPaused(false)
                                    viewModel.clearTextPlaybackCurrentIndex()
                                    viewModel.setTextPlaybackActive(false)
                                    viewModel.setPlaybackMode(PlaybackMode.IDLE)
                                }
                            }
                        },
                        onPausePressed = {
                            viewModel.setTextPlaybackPaused(true)
                        },
                        onResumePressed = {
                            viewModel.setTextPlaybackPaused(false)
                        },
                        onStopPressed = {
                            viewModel.requestPlaybackStop()
                            viewModel.setTextPlaybackPaused(false)
                        }
                    )

                    AppTab.SETTINGS -> SettingsScreen(
                        settings = state.settings,
                        currentCharacters = state.currentCharacters,
                        modifier = Modifier.fillMaxSize(),
                        onApplyThirtyWpmBeginnerPreset = viewModel::applyThirtyWpmBeginnerPreset,
                        onCharacterWpmChange = viewModel::updateCharacterWpm,
                        onEffectiveWpmChange = viewModel::updateEffectiveWpm,
                        onToneChange = viewModel::updateToneHz,
                        onSoundEnabledChange = viewModel::updateSoundEnabled,
                        onVibrationEnabledChange = viewModel::updateVibrationEnabled,
                        onHighlightPlaybackEnabledChange = viewModel::updateHighlightPlaybackEnabled,
                        onTrainingSetRepeatCountChange = viewModel::updateTrainingSetRepeatCount,
                        onRandomizeTrainingSetOrderChange = viewModel::updateRandomizeTrainingSetOrder,
                        onDarkModeChange = viewModel::updateDarkMode,
                        onHandsFreeEnabledChange = viewModel::updateHandsFreeEnabled,
                        onWakePhraseRequiredChange = viewModel::updateWakePhraseRequired,
                        onFeedbackVerboseChange = viewModel::updateFeedbackVerbose,
                        onUltraPhaseEnabledChange = viewModel::updateUltraPhaseEnabled
                    )
                }
            }
        }
    }
}

@Composable
private fun rememberDitDaViewModel(): DitDaViewModel {
    val context = LocalContext.current.applicationContext
    val speechGateway = remember(context) {
        AndroidSpeechRecognizerGateway(context)
    }
    val narrationGateway = remember(context) {
        AndroidTextToSpeechCoachNarrationGateway(context)
    }
    DisposableEffect(narrationGateway, speechGateway) {
        onDispose {
            narrationGateway.shutdown()
            speechGateway.shutdown()
        }
    }
    return remember(context, narrationGateway, speechGateway) {
        DitDaViewModel(
            stateStore = SharedPrefsDitDaStateStore(context),
            coachCoordinator = VoiceCoachCoordinator(
                commandParser = CommandParser(),
                vocabulary = PhoneticVocabulary(),
                speechRecognizer = speechGateway,
                narration = narrationGateway,
                wakePhraseGateway = PrefixWakePhraseGateway(),
                settings = VoiceCoachSettings(),
                orchestrator = CoachDecisionEngine()
            )
        )
    }
}

@Composable
private fun TextPlaybackScreen(
    input: String,
    normalizedInput: String,
    isPlaying: Boolean,
    textPlaybackActive: Boolean,
    textPlaybackPaused: Boolean,
    progress: Int,
    currentIndex: Int?,
    textPlaybackLoopEnabled: Boolean,
    modifier: Modifier = Modifier,
    onInputChange: (String) -> Unit,
    onLoopEnabledChange: (Boolean) -> Unit,
    onPlayPressed: () -> Unit,
    onPausePressed: () -> Unit,
    onResumePressed: () -> Unit,
    onStopPressed: () -> Unit
) {
    val playableCount = normalizedInput.length
    val displayProgress = progress.coerceIn(0, playableCount)
    val highlightedIndex = textPlaybackHighlightedIndex(
        normalizedInput = normalizedInput,
        currentIndex = currentIndex
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Text Playback")
        Text("Paste text and play as Morse using current settings.")
        OutlinedTextField(
            value = input,
            onValueChange = onInputChange,
            modifier = Modifier.fillMaxWidth(),
            minLines = 6,
            maxLines = 10,
            label = { Text("Input") }
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Loop Text")
            Switch(
                checked = textPlaybackLoopEnabled,
                onCheckedChange = onLoopEnabledChange
            )
        }

        Text("Playable sequence: $playableCount chars")
        if (normalizedInput.isNotEmpty()) {
            Text(
                text = highlightedTextPlaybackPreview(
                    normalizedInput = normalizedInput,
                    highlightedIndex = highlightedIndex,
                    playedUpTo = progress,
                    highlightColor = MaterialTheme.colorScheme.primaryContainer,
                    playedColor = MaterialTheme.colorScheme.secondaryContainer
                )
            )
        }
        Text(
            when {
                textPlaybackActive && textPlaybackPaused -> "Status: Paused"
                textPlaybackActive -> "Status: Playing"
                else -> "Status: Idle"
            }
        )
        if (textPlaybackActive) {
            val loopLabel = if (textPlaybackLoopEnabled) " · Looping" else ""
            Text("Progress: $displayProgress / $playableCount$loopLabel")
        }
        if (isPlaying && !textPlaybackActive) {
            Text("Another playback is active. Stop it before starting text playback.")
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (textPlaybackActive) {
                Button(
                    onClick = onStopPressed
                ) {
                    Text("Stop")
                }
            } else {
                Button(
                    onClick = onPlayPressed,
                    enabled = isTextPlaybackStartEnabled(
                        input = input,
                        isPlaying = isPlaying,
                        textPlaybackActive = textPlaybackActive
                    )
                ) {
                    Text("Play Text")
                }
            }

            Button(
                onClick = if (textPlaybackPaused) onResumePressed else onPausePressed,
                enabled = textPlaybackActive
            ) {
                Text(if (textPlaybackPaused) "Resume" else "Pause")
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    settings: DitDaSettings,
    currentCharacters: List<Char>,
    modifier: Modifier = Modifier,
    onApplyThirtyWpmBeginnerPreset: () -> Unit,
    onCharacterWpmChange: (Int) -> Unit,
    onEffectiveWpmChange: (Int) -> Unit,
    onToneChange: (Int) -> Unit,
    onSoundEnabledChange: (Boolean) -> Unit,
    onVibrationEnabledChange: (Boolean) -> Unit,
    onHighlightPlaybackEnabledChange: (Boolean) -> Unit,
    onTrainingSetRepeatCountChange: (Int) -> Unit,
    onRandomizeTrainingSetOrderChange: (Boolean) -> Unit,
    onDarkModeChange: (Boolean) -> Unit,
    onHandsFreeEnabledChange: (Boolean) -> Unit,
    onWakePhraseRequiredChange: (Boolean) -> Unit,
    onFeedbackVerboseChange: (Boolean) -> Unit,
    onUltraPhaseEnabledChange: (Boolean) -> Unit
) {
    val presetActive = isThirtyWpmExpertPresetActive(settings)
    val phase = expertPathPhase(settings)
    val unlockedGestalts = gestaltLettersUnlocked(currentCharacters)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = onApplyThirtyWpmBeginnerPreset
        ) {
            Text("Apply 30/8 Expert Path Preset")
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("30 WPM Expert Path")
            Text(if (presetActive) "ACTIVE" else "CUSTOM")
        }
        Text("Learning 30/8 -> Closing 30/9..29 -> Mastery 30/30 -> Ultra 40/40+")
        Text("Current phase: ${expertPathPhaseSummary(phase)}")
        Text("Gestalts unlocked: $unlockedGestalts / 26")

        Text("Character Speed: ${settings.characterWpm} WPM")
        Slider(
            value = settings.characterWpm.toFloat(),
            onValueChange = { onCharacterWpmChange(it.roundToInt()) },
            valueRange = 10f..60f
        )

        Text("Effective Speed (Gap): ${settings.effectiveWpm} WPM")
        Slider(
            value = settings.effectiveWpm.toFloat(),
            onValueChange = { onEffectiveWpmChange(it.roundToInt()) },
            valueRange = 5f..settings.characterWpm.toFloat()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Enable Ultra Phase Auto-Ramp")
            Switch(
                checked = settings.ultraPhaseEnabled,
                onCheckedChange = onUltraPhaseEnabledChange
            )
        }

        Text("Tone: ${settings.toneHz} Hz")
        Slider(
            value = settings.toneHz.toFloat(),
            onValueChange = { onToneChange(it.roundToInt()) },
            valueRange = 400f..1000f
        )

        Text("Training Set Repeats: ${trainingSetRepeatLabel(settings.trainingSetRepeatCount)}")
        Slider(
            value = trainingSetRepeatSliderValue(settings.trainingSetRepeatCount),
            onValueChange = {
                onTrainingSetRepeatCountChange(
                    sliderValueToTrainingSetRepeatCount(it.roundToInt())
                )
            },
            valueRange = 0f..REPEAT_SLIDER_ENDLESS_VALUE.toFloat(),
            steps = REPEAT_SLIDER_ENDLESS_VALUE - 1
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Randomize Training Set Order")
            Switch(
                checked = settings.randomizeTrainingSetOrder,
                onCheckedChange = onRandomizeTrainingSetOrderChange
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Sound")
            Switch(
                checked = settings.soundEnabled,
                onCheckedChange = onSoundEnabledChange
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Vibration")
            Switch(
                checked = settings.vibrationEnabled,
                onCheckedChange = onVibrationEnabledChange
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Highlight Playback")
            Switch(
                checked = settings.highlightPlaybackEnabled,
                onCheckedChange = onHighlightPlaybackEnabledChange
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Hands-Free Voice Coach")
            Switch(
                checked = settings.handsFreeEnabled,
                onCheckedChange = onHandsFreeEnabledChange
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Wake Phrase Required")
            Switch(
                checked = settings.wakePhraseRequired,
                onCheckedChange = onWakePhraseRequiredChange
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Verbose Voice Feedback")
            Switch(
                checked = settings.feedbackVerbose,
                onCheckedChange = onFeedbackVerboseChange
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Dark Mode")
            Switch(
                checked = settings.darkMode,
                onCheckedChange = onDarkModeChange
            )
        }
    }
}
