package com.morse.master.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

internal data class CharacterButtonVisualState(
    val enabled: Boolean,
    val isHighlighted: Boolean
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

@Composable
fun SessionScreen(
    characters: List<Char>,
    modifier: Modifier = Modifier,
    settings: DitDaSettings = DitDaSettings(),
    nextCharacter: Char? = null,
    isPlaying: Boolean = false,
    highlightedCharacter: Char? = null,
    onCharacterPressed: (Char) -> Unit = {},
    onPlaySetPressed: () -> Unit = {},
    onAdvancePressed: () -> Unit = {},
    onRemovePressed: () -> Unit = {}
) {
    val removableCharacter = latestRemovableCharacter(characters)
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
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Practice")
        Text("Tap a letter or play the full training set.")
        Text("Speed ${settings.characterWpm}/${settings.effectiveWpm} WPM · ${settings.toneHz} Hz")

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

        Spacer(Modifier.height(8.dp))

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
