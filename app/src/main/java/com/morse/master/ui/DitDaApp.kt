@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.morse.master.ui

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.material3.MaterialTheme
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
    AppTab.SETTINGS -> 1
}

internal fun pageToTab(page: Int): AppTab = when (page) {
    1 -> AppTab.SETTINGS
    else -> AppTab.PRACTICE
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

@Composable
fun DitDaApp(viewModel: DitDaViewModel = rememberDitDaViewModel()) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val player = remember(context) { MorsePracticePlayer(context) }
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
                        isPlaying = state.isPlaying,
                        currentIteration = state.currentIteration,
                        highlightedCharacter = state.highlightedCharacter,
                        coachState = state.coachState,
                        sessionElapsedMs = state.sessionElapsedMs,
                        lastCoachMessage = state.lastCoachMessage,
                        voiceControlArmed = state.voiceControlArmed,
                        micPermissionGranted = micPermissionGranted,
                        onCharacterPressed = { char ->
                            scope.launch {
                                viewModel.setPlaying(true)
                                try {
                                    player.playCharacter(char, state.settings)
                                } finally {
                                    viewModel.setPlaying(false)
                                }
                            }
                        },
                        onPlaySetPressed = {
                            if (state.isPlaying) {
                                viewModel.requestPlaybackStop()
                            } else {
                                val settingsSnapshot = state.settings
                                viewModel.clearPlaybackStopRequest()
                                viewModel.resetCurrentIteration()
                                viewModel.setPlaying(true)
                                scope.launch {
                                    try {
                                        val timing = MorseTiming(
                                            characterWpm = settingsSnapshot.characterWpm,
                                            effectiveWpm = settingsSnapshot.effectiveWpm
                                        )
                                        val totalIterations = totalTrainingSetIterations(
                                            settingsSnapshot.trainingSetRepeatCount
                                        )
                                        var repetition = 0
                                        playback@ while (totalIterations == null || repetition < totalIterations) {
                                            if (viewModel.isPlaybackStopRequested()) break@playback

                                            repetition += 1
                                            viewModel.setCurrentIteration(repetition)

                                            val playSequence = viewModel.nextRandomizedTrainingSet()
                                            for ((index, char) in playSequence.withIndex()) {
                                                if (viewModel.isPlaybackStopRequested()) break@playback

                                                if (settingsSnapshot.highlightPlaybackEnabled) {
                                                    viewModel.setHighlightedCharacter(char)
                                                }
                                                player.playCharacter(char, settingsSnapshot)
                                                if (settingsSnapshot.highlightPlaybackEnabled) {
                                                    viewModel.setHighlightedCharacter(null)
                                                }
                                                if (viewModel.isPlaybackStopRequested()) break@playback
                                                if (index < playSequence.lastIndex) {
                                                    delay(timing.interCharGapMs.toLong())
                                                }
                                            }

                                            if ((totalIterations == null || repetition < totalIterations) &&
                                                !viewModel.isPlaybackStopRequested()
                                            ) {
                                                delay(interIterationPauseMs(timing))
                                            }
                                        }
                                    } finally {
                                        viewModel.clearPlaybackStopRequest()
                                        viewModel.resetCurrentIteration()
                                        viewModel.setHighlightedCharacter(null)
                                        viewModel.setPlaying(false)
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

                    AppTab.SETTINGS -> SettingsScreen(
                        settings = state.settings,
                        modifier = Modifier.fillMaxSize(),
                        onCharacterWpmChange = viewModel::updateCharacterWpm,
                        onEffectiveWpmChange = viewModel::updateEffectiveWpm,
                        onToneChange = viewModel::updateToneHz,
                        onSoundEnabledChange = viewModel::updateSoundEnabled,
                        onVibrationEnabledChange = viewModel::updateVibrationEnabled,
                        onHighlightPlaybackEnabledChange = viewModel::updateHighlightPlaybackEnabled,
                        onTrainingSetRepeatCountChange = viewModel::updateTrainingSetRepeatCount,
                        onDarkModeChange = viewModel::updateDarkMode,
                        onHandsFreeEnabledChange = viewModel::updateHandsFreeEnabled,
                        onWakePhraseRequiredChange = viewModel::updateWakePhraseRequired,
                        onFeedbackVerboseChange = viewModel::updateFeedbackVerbose
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
private fun SettingsScreen(
    settings: DitDaSettings,
    modifier: Modifier = Modifier,
    onCharacterWpmChange: (Int) -> Unit,
    onEffectiveWpmChange: (Int) -> Unit,
    onToneChange: (Int) -> Unit,
    onSoundEnabledChange: (Boolean) -> Unit,
    onVibrationEnabledChange: (Boolean) -> Unit,
    onHighlightPlaybackEnabledChange: (Boolean) -> Unit,
    onTrainingSetRepeatCountChange: (Int) -> Unit,
    onDarkModeChange: (Boolean) -> Unit,
    onHandsFreeEnabledChange: (Boolean) -> Unit,
    onWakePhraseRequiredChange: (Boolean) -> Unit,
    onFeedbackVerboseChange: (Boolean) -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Character Speed: ${settings.characterWpm} WPM")
        Slider(
            value = settings.characterWpm.toFloat(),
            onValueChange = { onCharacterWpmChange(it.roundToInt()) },
            valueRange = 10f..45f
        )

        Text("Effective Speed (Gap): ${settings.effectiveWpm} WPM")
        Slider(
            value = settings.effectiveWpm.toFloat(),
            onValueChange = { onEffectiveWpmChange(it.roundToInt()) },
            valueRange = 5f..20f
        )

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
