package dev.sumanth.spd

import dev.sumanth.spd.utils.LocaleHelper
import dev.sumanth.spd.utils.StorageManager
import dev.sumanth.spd.utils.YtDlp
import androidx.compose.ui.res.stringResource
import android.content.Context
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import dev.sumanth.spd.model.NavigationItem
import dev.sumanth.spd.ui.component.Background
import dev.sumanth.spd.ui.component.BottomBar
import dev.sumanth.spd.ui.component.TopBar
import dev.sumanth.spd.ui.component.UpdateDialog
import dev.sumanth.spd.ui.screen.HomeScreen
import dev.sumanth.spd.ui.screen.PreferencesScreen
import dev.sumanth.spd.ui.viewmodel.HomeScreenViewModel
import dev.sumanth.spd.ui.viewmodel.UpdaterViewModel
import dev.sumanth.spd.utils.NewPipeDownloader
import dev.sumanth.spd.utils.SharedPref
import org.schabi.newpipe.extractor.NewPipe

class MainActivity : ComponentActivity() {

    private val navigationItems = listOf(
        NavigationItem(R.string.nav_home, Icons.Filled.Home, Icons.Outlined.Home),
        NavigationItem(R.string.nav_preferences, Icons.Filled.Settings, Icons.Outlined.Settings)
    )

    private val updateViewModel: UpdaterViewModel by viewModels()
    private val homeScreenViewModel: HomeScreenViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Default destination Download/Kugo Tunes (needs no choice from the user)
        Thread { StorageManager.prepareDefaultFolder() }.start()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        val sharedPref = SharedPref(this)
        if (savedInstanceState == null && sharedPref.getAutoUpdateCheck()) {
            updateViewModel.checkForUpdate(manual = false)
        }

        NewPipe.init(NewPipeDownloader.getInstance())

        // Only for a fresh launch: re-handling the share intent on recreate (rotation/language) would reset the queue
        if (savedInstanceState == null) handleIntent(intent)

        setContent {

            val pagerState = rememberPagerState(pageCount = { navigationItems.size })
            val title = stringResource(navigationItems[pagerState.currentPage].titleRes)

            Background {
                Scaffold(
                    topBar = { TopBar(title) },
                    bottomBar = { BottomBar(navigationItems, pagerState) }
                ) { innerPadding ->
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) { page ->
                        when (page) {
                            0 -> HomeScreen()
                            1 -> PreferencesScreen(updaterViewModel = updateViewModel)
                        }
                    }

                    UpdateDialog(updateViewModel)
                }
            }
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onResume() {
        super.onResume()
        updateViewModel.onResume()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (sharedText != null) {
                val regex = Regex("https?://[\\w\\d./?=&%-]+")
                val match = regex.find(sharedText)
                match?.value?.let { url ->
                    if (url.contains("open.spotify.com") || url.contains("spotify.link") ||
                        url.contains("youtube.com") || url.contains("youtu.be") ||
                        url.contains("tiktok.com") || url.contains("kwai.com") || url.contains("kw.ai") ||
                        url.contains("instagram.com") || url.contains("facebook.com") ||
                        url.contains("fb.watch") || url.contains("fb.com")) {
                        homeScreenViewModel.startFromSharedLink(url)
                    }
                }
            }
        }
    }
}
