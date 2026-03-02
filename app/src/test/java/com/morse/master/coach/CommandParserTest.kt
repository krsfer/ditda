package com.morse.master.coach

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CommandParserTest {
    @Test
    fun `parses wake phrase commands when wake phrase is required`() {
        val parser = CommandParser(wakePhraseRequired = true)

        assertThat(parser.parse("Coach start session")).isEqualTo(CoachVoiceCommand.START_SESSION)
        assertThat(parser.parse("Coach pause")).isEqualTo(CoachVoiceCommand.PAUSE)
        assertThat(parser.parse("Coach resume")).isEqualTo(CoachVoiceCommand.RESUME)
        assertThat(parser.parse("Coach repeat")).isEqualTo(CoachVoiceCommand.REPEAT)
        assertThat(parser.parse("Coach slower")).isEqualTo(CoachVoiceCommand.SLOWER)
        assertThat(parser.parse("Coach faster")).isEqualTo(CoachVoiceCommand.FASTER)
        assertThat(parser.parse("Coach stop")).isEqualTo(CoachVoiceCommand.STOP)
        assertThat(parser.parse("Coach continue")).isEqualTo(CoachVoiceCommand.CONTINUE)
    }

    @Test
    fun `ignores commands without wake phrase when required`() {
        val parser = CommandParser(wakePhraseRequired = true)

        assertThat(parser.parse("pause")).isNull()
        assertThat(parser.parse("start session")).isNull()
    }

    @Test
    fun `accepts direct commands when wake phrase is disabled`() {
        val parser = CommandParser(wakePhraseRequired = false)

        assertThat(parser.parse("pause")).isEqualTo(CoachVoiceCommand.PAUSE)
        assertThat(parser.parse(" start session ")).isEqualTo(CoachVoiceCommand.START_SESSION)
    }
}
