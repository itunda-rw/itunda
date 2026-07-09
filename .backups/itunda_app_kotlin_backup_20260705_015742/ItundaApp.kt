package com.itunda.app

import android.app.Application
import com.itunda.app.data.api.RetrofitClient

class ItundaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        RetrofitClient.init(this)
    }
}
