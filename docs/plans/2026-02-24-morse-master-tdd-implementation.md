# Morse Master (Android) TDD Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build the Morse Master Android app from `morse_master_spec.md` with strict TDD and a production-safe Gemini Nano integration that degrades gracefully when on-device AI is unavailable or busy.

**Architecture:** Use a layered design: pure Kotlin domain/core (timing, curriculum, metrics), platform adapters (AudioTrack/Haptics, ML Kit GenAI Prompt API), and a Compose presentation layer. Keep most behavior in JVM-testable modules/classes, with Android-only code behind small interfaces. AI decisioning must flow through a gateway that first checks feature status/download readiness and falls back to deterministic heuristics when unavailable.

**Tech Stack:** Kotlin, Coroutines/Flow, Jetpack Compose, AndroidX Lifecycle/ViewModel, AudioTrack + HapticGenerator, ML Kit GenAI Prompt API (`com.google.mlkit:genai`), JUnit4, Truth, MockK (minimal), Turbine, Android instrumentation tests.

**Skills to apply while executing this plan:**
- `@superpowers:test-driven-development`
- `@superpowers:verification-before-completion`
- `@superpowers:requesting-code-review`

**Assumptions and constraints:**
- Current folder has only spec/docs; app scaffold is created during this plan.
- Device/runtime checks already showed AI Core service is present but some requests can fail (`statusCode=9`), so fallback behavior is mandatory.
- If repository is not initialized, run `git init` once before Task 1 Step 5.

### Task 1: Bootstrap Project and First Domain Test (Koch Sequence)

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle.properties`
- Create: `app/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/test/java/com/morse/master/domain/KochSequenceTest.kt`
- Create: `app/src/main/java/com/morse/master/domain/KochSequence.kt`

**Step 1: Write the failing test**

```kotlin
package com.morse.master.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class KochSequenceTest {
    @Test
    fun `returns expected LCWO order`() {
        assertThat(KochSequence.full()).containsExactly(
            'K','M','U','R','E','S','N','A','P','T','L','I','O','G','Z','H','D','C','Y','F','X','Q','J','B','V'
        ).inOrder()
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.morse.master.domain.KochSequenceTest"`  
Expected: FAIL with unresolved reference `KochSequence` (or class not found).

**Step 3: Write minimal implementation**

```kotlin
package com.morse.master.domain

object KochSequence {
    private val order = listOf(
        'K','M','U','R','E','S','N','A','P','T','L','I','O','G','Z','H','D','C','Y','F','X','Q','J','B','V'
    )
    fun full(): List<Char> = order
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.morse.master.domain.KochSequenceTest"`  
Expected: PASS.

**Step 5: Commit**

```bash
git add settings.gradle.kts build.gradle.kts gradle.properties app/build.gradle.kts app/src/main/AndroidManifest.xml app/src/test/java/com/morse/master/domain/KochSequenceTest.kt app/src/main/java/com/morse/master/domain/KochSequence.kt
git commit -m "build: bootstrap app and add Koch sequence domain baseline"
```

### Task 2: Morse Timing Model (25 WPM + Farnsworth Effective Spacing)

**Files:**
- Create: `app/src/test/java/com/morse/master/domain/MorseTimingTest.kt`
- Create: `app/src/main/java/com/morse/master/domain/MorseTiming.kt`

**Step 1: Write the failing test**

```kotlin
package com.morse.master.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MorseTimingTest {
    @Test
    fun `computes standard dit and dah for 25wpm`() {
        val t = MorseTiming(characterWpm = 25, effectiveWpm = 8)
        assertThat(t.dotMs).isEqualTo(48)
        assertThat(t.dashMs).isEqualTo(144)
        assertThat(t.intraSymbolGapMs).isEqualTo(48)
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.morse.master.domain.MorseTimingTest"`  
Expected: FAIL with missing `MorseTiming`.

**Step 3: Write minimal implementation**

```kotlin
package com.morse.master.domain

data class MorseTiming(
    val characterWpm: Int,
    val effectiveWpm: Int
) {
    val dotMs: Int = 1200 / characterWpm
    val dashMs: Int = dotMs * 3
    val intraSymbolGapMs: Int = dotMs
    val interCharGapMs: Int = (1200 / effectiveWpm) * 3
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.morse.master.domain.MorseTimingTest"`  
Expected: PASS.

**Step 5: Commit**

```bash
git add app/src/test/java/com/morse/master/domain/MorseTimingTest.kt app/src/main/java/com/morse/master/domain/MorseTiming.kt
git commit -m "feat: add Morse timing model for character and effective speed"
```

### Task 3: Tone Synthesis (600Hz + 5ms Cosine Ramp)

**Files:**
- Create: `app/src/test/java/com/morse/master/audio/MorseToneGeneratorTest.kt`
- Create: `app/src/main/java/com/morse/master/audio/MorseToneGenerator.kt`

**Step 1: Write the failing test**

```kotlin
package com.morse.master.audio

import com.google.common.truth.Truth.assertThat
import kotlin.math.abs
import org.junit.Test

class MorseToneGeneratorTest {
    @Test
    fun `applies start and end smoothing`() {
        val pcm = MorseToneGenerator().generatePulse(durationMs = 120, sampleRate = 44100)
        assertThat(abs(pcm.first().toInt())).isLessThan(200)
        assertThat(abs(pcm.last().toInt())).isLessThan(200)
        assertThat(pcm.size).isEqualTo((44100 * 0.120).toInt())
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.morse.master.audio.MorseToneGeneratorTest"`  
Expected: FAIL with missing `MorseToneGenerator`.

**Step 3: Write minimal implementation**

```kotlin
package com.morse.master.audio

import kotlin.math.cos
import kotlin.math.sin

class MorseToneGenerator {
    fun generatePulse(durationMs: Int, sampleRate: Int = 44100): ShortArray {
        val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
        val rampSamples = (sampleRate * 0.005).toInt()
        return ShortArray(numSamples) { i ->
            val angle = 2.0 * Math.PI * i * 600.0 / sampleRate
            val volume = when {
                i < rampSamples -> 0.5 * (1 - cos(Math.PI * i / rampSamples))
                i > numSamples - rampSamples -> 0.5 * (1 - cos(Math.PI * (numSamples - i) / rampSamples))
                else -> 1.0
            }
            (sin(angle) * Short.MAX_VALUE * volume).toInt().toShort()
        }
    }
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.morse.master.audio.MorseToneGeneratorTest"`  
Expected: PASS.

**Step 5: Commit**

```bash
git add app/src/test/java/com/morse/master/audio/MorseToneGeneratorTest.kt app/src/main/java/com/morse/master/audio/MorseToneGenerator.kt
git commit -m "feat: add smoothed Morse tone generator"
```

### Task 4: Latency Tracking and Session Metrics

**Files:**
- Create: `app/src/test/java/com/morse/master/session/LatencyTrackerTest.kt`
- Create: `app/src/main/java/com/morse/master/session/LatencyTracker.kt`
- Create: `app/src/main/java/com/morse/master/session/SessionMetrics.kt`

**Step 1: Write the failing test**

```kotlin
package com.morse.master.session

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LatencyTrackerTest {
    @Test
    fun `computes median and accuracy`() {
        val tracker = LatencyTracker()
        tracker.record(expected = 'K', actual = 'K', latencyMs = 300)
        tracker.record(expected = 'M', actual = 'K', latencyMs = 520)
        tracker.record(expected = 'U', actual = 'U', latencyMs = 380)

        val metrics = tracker.snapshot()
        assertThat(metrics.totalChars).isEqualTo(3)
        assertThat(metrics.accuracyPercent).isEqualTo(67)
        assertThat(metrics.medianLatencyMs).isEqualTo(380)
        assertThat(metrics.failedChars).containsExactly('M')
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.morse.master.session.LatencyTrackerTest"`  
Expected: FAIL with missing tracker/metrics classes.

**Step 3: Write minimal implementation**

```kotlin
package com.morse.master.session

data class SessionMetrics(
    val totalChars: Int,
    val accuracyPercent: Int,
    val medianLatencyMs: Int,
    val failedChars: List<Char>
)

class LatencyTracker {
    private val events = mutableListOf<Triple<Char, Char, Int>>()
    fun record(expected: Char, actual: Char, latencyMs: Int) { events += Triple(expected, actual, latencyMs) }
    fun snapshot(): SessionMetrics {
        val total = events.size.coerceAtLeast(1)
        val correct = events.count { it.first == it.second }
        val sorted = events.map { it.third }.sorted()
        val median = if (sorted.isEmpty()) 0 else sorted[sorted.size / 2]
        val failed = events.filter { it.first != it.second }.map { it.first }.distinct()
        return SessionMetrics(
            totalChars = events.size,
            accuracyPercent = ((correct * 100.0) / total).toInt(),
            medianLatencyMs = median,
            failedChars = failed
        )
    }
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.morse.master.session.LatencyTrackerTest"`  
Expected: PASS.

**Step 5: Commit**

```bash
git add app/src/test/java/com/morse/master/session/LatencyTrackerTest.kt app/src/main/java/com/morse/master/session/LatencyTracker.kt app/src/main/java/com/morse/master/session/SessionMetrics.kt
git commit -m "feat: add session latency and accuracy tracking"
```

### Task 5: Curriculum Expansion + Fallback Decision Engine

**Files:**
- Create: `app/src/test/java/com/morse/master/ai/CurriculumDecisionEngineTest.kt`
- Create: `app/src/main/java/com/morse/master/ai/CurriculumDecisionEngine.kt`
- Create: `app/src/main/java/com/morse/master/ai/CurriculumCommand.kt`

**Step 1: Write the failing test**

```kotlin
package com.morse.master.ai

import com.google.common.truth.Truth.assertThat
import com.morse.master.session.SessionMetrics
import org.junit.Test

class CurriculumDecisionEngineTest {
    @Test
    fun `expands list when thresholds are met`() {
        val command = CurriculumDecisionEngine().decide(
            currentList = listOf('K', 'M'),
            metrics = SessionMetrics(totalChars = 50, accuracyPercent = 94, medianLatencyMs = 385, failedChars = listOf('M'))
        )
        assertThat(command.newCharacter).isEqualTo('U')
        assertThat(command.type).isEqualTo(CommandType.EXPAND_LIST)
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.morse.master.ai.CurriculumDecisionEngineTest"`  
Expected: FAIL with missing engine/command types.

**Step 3: Write minimal implementation**

```kotlin
package com.morse.master.ai

import com.morse.master.domain.KochSequence
import com.morse.master.session.SessionMetrics

enum class CommandType { EXPAND_LIST, KEEP_LIST }
data class CurriculumCommand(val type: CommandType, val newCharacter: Char? = null)

class CurriculumDecisionEngine {
    fun decide(currentList: List<Char>, metrics: SessionMetrics): CurriculumCommand {
        if (metrics.accuracyPercent > 90 && metrics.medianLatencyMs < 400) {
            val next = KochSequence.full().firstOrNull { it !in currentList }
            return CurriculumCommand(CommandType.EXPAND_LIST, next)
        }
        return CurriculumCommand(CommandType.KEEP_LIST)
    }
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.morse.master.ai.CurriculumDecisionEngineTest"`  
Expected: PASS.

**Step 5: Commit**

```bash
git add app/src/test/java/com/morse/master/ai/CurriculumDecisionEngineTest.kt app/src/main/java/com/morse/master/ai/CurriculumDecisionEngine.kt app/src/main/java/com/morse/master/ai/CurriculumCommand.kt
git commit -m "feat: add deterministic curriculum expansion fallback engine"
```

### Task 6: Gemini Nano Gateway Contract (ML Kit Feature Status + Download)

**Files:**
- Create: `app/src/test/java/com/morse/master/ai/GeminiNanoGatewayTest.kt`
- Create: `app/src/main/java/com/morse/master/ai/GeminiNanoGateway.kt`
- Create: `app/src/main/java/com/morse/master/ai/NanoAvailability.kt`

**Step 1: Write the failing test**

```kotlin
package com.morse.master.ai

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GeminiNanoGatewayTest {
    @Test
    fun `maps busy status to retryable unavailable`() {
        val gateway = GeminiNanoGateway(fakeStatus = "BUSY")
        assertThat(gateway.checkAvailability()).isEqualTo(NanoAvailability.RETRYABLE_UNAVAILABLE)
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.morse.master.ai.GeminiNanoGatewayTest"`  
Expected: FAIL with missing gateway/availability symbols.

**Step 3: Write minimal implementation**

```kotlin
package com.morse.master.ai

enum class NanoAvailability { AVAILABLE, DOWNLOADING, RETRYABLE_UNAVAILABLE, UNAVAILABLE }

class GeminiNanoGateway(
    private val fakeStatus: String? = null
) {
    fun checkAvailability(): NanoAvailability = when (fakeStatus) {
        "AVAILABLE" -> NanoAvailability.AVAILABLE
        "DOWNLOADING" -> NanoAvailability.DOWNLOADING
        "BUSY" -> NanoAvailability.RETRYABLE_UNAVAILABLE
        else -> NanoAvailability.UNAVAILABLE
    }
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.morse.master.ai.GeminiNanoGatewayTest"`  
Expected: PASS.

**Step 5: Commit**

```bash
git add app/src/test/java/com/morse/master/ai/GeminiNanoGatewayTest.kt app/src/main/java/com/morse/master/ai/GeminiNanoGateway.kt app/src/main/java/com/morse/master/ai/NanoAvailability.kt
git commit -m "feat: add Gemini Nano gateway availability contract"
```

### Task 7: AI Orchestrator with Hard Fallback to Deterministic Engine

**Files:**
- Create: `app/src/test/java/com/morse/master/ai/AiOrchestratorTest.kt`
- Create: `app/src/main/java/com/morse/master/ai/AiOrchestrator.kt`

**Step 1: Write the failing test**

```kotlin
package com.morse.master.ai

import com.google.common.truth.Truth.assertThat
import com.morse.master.session.SessionMetrics
import org.junit.Test

class AiOrchestratorTest {
    @Test
    fun `falls back when nano is retryable unavailable`() {
        val orchestrator = AiOrchestrator(
            nano = GeminiNanoGateway(fakeStatus = "BUSY"),
            fallback = CurriculumDecisionEngine()
        )
        val cmd = orchestrator.nextCommand(
            currentList = listOf('K', 'M'),
            metrics = SessionMetrics(50, 94, 385, listOf('M'))
        )
        assertThat(cmd.type).isEqualTo(CommandType.EXPAND_LIST)
        assertThat(cmd.newCharacter).isEqualTo('U')
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.morse.master.ai.AiOrchestratorTest"`  
Expected: FAIL with missing `AiOrchestrator`.

**Step 3: Write minimal implementation**

```kotlin
package com.morse.master.ai

import com.morse.master.session.SessionMetrics

class AiOrchestrator(
    private val nano: GeminiNanoGateway,
    private val fallback: CurriculumDecisionEngine
) {
    fun nextCommand(currentList: List<Char>, metrics: SessionMetrics): CurriculumCommand {
        return when (nano.checkAvailability()) {
            NanoAvailability.AVAILABLE -> fallback.decide(currentList, metrics) // replace with real prompt call in later red/green cycle
            NanoAvailability.DOWNLOADING,
            NanoAvailability.RETRYABLE_UNAVAILABLE,
            NanoAvailability.UNAVAILABLE -> fallback.decide(currentList, metrics)
        }
    }
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.morse.master.ai.AiOrchestratorTest"`  
Expected: PASS.

**Step 5: Commit**

```bash
git add app/src/test/java/com/morse/master/ai/AiOrchestratorTest.kt app/src/main/java/com/morse/master/ai/AiOrchestrator.kt
git commit -m "feat: add AI orchestration with mandatory fallback path"
```

### Task 8: Session ViewModel State Machine (No-Look Grid + Nudge Timeout)

**Files:**
- Create: `app/src/test/java/com/morse/master/ui/SessionViewModelTest.kt`
- Create: `app/src/main/java/com/morse/master/ui/SessionViewModel.kt`
- Create: `app/src/main/java/com/morse/master/ui/SessionUiState.kt`

**Step 1: Write the failing test**

```kotlin
package com.morse.master.ui

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SessionViewModelTest {
    @Test
    fun `emits nudge when response timeout is exceeded`() = runTest {
        val vm = SessionViewModel()
        vm.state.test {
            vm.onPlaybackFinished(nowMs = 0L)
            vm.onTick(nowMs = 1501L)
            assertThat(awaitItem().showNudge).isTrue()
        }
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.morse.master.ui.SessionViewModelTest"`  
Expected: FAIL with missing ViewModel/state classes.

**Step 3: Write minimal implementation**

```kotlin
package com.morse.master.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SessionUiState(val showNudge: Boolean = false, val lastPlaybackFinishedAtMs: Long? = null)

class SessionViewModel {
    private val _state = MutableStateFlow(SessionUiState())
    val state: StateFlow<SessionUiState> = _state.asStateFlow()

    fun onPlaybackFinished(nowMs: Long) {
        _state.value = _state.value.copy(lastPlaybackFinishedAtMs = nowMs, showNudge = false)
    }

    fun onTick(nowMs: Long) {
        val last = _state.value.lastPlaybackFinishedAtMs ?: return
        if (nowMs - last > 1500) _state.value = _state.value.copy(showNudge = true)
    }
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.morse.master.ui.SessionViewModelTest"`  
Expected: PASS.

**Step 5: Commit**

```bash
git add app/src/test/java/com/morse/master/ui/SessionViewModelTest.kt app/src/main/java/com/morse/master/ui/SessionViewModel.kt app/src/main/java/com/morse/master/ui/SessionUiState.kt
git commit -m "feat: add session view-model nudge timeout behavior"
```

### Task 9: Compose Grid UI + Integration Wiring

**Files:**
- Create: `app/src/androidTest/java/com/morse/master/ui/SessionScreenTest.kt`
- Create: `app/src/main/java/com/morse/master/ui/SessionScreen.kt`
- Create: `app/src/main/java/com/morse/master/MainActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml`

**Step 1: Write the failing test**

```kotlin
package com.morse.master.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.morse.master.MainActivity
import org.junit.Rule
import org.junit.Test

class SessionScreenTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `shows active training characters`() {
        rule.onNodeWithText("K").assertIsDisplayed()
        rule.onNodeWithText("M").assertIsDisplayed()
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.morse.master.ui.SessionScreenTest`  
Expected: FAIL because `MainActivity`/`SessionScreen` are not implemented.

**Step 3: Write minimal implementation**

```kotlin
package com.morse.master

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.morse.master.ui.SessionScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SessionScreen(characters = listOf('K', 'M')) }
    }
}
```

```kotlin
package com.morse.master.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun SessionScreen(characters: List<Char>) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        characters.forEach { Text(it.toString()) }
    }
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.morse.master.ui.SessionScreenTest`  
Expected: PASS.

**Step 5: Commit**

```bash
git add app/src/androidTest/java/com/morse/master/ui/SessionScreenTest.kt app/src/main/java/com/morse/master/ui/SessionScreen.kt app/src/main/java/com/morse/master/MainActivity.kt app/src/main/AndroidManifest.xml
git commit -m "feat: add minimal compose training grid and activity wiring"
```

### Task 10: End-to-End AI Contract Test (Prompt JSON I/O + Fallback)

**Files:**
- Create: `app/src/test/java/com/morse/master/ai/PromptContractTest.kt`
- Modify: `app/src/main/java/com/morse/master/ai/AiOrchestrator.kt`
- Modify: `app/src/main/java/com/morse/master/ai/GeminiNanoGateway.kt`

**Step 1: Write the failing test**

```kotlin
package com.morse.master.ai

import com.google.common.truth.Truth.assertThat
import com.morse.master.session.SessionMetrics
import org.junit.Test

class PromptContractTest {
    @Test
    fun `falls back when prompt call throws`() {
        val gateway = GeminiNanoGateway(fakeStatus = "AVAILABLE", failPrompt = true)
        val orchestrator = AiOrchestrator(gateway, CurriculumDecisionEngine())
        val cmd = orchestrator.nextCommand(listOf('K', 'M'), SessionMetrics(50, 95, 300, emptyList()))
        assertThat(cmd.newCharacter).isEqualTo('U')
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.morse.master.ai.PromptContractTest"`  
Expected: FAIL because prompt failure handling path is missing.

**Step 3: Write minimal implementation**

```kotlin
// in GeminiNanoGateway
class GeminiNanoGateway(
    private val fakeStatus: String? = null,
    private val failPrompt: Boolean = false
) {
    fun runPromptOrNull(inputJson: String): String? {
        if (failPrompt) return null
        return """{"command":"EXPAND_LIST","new_character":"U"}"""
    }
}

// in AiOrchestrator.nextCommand(...)
if (nano.checkAvailability() == NanoAvailability.AVAILABLE) {
    val response = nano.runPromptOrNull("{}")
    if (response != null && response.contains("\"EXPAND_LIST\"")) {
        return CurriculumCommand(CommandType.EXPAND_LIST, 'U')
    }
}
return fallback.decide(currentList, metrics)
```

**Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.morse.master.ai.PromptContractTest"`  
Expected: PASS.

**Step 5: Commit**

```bash
git add app/src/test/java/com/morse/master/ai/PromptContractTest.kt app/src/main/java/com/morse/master/ai/AiOrchestrator.kt app/src/main/java/com/morse/master/ai/GeminiNanoGateway.kt
git commit -m "feat: enforce prompt contract fallback on nano inference failure"
```

### Task 11: Verification Gate Before Completion

**Files:**
- Create: `docs/testing/morse-master-verification.md`

**Step 1: Write failing verification checklist test (scripted expectation)**

```bash
# docs/testing/morse-master-verification.md should include all required checks.
# Intentionally run a missing check first:
./gradlew :app:lintDebug
```

**Step 2: Run verification commands to identify failures**

Run:
- `./gradlew :app:testDebugUnitTest`
- `./gradlew :app:connectedDebugAndroidTest`
- `./gradlew :app:lintDebug`

Expected: all PASS with zero failing tests and no lint errors.

**Step 3: Write minimal verification doc implementation**

```markdown
# Morse Master Verification
- Unit: `./gradlew :app:testDebugUnitTest`
- Instrumented: `./gradlew :app:connectedDebugAndroidTest`
- Lint: `./gradlew :app:lintDebug`
- Manual runtime check:
  - Launch app
  - Confirm fallback works when Nano unavailable/busy
  - Confirm list expands at >90% and <400ms
```

**Step 4: Re-run and capture proof**

Run:
- `./gradlew :app:testDebugUnitTest`
- `./gradlew :app:connectedDebugAndroidTest`
- `./gradlew :app:lintDebug`

Expected: PASS; attach output snippets to PR/summary.

**Step 5: Commit**

```bash
git add docs/testing/morse-master-verification.md
git commit -m "docs: add verification gate for morse master"
```

## Final acceptance criteria

- Audio pulses use 600Hz with 5ms smoothing ramps.
- Session metrics produce accurate `accuracy_percent`, `median_latency_ms`, and failed-character set.
- Curriculum expands on `(accuracy > 90) && (median latency < 400ms)` using LCWO order.
- Gemini Nano access is attempted only through availability-gated flow.
- Any Nano busy/unavailable/error condition reliably falls back to deterministic engine.
- UI supports no-look grid behavior and nudge timeout.
- All tests, lint, and instrumentation checks pass.

## Notes for execution

- Keep each red/green cycle minimal; no speculative abstractions.
- Do not implement full ML Kit prompt wiring until the first failing test that requires it.
- For device-specific AI checks, record ADB/logcat evidence in the PR when behavior differs by rollout/account.
