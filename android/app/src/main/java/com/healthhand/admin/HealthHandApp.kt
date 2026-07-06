package com.healthhand.admin

import android.app.Application
import com.healthhand.admin.data.ApiClient
import com.healthhand.admin.data.TokenStore

class HealthHandApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val tokenStore = TokenStore(this)
        ApiClient.init(tokenStore)
    }
}