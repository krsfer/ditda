package com.morse.master.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppTabPagerMappingTest {
    @Test
    fun `maps app tabs to pager pages and back`() {
        assertThat(tabToPage(AppTab.PRACTICE)).isEqualTo(0)
        assertThat(tabToPage(AppTab.TEXT)).isEqualTo(1)
        assertThat(tabToPage(AppTab.SETTINGS)).isEqualTo(2)
        assertThat(pageToTab(0)).isEqualTo(AppTab.PRACTICE)
        assertThat(pageToTab(1)).isEqualTo(AppTab.TEXT)
        assertThat(pageToTab(2)).isEqualTo(AppTab.SETTINGS)
    }
}
