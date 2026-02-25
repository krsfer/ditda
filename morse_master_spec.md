# Technical Specification: AI-Adaptive Morse Master (2026)

---

## 1. Core Learning Philosophy

| Aspect | Detail |
|---|---|
| **Method** | Progressive Koch Method (Incremental list) |
| **Target Speed** | 25 WPM character speed (Muscle Memory) / 8 WPM Effective speed (Farnsworth Spacing) |
| **Auditory Focus** | "Smoothed" sine waves to prevent ear fatigue |
| **AI Integration** | Gemini Nano manages the "Nudge" and "List Expansion" based on user response latency |

---

## 2. Low-Latency Audio & Haptic Engine (Kotlin / AAudio)

To ensure fatigueless listening, a **5ms Cosine Squared Ramp** is applied to the start and end of every pulse. This removes the "metallic click" found in basic Morse apps.

```kotlin
/**
 * Generates a PCM 16-bit sine wave at 600Hz with 5ms smoothing ramps.
 * Designed for Tensor G5 TPU offloading.
 */
fun generateMorsePulse(durationMs: Int, sampleRate: Int = 44100): ShortArray {
    val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
    val samples = ShortArray(numSamples)
    val rampSamples = (sampleRate * 0.005).toInt() // 5ms ramp

    for (i in 0 until numSamples) {
        val angle = 2.0 * Math.PI * i * 600.0 / sampleRate
        val volume = when {
            i < rampSamples -> 0.5 * (1 - Math.cos(Math.PI * i / rampSamples))
            i > numSamples - rampSamples -> 0.5 * (1 - Math.cos(Math.PI * (numSamples - i) / rampSamples))
            else -> 1.0
        }
        samples[i] = (Math.sin(angle) * Short.MAX_VALUE * volume).toInt().toShort()
    }
    return samples
}

// HAPTIC SYNC: Attach to AudioTrack session
// val hapticGenerator = HapticGenerator.create(audioTrack.audioSessionId)
// hapticGenerator.enabled = true
```

---

## 3. Gemini Nano: On-Device Intelligence

The app communicates with the local `gemini-nano` model to decide when to add letters.

### System Prompt

> *"Act as a Morse Code Proctor for a student on a Tensor G5 device. Analyze the provided JSON performance data. If Accuracy > 90% and Latency < 400ms, increment the alphabet list using the LCWO sequence: K, M, U, R, E, S, N, A, P, T, L, I, O, G, Z, H, D, C, Y, F, X, Q, J, B, V. Output ONLY JSON."*

### Communication JSON Schema

**Input — App → Nano:**

```json
{
  "current_list": ["K", "M"],
  "metrics": {
    "total_chars": 50,
    "accuracy_percent": 94,
    "median_latency_ms": 385,
    "failed_chars": ["M"]
  }
}
```

**Output — Nano → App:**

```json
{
  "command": "EXPAND_LIST",
  "new_character": "U",
  "weight_adjustments": { "K": 0.2, "M": 0.4, "U": 0.4 },
  "nudge_tip": "You are hearing M as a single block now. Introducing U (di-di-dah)."
}
```

---

## 4. UI/UX: The Adaptive Touch Interface

- **No-Look Grid** — The screen is a grid of touch-zones corresponding to the `current_list`.
- **AI Nudge** — If the user does not respond within 1500ms, the phone triggers a *"Haptic Ghost"* pulse: a faint vibration of the correct pattern to prompt the brain without breaking the flow.
- **Visual-Free Mode** — Dark UI with minimal text to keep the brain in the "Auditory Loop."

---

## 5. LCWO Character Reference — The "Songs"

Mental Maps displayed by the AI during the introduction of each new letter.

| Letter | Rhythm (25 WPM) | The "Gestalt" Hook |
|:---:|:---:|---|
| K | `− ⋅ −` | "Kangaroo" *(Bouncy)* |
| M | `− −` | "Moo" *(Heavy, long)* |
| U | `⋅ ⋅ −` | "Under-way" *(Two skips, one slide)* |
| R | `⋅ − ⋅` | "Rotator" *(Balanced)* |
| E | `⋅` | "Eye" *(A single sharp prick)* |

---

## 6. Implementation Workflow

| Step | Task |
|:---:|---|
| **1** | Create the `MorseToneGenerator` using the AAudio/AudioTrack PCM logic |
| **2** | Implement the `LatencyTracker` to measure the time between audio finish and screen tap |
| **3** | Connect the ML Kit Prompt API to send session data to Gemini Nano |
| **4** | Build the Dynamic Grid UI that updates whenever `EXPAND_LIST` is received from the AI |

---

> **Next Step:** Generate the Android Manifest permissions and Gradle dependencies needed for the Tensor G5 / AI Edge SDK to complete the setup.
