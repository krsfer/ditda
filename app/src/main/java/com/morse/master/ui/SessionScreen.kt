package com.morse.master.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.morse.master.coach.CoachState
import kotlin.math.roundToInt

internal data class CharacterButtonVisualState(
    val enabled: Boolean,
    val isHighlighted: Boolean,
    val isProblem: Boolean,
    val isEasy: Boolean
)

internal data class TrainingSetProgressBarState(
    val progress: Float,
    val percentage: Int
)

private const val BASE_CURRICULUM_SIZE = 2
private const val CHARACTER_GRID_COLUMNS = 6

internal fun isCoachSessionInProgress(coachState: CoachState): Boolean {
    return coachState != CoachState.IDLE && coachState != CoachState.STOPPED
}

internal fun isManualCharacterInputEnabled(playbackMode: PlaybackMode, coachState: CoachState): Boolean {
    return !isCoachSessionInProgress(coachState) &&
        (playbackMode == PlaybackMode.IDLE || playbackMode == PlaybackMode.TRAINING_SET)
}

internal fun isPlayTrainingSetEnabled(playbackMode: PlaybackMode, coachState: CoachState): Boolean {
    return playbackMode == PlaybackMode.TRAINING_SET ||
        (playbackMode == PlaybackMode.IDLE && !isCoachSessionInProgress(coachState))
}

internal fun isCoachStartEnabled(coachState: CoachState, playbackMode: PlaybackMode): Boolean {
    return playbackMode == PlaybackMode.IDLE &&
        (coachState == CoachState.IDLE || coachState == CoachState.STOPPED)
}

internal fun characterButtonVisualState(
    playbackMode: PlaybackMode,
    character: Char,
    highlightedCharacter: Char?,
    problemCharacters: Set<Char> = emptySet(),
    easyCharacters: Set<Char> = emptySet(),
    coachState: CoachState = CoachState.IDLE
): CharacterButtonVisualState {
    val highlighted = character == highlightedCharacter
    return CharacterButtonVisualState(
        enabled = isManualCharacterInputEnabled(playbackMode, coachState),
        isHighlighted = highlighted,
        isProblem = !highlighted && character in problemCharacters,
        isEasy = !highlighted && character in easyCharacters
    )
}

internal fun latestRemovableCharacter(characters: List<Char>): Char? {
    return if (characters.size > BASE_CURRICULUM_SIZE) {
        characters.last()
    } else {
        null
    }
}

internal fun characterGridRows(characters: List<Char>, columns: Int = CHARACTER_GRID_COLUMNS): List<List<Char>> {
    return characters.chunked(columns.coerceAtLeast(1))
}

internal fun playbackIterationCounterText(
    playbackMode: PlaybackMode,
    currentIteration: Int,
    repeatCount: Int
): String {
    val progressState = trainingSetProgressBarState(
        playbackMode = playbackMode,
        currentIteration = currentIteration,
        repeatCount = repeatCount
    )
    val maxIterations = totalTrainingSetIterations(repeatCount)
    val displayCurrent = if (playbackMode == PlaybackMode.TRAINING_SET) currentIteration.coerceAtLeast(1) else 0
    return if (maxIterations == null) {
        "Training Sets: $displayCurrent / Endless (${progressState.percentage}%)"
    } else {
        "Training Sets: ${displayCurrent.coerceAtMost(maxIterations)} / $maxIterations (${progressState.percentage}%)"
    }
}

internal fun trainingSetProgressBarState(
    playbackMode: PlaybackMode,
    currentIteration: Int,
    repeatCount: Int
): TrainingSetProgressBarState {
    val displayCurrent = if (playbackMode == PlaybackMode.TRAINING_SET) currentIteration.coerceAtLeast(1) else 0
    val maxIterations = totalTrainingSetIterations(repeatCount)
    val progress = if (maxIterations == null) {
        displayCurrent.coerceIn(0, 100).toFloat() / 100f
    } else {
        val clampedCurrent = displayCurrent.coerceIn(0, maxIterations)
        if (maxIterations == 0) {
            0f
        } else {
            clampedCurrent.toFloat() / maxIterations.toFloat()
        }
    }

    return TrainingSetProgressBarState(
        progress = progress.coerceIn(0f, 1f),
        percentage = (progress.coerceIn(0f, 1f) * 100f).roundToInt()
    )
}

internal fun trainingLevelProgressBarState(
    currentTrainingLevels: Int,
    maxTrainingLevels: Int
): TrainingSetProgressBarState {
    val safeMax = maxTrainingLevels.coerceAtLeast(1)
    val safeCurrent = currentTrainingLevels.coerceIn(0, safeMax)
    val progress = safeCurrent.toFloat() / safeMax.toFloat()
    return TrainingSetProgressBarState(
        progress = progress.coerceIn(0f, 1f),
        percentage = (progress.coerceIn(0f, 1f) * 100f).roundToInt()
    )
}

internal fun trainingLevelProgressText(
    currentTrainingLevels: Int,
    maxTrainingLevels: Int
): String {
    val safeMax = maxTrainingLevels.coerceAtLeast(1)
    val safeCurrent = currentTrainingLevels.coerceIn(0, safeMax)
    val progress = trainingLevelProgressBarState(
        currentTrainingLevels = safeCurrent,
        maxTrainingLevels = safeMax
    )
    return "Training Level: $safeCurrent / $safeMax (${progress.percentage}%)"
}

@Composable
fun SessionScreen(
    characters: List<Char>,
    modifier: Modifier = Modifier,
    maxTrainingLevels: Int = 25,
    settings: DitDaSettings = DitDaSettings(),
    nextCharacter: Char? = null,
    playbackMode: PlaybackMode = PlaybackMode.IDLE,
    currentIteration: Int = 0,
    highlightedCharacter: Char? = null,
    problemCharacters: Set<Char> = emptySet(),
    easyCharacters: Set<Char> = emptySet(),
    coachState: CoachState = CoachState.IDLE,
    sessionElapsedMs: Long = 0L,
    lastCoachMessage: String? = null,
    voiceControlArmed: Boolean = false,
    micPermissionGranted: Boolean = true,
    onCharacterPressed: (Char) -> Unit = {},
    onPlaySetPressed: () -> Unit = {},
    onAdvancePressed: () -> Unit = {},
    onRemovePressed: () -> Unit = {},
    onCoachStart: () -> Unit = {},
    onCoachPause: () -> Unit = {},
    onCoachResume: () -> Unit = {},
    onCoachStop: () -> Unit = {}
) {
    val isTrainingSetPlaying = playbackMode == PlaybackMode.TRAINING_SET
    val isAnyPlaybackActive = playbackMode != PlaybackMode.IDLE
    val removableCharacter = latestRemovableCharacter(characters)
    val progressBarState = trainingLevelProgressBarState(
        currentTrainingLevels = characters.size,
        maxTrainingLevels = maxTrainingLevels
    )
    val playTrainingSetButtonColors = if (isTrainingSetPlaying) {
        ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        ButtonDefaults.buttonColors()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .weight(1f, fill = true)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Practice")
            Text("Tap a letter or play the full training set.")
            Text("Speed ${settings.characterWpm}/${settings.effectiveWpm} WPM · ${settings.toneHz} Hz")
            Text(
                trainingLevelProgressText(
                    currentTrainingLevels = characters.size,
                    maxTrainingLevels = maxTrainingLevels
                )
            )
            Text(
                playbackIterationCounterText(
                    playbackMode = playbackMode,
                    currentIteration = currentIteration,
                    repeatCount = settings.trainingSetRepeatCount
                )
            )
            if (settings.handsFreeEnabled) {
                val status = coachState.name.lowercase().replace('_', ' ')
                Text("Voice Coach: $status · ${sessionElapsedMs / 1000}s")
                Text(if (voiceControlArmed) "Voice Control: Armed" else "Voice Control: Standby")
                if (!micPermissionGranted) {
                    Text("Microphone permission is required for voice commands and spoken answers.")
                }
                if (!lastCoachMessage.isNullOrBlank()) {
                    Text("Coach: $lastCoachMessage")
                }
            }
            LinearProgressIndicator(
                progress = { progressBarState.progress },
                modifier = Modifier.fillMaxWidth()
            )

            characterGridRows(characters).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    row.forEach { char ->
                        val buttonState = characterButtonVisualState(
                            playbackMode = playbackMode,
                            character = char,
                            highlightedCharacter = highlightedCharacter,
                            problemCharacters = problemCharacters,
                            easyCharacters = easyCharacters,
                            coachState = coachState
                        )
                        val containerColor = when {
                            buttonState.isHighlighted -> MaterialTheme.colorScheme.primaryContainer
                            buttonState.isProblem -> MaterialTheme.colorScheme.errorContainer
                            buttonState.isEasy -> MaterialTheme.colorScheme.tertiaryContainer
                            else -> Color.Transparent
                        }
                        val contentColor = when {
                            buttonState.isHighlighted -> MaterialTheme.colorScheme.onPrimaryContainer
                            buttonState.isProblem -> MaterialTheme.colorScheme.onErrorContainer
                            buttonState.isEasy -> MaterialTheme.colorScheme.onTertiaryContainer
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                        OutlinedButton(
                            onClick = {
                                if (buttonState.enabled) {
                                    onCharacterPressed(char)
                                }
                            },
                            enabled = buttonState.enabled,
                            modifier = Modifier.padding(horizontal = 4.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = containerColor,
                                contentColor = contentColor
                            )
                        ) {
                            Text(char.toString())
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = onPlaySetPressed,
                enabled = isPlayTrainingSetEnabled(playbackMode, coachState),
                colors = playTrainingSetButtonColors
            ) {
                Text("Play Training Set")
            }

            Button(
                onClick = onAdvancePressed,
                enabled = !isCoachSessionInProgress(coachState) &&
                    !isAnyPlaybackActive &&
                    nextCharacter != null
            ) {
                val label = nextCharacter?.let { "Add Next Character ($it)" } ?: "Curriculum Complete"
                Text(label)
            }

            Button(
                onClick = onRemovePressed,
                enabled = !isCoachSessionInProgress(coachState) &&
                    !isAnyPlaybackActive &&
                    removableCharacter != null
            ) {
                val label = removableCharacter?.let { "Remove Latest Character ($it)" } ?: "Remove Latest Character"
                Text(label)
            }

            if (settings.handsFreeEnabled) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
                ) {
                    Button(
                        onClick = onCoachStart,
                        enabled = isCoachStartEnabled(
                            coachState = coachState,
                            playbackMode = playbackMode
                        )
                    ) {
                        Text("Start Coach")
                    }

                    val pauseEnabled = coachState == CoachState.ROUND_ACTIVE || coachState == CoachState.BREAK_PROMPT
                    val resumeEnabled = coachState == CoachState.PAUSED
                    Button(
                        onClick = if (resumeEnabled) onCoachResume else onCoachPause,
                        enabled = pauseEnabled || resumeEnabled
                    ) {
                        Text(if (resumeEnabled) "Resume Coach" else "Pause Coach")
                    }
                }

                Button(
                    onClick = onCoachStop,
                    enabled = coachState != CoachState.IDLE && coachState != CoachState.STOPPED
                ) {
                    Text("Stop Coach")
                }
            }
        }
    }
}
