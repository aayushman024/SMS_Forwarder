package com.mnivesh.smsforwarder.api

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitInstance {
    // replace with your actual server URL
    private const val BASE_URL = "https://callyn-backend-avh8cae5dpdnckg8.centralindia-01.azurewebsites.net/"
    private const val LOCAL_URL = "http://192.168.1.36:5500/"

    val api: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(LOCAL_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}