package rw.itunda.agent

import android.app.Application
import rw.itunda.agent.network.NetworkClient

class AgentApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        NetworkClient.init(this)
    }
}
