package com.scd.android

import android.content.Context
import android.content.ContextWrapper
import java.util.Locale

fun LocaleHelper.wrap(base: Context, lang: String): Context {
    if (lang == "system") return base
    val locale = Locale(lang)
    Locale.setDefault(locale)
    val config = android.content.res.Configuration(base.resources.configuration)
    config.setLocale(locale)
    return ContextWrapper(base.createConfigurationContext(config))
}
