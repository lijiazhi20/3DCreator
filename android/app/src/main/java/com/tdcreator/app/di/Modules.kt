package com.tdcreator.app.di

import android.content.Context
import com.tdcreator.core.data.local.AppDatabase
import com.tdcreator.core.data.local.JobDao
import com.tdcreator.core.data.local.UploadDao
import com.tdcreator.core.data.prefs.PreferencesRepository
import com.tdcreator.core.network.ApiService
import com.tdcreator.core.network.RetrofitClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun providePreferences(@ApplicationContext context: Context): PreferencesRepository =
        PreferencesRepository(context)

    @Provides
    @Singleton
    fun provideApiService(prefs: PreferencesRepository): ApiService {
        val baseUrl = com.tdcreator.app.BuildConfig.BASE_URL
        return RetrofitClient.create(baseUrl, prefs)
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        androidx.room.Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME,
        ).fallbackToDestructiveMigration().build()

    @Provides
    fun provideJobDao(db: AppDatabase): JobDao = db.jobDao()

    @Provides
    fun provideUploadDao(db: AppDatabase): UploadDao = db.uploadDao()
}

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideJobRepository(
        api: ApiService,
        jobDao: JobDao,
    ) = com.tdcreator.core.data.repository.JobRepository(api, jobDao)

    @Provides
    @Singleton
    fun provideUploadRepository(
        @ApplicationContext context: Context,
        api: ApiService,
        uploadDao: UploadDao,
    ) = com.tdcreator.core.data.repository.UploadRepository(context, api, uploadDao)
}
