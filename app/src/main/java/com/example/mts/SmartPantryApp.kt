package com.example.mts

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.example.mts.data.Api
import com.example.mts.data.TokenStore
import com.example.mts.data.createApi
import com.example.mts.data.createHttpClient

class SmartPantryApp : Application(), SingletonImageLoader.Factory {
    private val httpClient by lazy { createHttpClient() }

    val tokens by lazy { TokenStore(this) }
    val api: Api by lazy { createApi(tokens, httpClient) }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { httpClient })) }
            .build()
}
