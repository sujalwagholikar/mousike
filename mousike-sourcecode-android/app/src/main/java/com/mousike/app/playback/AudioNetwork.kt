package com.mousike.app.playback
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
/** Probing and playback reuse the same connection pool, DNS/TLS session and dispatcher. */
object AudioNetwork {
    val http: OkHttpClient by lazy {
        OkHttpClient.Builder().connectTimeout(3,TimeUnit.SECONDS).readTimeout(4,TimeUnit.SECONDS)
            .addInterceptor {chain ->
                val response=chain.proceed(chain.request())
                if(response.header("Content-Type").orEmpty().contains("text/html",true)) {
                    response.close();throw java.io.IOException("Source returned a webpage instead of audio")
                };response
            }.build()
    }
}
