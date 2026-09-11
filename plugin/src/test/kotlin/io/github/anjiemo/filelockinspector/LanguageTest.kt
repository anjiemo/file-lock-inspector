package io.github.anjiemo.filelockinspector

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.Locale
import java.util.Properties

class LanguageTest {
    @Test fun `language resolution obeys override and falls back to English`() {
        assertEquals(Locale.ENGLISH, Text.locale("EN", Locale.CHINA))
        assertEquals(Locale.SIMPLIFIED_CHINESE, Text.locale("ZH", Locale.ENGLISH))
        assertEquals(Locale.SIMPLIFIED_CHINESE, Text.locale("IDE", Locale.TAIWAN))
        assertEquals(Locale.ENGLISH, Text.locale("IDE", Locale.JAPAN))
    }
    @Test fun `both language bundles contain exactly the same keys`() {
        fun read(suffix: String) = Properties().apply {
            LanguageTest::class.java.getResourceAsStream("/messages/Inspector$suffix.properties")!!.reader(Charsets.UTF_8).use { load(it) }
        }
        assertEquals(read("").keys, read("_zh_CN").keys)
    }
}
