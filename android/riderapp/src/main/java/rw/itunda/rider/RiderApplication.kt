package rw.itunda.rider

import android.app.Application
import rw.itunda.rider.network.NetworkClient

class RiderApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        NetworkClient.init(this)
    }
}
