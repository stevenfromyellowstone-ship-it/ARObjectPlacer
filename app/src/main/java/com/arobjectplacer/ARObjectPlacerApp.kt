package com.arobjectplacer

import android.app.Application

class ARObjectPlacerApp : Application() {
    lateinit var objectStore: ObjectStore
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        objectStore = ObjectStore(this)
    }

    companion object {
        lateinit var instance: ARObjectPlacerApp
            private set
    }
}
