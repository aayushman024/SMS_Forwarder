package com.mnivesh.smsforwarder.api

import android.content.Context
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import okhttp3.OkHttpClient

object RetrofitInstance {
    // replace with your actual server URL
    private const val BASE_URL = "https://callyn-backend-avh8cae5dpdnckg8.centralindia-01.azurewebsites.net/"
    private const val LOCAL_URL = "http://192.168.1.34:5000/"

    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder().apply {
            appContext?.let { addInterceptor(AuthInterceptor(it)) }
        }.build()
    }

    val api: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
