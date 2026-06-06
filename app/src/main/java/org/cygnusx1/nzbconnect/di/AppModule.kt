package org.cygnusx1.nzbconnect.di

import android.content.Context
import androidx.room.Room
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.cygnusx1.nzbconnect.data.local.AppDatabase
import org.cygnusx1.nzbconnect.data.local.CategoryDao
import org.cygnusx1.nzbconnect.data.local.IndexerDao
import org.cygnusx1.nzbconnect.data.local.SearchHistoryDao
import org.cygnusx1.nzbconnect.data.newznab.NewznabApi
import org.cygnusx1.nzbconnect.data.prefs.SecurePrefs
import org.cygnusx1.nzbconnect.data.sab.SabnzbdApi
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Both APIs use dynamic per-host URLs via Retrofit `@Url`, so a single Retrofit is built
 * with a throwaway base URL. SAB JSON goes through kotlinx.serialization; Newznab XML is
 * returned as a raw [okhttp3.ResponseBody] and parsed by hand.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    private const val PLACEHOLDER_BASE = "http://localhost/"

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideOkHttp(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        return OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(PLACEHOLDER_BASE)
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    @Provides
    @Singleton
    fun provideNewznabApi(retrofit: Retrofit): NewznabApi = retrofit.create(NewznabApi::class.java)

    @Provides
    @Singleton
    fun provideSabnzbdApi(retrofit: Retrofit): SabnzbdApi = retrofit.create(SabnzbdApi::class.java)

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        @Suppress("DEPRECATION")
        Room.databaseBuilder(context, AppDatabase::class.java, "nzbconnect.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideIndexerDao(db: AppDatabase): IndexerDao = db.indexerDao()

    @Provides
    fun provideCategoryDao(db: AppDatabase): CategoryDao = db.categoryDao()

    @Provides
    fun provideSearchHistoryDao(db: AppDatabase): SearchHistoryDao = db.searchHistoryDao()

    @Provides
    @Singleton
    fun provideSecurePrefs(@ApplicationContext context: Context): SecurePrefs = SecurePrefs(context)
}
