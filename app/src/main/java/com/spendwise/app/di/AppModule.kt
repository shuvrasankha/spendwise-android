package com.spendwise.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.google.firebase.auth.FirebaseAuth
import com.spendwise.app.data.ai.AiHttp
import com.spendwise.app.data.ai.KeystoreCipher
import com.spendwise.app.data.ai.UrlConnectionHttp
import com.spendwise.app.data.ai.SecretCipher
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import javax.inject.Qualifier
import javax.inject.Singleton

/** The app-lifetime scope: shared live reads (data/Live.kt › SharedLive) run here, off the main thread. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppScope

/** noBackupFilesDir: never backed up or transferred; the encrypted BYOK keys live here. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class NoBackupDir

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton @AppScope
    fun appScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides @Singleton
    fun firebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    /** Offline persistence replaces the web's localStorage queue (spec §7.3). Settings must be set before first use. */
    @Provides @Singleton
    fun firestore(): FirebaseFirestore = FirebaseFirestore.getInstance().apply {
        firestoreSettings = FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
            .build()
    }

    @Provides @Singleton
    fun devicePrefsStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("device_prefs") }

    @Provides @NoBackupDir
    fun noBackupDir(@ApplicationContext context: Context): File = context.noBackupFilesDir

    @Provides
    fun secretCipher(cipher: KeystoreCipher): SecretCipher = cipher

    @Provides
    fun aiHttp(http: UrlConnectionHttp): AiHttp = http
}
