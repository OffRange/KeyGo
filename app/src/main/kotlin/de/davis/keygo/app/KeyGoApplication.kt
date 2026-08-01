package de.davis.keygo.app

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.view.WindowManager
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.androidx.workmanager.koin.workManagerFactory
import org.koin.core.annotation.KoinApplication
import org.koin.plugin.module.dsl.startKoin

@KoinApplication
class KeyGoApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        startKoin<KeyGoApplication> {
            androidLogger()
            androidContext(this@KeyGoApplication)
            workManagerFactory()
        }

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, p1: Bundle?) {
                activity.window.setFlags(
                    WindowManager.LayoutParams.FLAG_SECURE,
                    WindowManager.LayoutParams.FLAG_SECURE
                )
            }

            override fun onActivityDestroyed(p0: Activity) {}
            override fun onActivityPaused(p0: Activity) {}
            override fun onActivityResumed(p0: Activity) {}
            override fun onActivitySaveInstanceState(p0: Activity, p1: Bundle) {}
            override fun onActivityStarted(p0: Activity) {}
            override fun onActivityStopped(p0: Activity) {}
        })
    }
}