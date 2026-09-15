package cn.edu.gzus.qingke

import android.app.Application

class QingkeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        app = this
    }

    companion object {
        lateinit var app: QingkeApp
            private set

        fun ready(): Boolean = this::app.isInitialized
    }
}
