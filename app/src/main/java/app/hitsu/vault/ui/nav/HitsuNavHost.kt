package app.hitsu.vault.ui.nav

import android.os.SystemClock
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.hitsu.vault.domain.VaultState
import app.hitsu.vault.domain.MediaFilter
import app.hitsu.vault.domain.MediaType
import app.hitsu.vault.ui.home.HomeRoute
import app.hitsu.vault.ui.lock.LockRoute
import app.hitsu.vault.ui.setup.SetupRoute
import app.hitsu.vault.ui.player.VideoPlayerRoute
import app.hitsu.vault.ui.player.VideoPlayerViewModel
import app.hitsu.vault.ui.download.DownloadRoute
import app.hitsu.vault.ui.download.DownloadViewModel
import app.hitsu.vault.ui.albums.AlbumRoute
import app.hitsu.vault.ui.albums.AlbumViewModel
import app.hitsu.vault.ui.settings.AboutScreen
import app.hitsu.vault.ui.settings.AlbumsSettingsRoute
import app.hitsu.vault.ui.settings.BackupRoute
import app.hitsu.vault.ui.settings.ChangePinRoute
import app.hitsu.vault.ui.settings.AutoLockRoute
import app.hitsu.vault.ui.settings.SettingsRoute
import app.hitsu.vault.ui.settings.YtDlpRoute
import app.hitsu.vault.ui.splash.SplashScreen
import app.hitsu.vault.ui.viewer.PhotoViewerRoute
import app.hitsu.vault.ui.viewer.PhotoViewerViewModel
import app.hitsu.vault.ui.theme.HitsuColors
import app.hitsu.vault.ui.theme.HitsuMotion
import kotlinx.coroutines.delay

/** Spec §7.2: the splash stays up until the Keystore answers, but at least this long. */
private const val SPLASH_MIN_MS = 400L

/** The vault state owns top-level navigation: screens never navigate to setup/lock/home themselves. */
@Composable
fun HitsuNavHost(vaultState: VaultState, pendingLink: String?, onLinkHandled: () -> Unit) {
    val navController = rememberNavController()
    val startDestination = remember { vaultState.route() }
    val startedAt = remember { SystemClock.uptimeMillis() }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = Modifier
            .fillMaxSize()
            .background(HitsuColors.Bg),
        enterTransition = { fadeIn(HitsuMotion.standard()) },
        exitTransition = { fadeOut(HitsuMotion.standard()) },
        popEnterTransition = { fadeIn(HitsuMotion.standard()) },
        popExitTransition = { fadeOut(HitsuMotion.standard()) },
    ) {
        composable(Routes.SPLASH) { SplashScreen() }
        composable(Routes.SETUP) { SetupRoute() }
        composable(Routes.LOCK) { LockRoute() }
        composable(Routes.HOME) {
            HomeRoute(
                onOpenPhoto = { id, filter -> navController.navigate(Routes.photo(id, filter)) },
                onOpenVideo = { id -> navController.navigate(Routes.video(id)) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenAlbum = { navController.navigate(Routes.album(it)) },
            )
        }
        composable(
            route = Routes.PHOTO,
            arguments = listOf(
                navArgument(PhotoViewerViewModel.ARG_ID) { type = NavType.StringType },
                navArgument(PhotoViewerViewModel.ARG_FILTER) {
                    type = NavType.StringType
                    defaultValue = MediaFilter.All.name
                },
            ),
        ) {
            PhotoViewerRoute(onClose = { navController.popBackStack() })
        }
        composable(
            route = Routes.DOWNLOAD,
            arguments = listOf(navArgument(DownloadViewModel.ARG_URL) { type = NavType.StringType }),
        ) {
            DownloadRoute(onClose = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsRoute(
                onBack = { navController.popBackStack() },
                onOpenAutoLock = { navController.navigate(Routes.SETTINGS_AUTO_LOCK) },
                onOpenYtDlp = { navController.navigate(Routes.SETTINGS_YTDLP) },
                onOpenAlbums = { navController.navigate(Routes.SETTINGS_ALBUMS) },
                onOpenChangePin = { navController.navigate(Routes.SETTINGS_CHANGE_PIN) },
                onOpenBackup = { navController.navigate(Routes.SETTINGS_BACKUP) },
                onOpenAbout = { navController.navigate(Routes.SETTINGS_ABOUT) },
            )
        }
        composable(Routes.SETTINGS_AUTO_LOCK) {
            AutoLockRoute(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS_YTDLP) {
            YtDlpRoute(
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.SETTINGS_ALBUMS) {
            AlbumsSettingsRoute(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS_CHANGE_PIN) {
            ChangePinRoute(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS_BACKUP) {
            BackupRoute(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS_ABOUT) {
            AboutScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = Routes.ALBUM,
            arguments = listOf(navArgument(AlbumViewModel.ARG_ID) { type = NavType.StringType }),
        ) {
            AlbumRoute(
                onOpen = { item ->
                    if (item.type == MediaType.Video) {
                        navController.navigate(Routes.video(item.id))
                    } else {
                        navController.navigate(Routes.photo(item.id, MediaFilter.All))
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.VIDEO,
            arguments = listOf(navArgument(VideoPlayerViewModel.ARG_ID) { type = NavType.StringType }),
        ) {
            VideoPlayerRoute(onClose = { navController.popBackStack() })
        }
    }

    /*
     * A shared link is handled here rather than on a screen: it can arrive while the user is in the
     * viewer, in settings or anywhere else, and it would be lost if only home listened for it.
     */
    LaunchedEffect(pendingLink, vaultState) {
        if (pendingLink == null || vaultState != VaultState.Unlocked) return@LaunchedEffect
        onLinkHandled()
        navController.navigate(Routes.download(pendingLink))
    }

    LaunchedEffect(vaultState) {
        if (vaultState == VaultState.Unknown) return@LaunchedEffect
        val target = vaultState.route()
        if (navController.currentDestination?.route == Routes.SPLASH) {
            val shown = SystemClock.uptimeMillis() - startedAt
            if (shown < SPLASH_MIN_MS) delay(SPLASH_MIN_MS - shown)
        }
        val current = navController.currentDestination?.route
        // Unlocked means any screen behind the lock is fine, viewer included.
        val misplaced = if (vaultState == VaultState.Unlocked) {
            current == Routes.SPLASH || current == Routes.SETUP || current == Routes.LOCK
        } else {
            current != target
        }
        if (misplaced) {
            navController.navigate(target) {
                popUpTo(navController.graph.id) { inclusive = true }
                launchSingleTop = true
            }
        }
    }
}
