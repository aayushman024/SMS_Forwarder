package com.mnivesh.smsforwarder.api

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitInstance {
    // replace with your actual server URL
    private const val BASE_URL = "https://callyn-backend-avh8cae5dpdnckg8.centralindia-01.azurewebsites.net/"

    val api: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}