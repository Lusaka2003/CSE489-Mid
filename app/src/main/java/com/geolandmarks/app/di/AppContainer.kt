package com.geolandmarks.app.di

import android.content.Context
import androidx.room.Room
import com.geolandmarks.app.BuildConfig
import com.geolandmarks.app.data.local.AppDatabase
import com.geolandmarks.app.data.location.LocationTracker
import com.geolandmarks.app.data.prefs.ApiKeyStore
import com.geolandmarks.app.data.remote.LandmarkApi
import com.geolandmarks.app.data.repo.LandmarkRepository
import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: AppDatabase = Room.databaseBuilder(
        appContext,
        AppDatabase::class.java,
        "landmarks.db"
    ).fallbackToDestructiveMigration().build()

    val apiKeyStore = ApiKeyStore(appContext)

    private val okHttp = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(
            HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
        )
        .build()

    private val gson = GsonBuilder().setLenient().create()

    val api: LandmarkApi = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(okHttp)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()
        .create(LandmarkApi::class.java)

    val locationTracker = LocationTracker(appContext)

    val repository = LandmarkRepository(
        context = appContext,
        api = api,
        db = database,
        apiKeyStore = apiKeyStore
    )
}
