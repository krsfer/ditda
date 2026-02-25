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
import kotlin.math.roundToInt

internal data class CharacterButtonVisualState(
    val enabled: Boolean,
    val isHighlighted: Boolean
)

internal data class TrainingSetProgressBarState(
    val progress: Float,
    val percentage: Int
)

private const val BASE_CURRICULUM_SIZE = 2

@Suppress("UNUSED_PARAMETER")
internal fun characterButtonVisualState(
    isPlaying: Boolean,
    character: Char,
    highlightedCharacter: Char?
): CharacterButtonVisualState {
    return CharacterButtonVisualState(
        enabled = true,
        isHighlighted = character == highlightedCharacter
    )
}

internal fun latestRemovableCharacter(characters: List<Char>): Char? {
    return if (characters.size > BASE_CURRICULUM_SIZE) {
        characters.last()
    } else {
        null
    }
}

internal fun playbackIterationCounterText(
    isPlaying: Boolean,
    currentIteration: Int,
    repeatCount: Int
): String {
    val progressState = trainingSetProgressBarState(
        isPlaying = isPlaying,
        currentIteration = currentIteration,
        repeatCount = repeatCount
    )
    val maxIterations = totalTrainingSetIterations(repeatCount)
    val displayCurrent = if (isPlaying) currentIteration.coerceAtLeast(1) else 0
    return if (maxIterations == null) {
        "Training Sets: $displayCurrent / Endless (${progressState.percentage}%)"
    } else {
        "Training Sets: ${displayCurrent.coerceAtMost(maxIterations)} / $maxIterations (${progressState.percentage}%)"
    }
}

internal fun trainingSetProgressBarState(
    isPlaying: Boolean,
    currentIteration: Int,
    repeatCount: Int
): TrainingSetProgressBarState {
    val displayCurrent = if (isPlaying) currentIteration.coerceAtLeast(1) else 0
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
    isPlaying: Boolean = false,
    currentIteration: Int = 0,
    highlightedCharacter: Char? = null,
    onCharacterPressed: (Char) -> Unit = {},
    onPlaySetPressed: () -> Unit = {},
    onAdvancePressed: () -> Unit = {},
    onRemovePressed: () -> Unit = {}
) {
    val removableCharacter = latestRemovableCharacter(characters)
    val progressBarState = trainingLevelProgressBarState(
        currentTrainingLevels = characters.size,
        maxTrainingLevels = maxTrainingLevels
    )
    val playTrainingSetButtonColors = if (isPlaying) {
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
                    isPlaying = isPlaying,
                    currentIteration = currentIteration,
                    repeatCount = settings.trainingSetRepeatCount
                )
            )
            LinearProgressIndicator(
                progress = { progressBarState.progress },
                modifier = Modifier.fillMaxWidth()
            )

            characters.chunked(4).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    row.forEach { char ->
                        val buttonState = characterButtonVisualState(
                            isPlaying = isPlaying,
                            character = char,
                            highlightedCharacter = highlightedCharacter
                        )
                        OutlinedButton(
                            onClick = {
                                if (!isPlaying) {
                                    onCharacterPressed(char)
                                }
                            },
                            enabled = buttonState.enabled,
                            modifier = Modifier.padding(horizontal = 4.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (buttonState.isHighlighted) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    Color.Transparent
                                },
                                contentColor = if (buttonState.isHighlighted) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
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
                enabled = true,
                colors = playTrainingSetButtonColors
            ) {
                Text("Play Training Set")
            }

            Button(
                onClick = onAdvancePressed,
                enabled = !isPlaying && nextCharacter != null
            ) {
                val label = nextCharacter?.let { "Add Next Character ($it)" } ?: "Curriculum Complete"
                Text(label)
            }

            Button(
                onClick = onRemovePressed,
                enabled = !isPlaying && removableCharacter != null
            ) {
                val label = removableCharacter?.let { "Remove Latest Character ($it)" } ?: "Remove Latest Character"
                Text(label)
            }
        }
    }
}
