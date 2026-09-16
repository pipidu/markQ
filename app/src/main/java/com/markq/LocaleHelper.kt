package com.markq

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object LocaleHelper {
    val appLocale: Locale = Locale.CHINESE

    fun wrap(context: Context): Context {
        Locale.setDefault(appLocale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(appLocale)
        config.setLayoutDirection(appLocale)
        return context.createConfigurationContext(config)
    }
}
