package com.tdcreator.app

import android.app.Application
import android.content.Context
import com.tdcreator.core.data.prefs.PreferencesRepository
import com.tdcreator.core.i18n.LocaleManager
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class App : Application() {

    override fun attachBaseContext(base: Context) {
        // Apply the persisted language app-wide before any component is created.
        val lang = PreferencesRepository(base).languageBlocking()
        super.attachBaseContext(LocaleManager.wrap(base, lang))
    }
}
