package com.omniai.core.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.omniai.core.model.AccentColor
import com.omniai.core.model.CurrencyCode
import com.omniai.core.model.ThemeMode
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.Test
import org.junit.Assert.assertEquals

class SettingsDataSourceTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun `round trips settings and clamps values`() = runTest(UnconfinedTestDispatcher()) {
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { java.io.File(tmp.root, "t.preferences_pb") })
        val source = SettingsDataSource(store)
        assertEquals(ThemeMode.SYSTEM, source.current().themeMode)
        source.update { it.copy(themeMode = ThemeMode.DARK, accent = AccentColor.TEAL, textScale = 9f, currency = it.currency.copy(display = CurrencyCode.BND)) }
        val s = source.current()
        assertEquals(ThemeMode.DARK, s.themeMode)
        assertEquals(AccentColor.TEAL, s.accent)
        assertEquals(1.6f, s.textScale)
        assertEquals(CurrencyCode.BND, s.currency.display)
    }

    @Test fun `corrupt json falls back to defaults`() {
        val source = SettingsDataSource(PreferenceDataStoreFactory.create(produceFile = { java.io.File(tmp.root, "x.preferences_pb") }))
        assertEquals(ThemeMode.SYSTEM, source.decode("{not json").themeMode)
        assertEquals(ThemeMode.SYSTEM, source.decode(null).themeMode)
    }
}
