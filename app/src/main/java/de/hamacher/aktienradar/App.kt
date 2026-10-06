package de.hamacher.aktienradar

import android.app.Application
import de.hamacher.aktienradar.data.Repo
import de.hamacher.aktienradar.work.Notifier

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Repo.init(this)
        Notifier.createChannel(this)
    }
}
