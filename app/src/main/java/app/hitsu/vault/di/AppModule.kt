package app.hitsu.vault.di

import android.content.Context
import android.os.storage.StorageManager
import androidx.core.content.getSystemService
import app.hitsu.vault.crypto.BiometricKey
import app.hitsu.vault.crypto.KeystoreBiometricKey
import app.hitsu.vault.crypto.KeyWrapper
import app.hitsu.vault.crypto.KeystoreKeyWrapper
import app.hitsu.vault.crypto.VaultCrypto
import androidx.room.Room
import app.hitsu.vault.data.CryptoVaultGateway
import app.hitsu.vault.data.AlbumPreferences
import app.hitsu.vault.data.AlbumRepository
import app.hitsu.vault.data.MediaRepository
import app.hitsu.vault.data.SharedImportWatcher
import app.hitsu.vault.data.VaultSessionCleaner
import app.hitsu.vault.data.db.HitsuDatabase
import app.hitsu.vault.data.db.AlbumDao
import app.hitsu.vault.data.db.MediaDao
import app.hitsu.vault.data.media.ContentImportSources
import app.hitsu.vault.data.backup.BackupStore
import app.hitsu.vault.data.media.ExifSanitizer
import app.hitsu.vault.data.media.MediaExporter
import app.hitsu.vault.data.media.MediaImporter
import app.hitsu.vault.data.media.PlaybackCache
import app.hitsu.vault.data.download.CookieStore
import app.hitsu.vault.data.download.DownloadCoordinator
import app.hitsu.vault.data.download.DownloadNotifications
import app.hitsu.vault.data.download.PendingLinks
import app.hitsu.vault.data.download.YtDlpEngine
import app.hitsu.vault.data.media.SharedIntake
import app.hitsu.vault.data.media.VideoFrames
import app.hitsu.vault.data.media.ThumbnailFactory
import app.hitsu.vault.data.media.VaultFiles
import app.hitsu.vault.data.FileVaultMetaStore
import app.hitsu.vault.data.SystemBiometricAvailability
import app.hitsu.vault.data.VaultMetaStore
import app.hitsu.vault.domain.BiometricAvailability
import app.hitsu.vault.domain.Clock
import app.hitsu.vault.domain.VaultGateway
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import javax.inject.Singleton

private const val ALBUM_PREFS = "hitsu.albums"
private const val KEYSTORE_ALIAS = "hitsu.vault.dek.wrap"
private const val BIOMETRIC_ALIAS = "hitsu.vault.dek.biometric"
private const val META_FILE = "vault/meta.json"
private const val VAULT_DIR = "vault"
private const val STAGING_DIR = "staging"
private const val SHARED_DIR = "shared"
private const val DOWNLOAD_DIR = "downloads"
private const val COOKIES_DIR = "vault/cookies"
private const val DATABASE = "hitsu.db"
private const val PLAYBACK_DIR = "playback"

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @CryptoDispatcher
    fun provideCryptoDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock { System.currentTimeMillis() }

    @Provides
    @Singleton
    fun provideKeyWrapper(): KeyWrapper = KeystoreKeyWrapper(KEYSTORE_ALIAS)

    @Provides
    @Singleton
    fun provideBiometricKey(): BiometricKey = KeystoreBiometricKey(BIOMETRIC_ALIAS)

    @Provides
    @Singleton
    fun provideVaultCrypto(keyWrapper: KeyWrapper): VaultCrypto = VaultCrypto(keyWrapper)

    @Provides
    @Singleton
    fun provideVaultMetaStore(
        @ApplicationContext context: Context,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ): VaultMetaStore = FileVaultMetaStore(File(context.filesDir, META_FILE), ioDispatcher)

    @Provides
    @Singleton
    fun provideVaultGateway(
        crypto: VaultCrypto,
        biometricKey: BiometricKey,
        store: VaultMetaStore,
        clock: Clock,
        @CryptoDispatcher cryptoDispatcher: CoroutineDispatcher,
        @ApplicationScope scope: CoroutineScope,
    ): VaultGateway = CryptoVaultGateway(crypto, biometricKey, store, clock, cryptoDispatcher, scope)

    @Provides
    @Singleton
    fun provideVaultFiles(@ApplicationContext context: Context): VaultFiles =
        VaultFiles(File(context.filesDir, VAULT_DIR))

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): HitsuDatabase =
        Room.databaseBuilder(context, HitsuDatabase::class.java, DATABASE)
            .addMigrations(HitsuDatabase.MIGRATION_1_2, HitsuDatabase.MIGRATION_2_3)
            .build()

    @Provides
    fun provideMediaDao(database: HitsuDatabase): MediaDao = database.mediaDao()

    @Provides
    fun provideAlbumDao(database: HitsuDatabase): AlbumDao = database.albumDao()

    @Provides
    @Singleton
    fun provideThumbnailFactory(): ThumbnailFactory = ThumbnailFactory()

    @Provides
    @Singleton
    fun provideMediaImporter(
        @ApplicationContext context: Context,
        files: VaultFiles,
        dao: MediaDao,
        thumbnails: ThumbnailFactory,
        clock: Clock,
    ): MediaImporter = MediaImporter(
        files = files,
        dao = dao,
        thumbnails = thumbnails,
        videos = VideoFrames(thumbnails),
        exif = ExifSanitizer(File(context.cacheDir, STAGING_DIR)),
        clock = clock,
    )

    @Provides
    @Singleton
    fun providePlaybackCache(
        @ApplicationContext context: Context,
        files: VaultFiles,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ): PlaybackCache = PlaybackCache(
        directory = File(context.cacheDir, PLAYBACK_DIR),
        files = files,
        storage = context.getSystemService<StorageManager>(),
        ioDispatcher = ioDispatcher,
    )

    @Provides
    @Singleton
    fun provideSharedIntake(
        @ApplicationContext context: Context,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
        @ApplicationScope scope: CoroutineScope,
    ): SharedIntake = SharedIntake(
        sources = ContentImportSources(context.contentResolver),
        stagingDir = File(context.cacheDir, SHARED_DIR),
        ioDispatcher = ioDispatcher,
        appScope = scope,
    )

    @Provides
    @Singleton
    fun provideAlbumRepository(
        albumDao: AlbumDao,
        clock: Clock,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ): AlbumRepository = AlbumRepository(albumDao, clock, ioDispatcher)

    @Provides
    @Singleton
    fun provideAlbumPreferences(@ApplicationContext context: Context): AlbumPreferences =
        AlbumPreferences(context.getSharedPreferences(ALBUM_PREFS, Context.MODE_PRIVATE))

    @Provides
    @Singleton
    fun provideMediaRepository(
        @ApplicationContext context: Context,
        albums: AlbumRepository,
        albumPreferences: AlbumPreferences,
        dao: MediaDao,
        importer: MediaImporter,
        files: VaultFiles,
        clock: Clock,
        playback: PlaybackCache,
        intake: SharedIntake,
        vault: VaultGateway,
        @ApplicationScope scope: CoroutineScope,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ): MediaRepository = MediaRepository(
        resolver = context.contentResolver,
        albums = albums,
        albumPreferences = albumPreferences,
        backups = BackupStore(
            dao = dao,
            albums = albums,
            files = files,
            vault = vault,
            clock = clock,
            stagingDir = File(context.cacheDir, STAGING_DIR),
            ioDispatcher = ioDispatcher,
        ),
        dao = dao,
        importer = importer,
        exporter = MediaExporter(
            resolver = context.contentResolver,
            stagingDir = File(context.cacheDir, STAGING_DIR),
            sanitizer = ExifSanitizer(File(context.cacheDir, STAGING_DIR)),
        ),
        files = files,
        sources = ContentImportSources(context.contentResolver),
        playback = playback,
        intake = intake,
        vault = vault,
        appScope = scope,
        ioDispatcher = ioDispatcher,
    )

    @Provides
    @Singleton
    fun providePendingLinks(): PendingLinks = PendingLinks()

    @Provides
    @Singleton
    fun provideYtDlpEngine(
        @ApplicationContext context: Context,
        clock: Clock,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ): YtDlpEngine = YtDlpEngine(context, clock, ioDispatcher)

    @Provides
    @Singleton
    fun provideDownloadNotifications(
        @ApplicationContext context: Context,
    ): DownloadNotifications = DownloadNotifications(context)

    @Provides
    @Singleton
    fun provideCookieStore(
        @ApplicationContext context: Context,
        vault: VaultGateway,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ): CookieStore = CookieStore(File(context.filesDir, COOKIES_DIR), vault, ioDispatcher)

    @Provides
    @Singleton
    fun provideDownloadCoordinator(
        @ApplicationContext context: Context,
        engine: YtDlpEngine,
        repository: MediaRepository,
        cookies: CookieStore,
        notifications: DownloadNotifications,
        @ApplicationScope scope: CoroutineScope,
    ): DownloadCoordinator = DownloadCoordinator(
        context = context,
        engine = engine,
        repository = repository,
        cookies = cookies,
        notifications = notifications,
        directory = File(context.cacheDir, DOWNLOAD_DIR),
        appScope = scope,
    )

    @Provides
    @Singleton
    fun provideVaultSessionCleaner(
        @ApplicationContext context: Context,
        vault: VaultGateway,
        repository: MediaRepository,
        @ApplicationScope scope: CoroutineScope,
    ): VaultSessionCleaner = VaultSessionCleaner(context, vault, repository, scope)

    @Provides
    @Singleton
    fun provideSharedImportWatcher(
        vault: VaultGateway,
        repository: MediaRepository,
        @ApplicationScope scope: CoroutineScope,
    ): SharedImportWatcher = SharedImportWatcher(vault, repository, scope)

    @Provides
    fun provideBiometricAvailability(@ApplicationContext context: Context): BiometricAvailability =
        SystemBiometricAvailability(context)
}
