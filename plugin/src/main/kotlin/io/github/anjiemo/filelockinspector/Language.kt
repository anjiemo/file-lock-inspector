package io.github.anjiemo.filelockinspector

import com.intellij.DynamicBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import java.text.MessageFormat
import java.util.Locale
import java.util.ResourceBundle
import javax.swing.*

@Service(Service.Level.APP)
@State(name = "FileLockInspector", storages = [Storage("file-lock-inspector.xml")])
class LanguageSettings : PersistentStateComponent<LanguageSettings.Data> {
    data class Data(var language: String = "IDE")
    private var data = Data()
    override fun getState() = data
    override fun loadState(state: Data) { data = state }
    companion object {
        fun get() = ApplicationManager.getApplication().getService(LanguageSettings::class.java)
    }
}

object Text {
    fun locale(mode: String, ideLocale: Locale): Locale = when (mode) {
        "ZH" -> Locale.SIMPLIFIED_CHINESE
        "EN" -> Locale.ENGLISH
        else -> if (ideLocale.language == "zh") Locale.SIMPLIFIED_CHINESE else Locale.ENGLISH
    }
    fun get(key: String, vararg args: Any?): String {
        val locale = locale(LanguageSettings.get().state.language, DynamicBundle.getLocale())
        val bundle = ResourceBundle.getBundle("messages.Inspector", locale, Text::class.java.classLoader,
            ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES))
        return MessageFormat(bundle.getString(key), locale).format(args)
    }
    fun status(code: String) = try { get("status.$code") } catch (_: java.util.MissingResourceException) { get("status.failed") }
}

class LanguageConfigurable : Configurable {
    private var combo: JComboBox<String>? = null
    private val modes = listOf("IDE", "ZH", "EN")
    override fun getDisplayName() = "File Lock Inspector"
    override fun createComponent(): JComponent {
        val selector = ComboBox(arrayOf(Text.get("language.ide"), Text.get("language.zh"), Text.get("language.en")))
        selector.setMinimumAndPreferredWidth(JBUI.scale(180))
        combo = selector
        return panel {
            row(Text.get("language")) {
                cell(selector)
            }
        }.also { reset() }
    }
    override fun isModified() = combo?.selectedIndex != modes.indexOf(LanguageSettings.get().state.language).coerceAtLeast(0)
    override fun apply() { LanguageSettings.get().state.language = modes[combo?.selectedIndex ?: 0] }
    override fun reset() { combo?.selectedIndex = modes.indexOf(LanguageSettings.get().state.language).coerceAtLeast(0) }
    override fun disposeUIResources() { combo = null }
}
