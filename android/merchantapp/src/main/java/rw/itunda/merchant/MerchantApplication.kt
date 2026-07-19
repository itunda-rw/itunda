package rw.itunda.merchant

import android.app.Application
import rw.itunda.merchant.network.NetworkClient

class MerchantApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        NetworkClient.init(this)
    }
}
