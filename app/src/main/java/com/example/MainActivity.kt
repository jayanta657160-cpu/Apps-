package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.local.AureliaLocalDatabase
import com.example.data.local.CapturedPhotoRepository
import com.example.data.local.LocalPresetRepository
import com.example.ui.CameraScreen
import com.example.ui.CapturesScreen
import com.example.ui.StudioScreen
import com.example.ui.theme.AureliaTheme
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.DeepStudioTeal
import com.example.ui.theme.MutedSage
import com.example.ui.theme.ObsidianTeal
import com.example.ui.theme.WarmIvory
import com.example.viewmodel.AureliaViewModel

private enum class AppTab {
    CAMERA, CAPTURES, STUDIO
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(ObsidianTeal.toArgb()),
            navigationBarStyle = SystemBarStyle.dark(DeepStudioTeal.toArgb())
        )
        setContent {
            AureliaTheme {
                AppNavigation()
            }
        }
    }
}

@Composable
private fun AppNavigation() {
    val viewModel: AureliaViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val localDb = AureliaLocalDatabase.getInstance(app)
                AureliaViewModel(
                    localPresetRepository = LocalPresetRepository(localDb.localPresetDao()),
                    capturedPhotoRepository = CapturedPhotoRepository(localDb.capturedPhotoDao())
                )
            }
        }
    )
    MainAppShell(viewModel = viewModel)
}

@Composable
private fun MainAppShell(
    viewModel: AureliaViewModel
) {
    var activeTab by rememberSaveable { mutableStateOf(AppTab.CAMERA) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.statusMessages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    if (activeTab != AppTab.CAMERA) {
        BackHandler { activeTab = AppTab.CAMERA }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 720.dp

        Scaffold(
            containerColor = ObsidianTeal,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (!useRail) {
                    NavigationBar(
                        containerColor = DeepStudioTeal,
                        contentColor = WarmIvory
                    ) {
                        NavigationBarItem(
                            selected = activeTab == AppTab.CAMERA,
                            onClick = { activeTab = AppTab.CAMERA },
                            icon = {
                                Icon(
                                    if (activeTab == AppTab.CAMERA) Icons.Filled.CameraAlt else Icons.Outlined.CameraAlt,
                                    contentDescription = stringResource(id = R.string.nav_camera)
                                )
                            },
                            label = { Text(stringResource(id = R.string.nav_camera)) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = ObsidianTeal,
                                selectedTextColor = ChampagneGold,
                                indicatorColor = ChampagneGold,
                                unselectedIconColor = MutedSage,
                                unselectedTextColor = MutedSage
                            ),
                            modifier = Modifier.testTag("nav_camera_tab")
                        )
                        NavigationBarItem(
                            selected = activeTab == AppTab.CAPTURES,
                            onClick = { activeTab = AppTab.CAPTURES },
                            icon = {
                                Icon(
                                    if (activeTab == AppTab.CAPTURES) Icons.Filled.PhotoLibrary else Icons.Outlined.PhotoLibrary,
                                    contentDescription = stringResource(id = R.string.nav_captures)
                                )
                            },
                            label = { Text(stringResource(id = R.string.nav_captures)) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = ObsidianTeal,
                                selectedTextColor = ChampagneGold,
                                indicatorColor = ChampagneGold,
                                unselectedIconColor = MutedSage,
                                unselectedTextColor = MutedSage
                            ),
                            modifier = Modifier.testTag("nav_captures_tab")
                        )
                        NavigationBarItem(
                            selected = activeTab == AppTab.STUDIO,
                            onClick = { activeTab = AppTab.STUDIO },
                            icon = {
                                Icon(
                                    if (activeTab == AppTab.STUDIO) Icons.Filled.Tune else Icons.Outlined.Tune,
                                    contentDescription = stringResource(id = R.string.nav_studio)
                                )
                            },
                            label = { Text(stringResource(id = R.string.nav_studio)) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = ObsidianTeal,
                                selectedTextColor = ChampagneGold,
                                indicatorColor = ChampagneGold,
                                unselectedIconColor = MutedSage,
                                unselectedTextColor = MutedSage
                            ),
                            modifier = Modifier.testTag("nav_studio_tab")
                        )
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(
                        containerColor = DeepStudioTeal,
                        contentColor = WarmIvory
                    ) {
                        NavigationRailItem(
                            selected = activeTab == AppTab.CAMERA,
                            onClick = { activeTab = AppTab.CAMERA },
                            icon = {
                                Icon(
                                    if (activeTab == AppTab.CAMERA) Icons.Filled.CameraAlt else Icons.Outlined.CameraAlt,
                                    contentDescription = stringResource(id = R.string.nav_camera)
                                )
                            },
                            label = { Text(stringResource(id = R.string.nav_camera)) },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = ObsidianTeal,
                                selectedTextColor = ChampagneGold,
                                indicatorColor = ChampagneGold,
                                unselectedIconColor = MutedSage,
                                unselectedTextColor = MutedSage
                            )
                        )
                        NavigationRailItem(
                            selected = activeTab == AppTab.CAPTURES,
                            onClick = { activeTab = AppTab.CAPTURES },
                            icon = {
                                Icon(
                                    if (activeTab == AppTab.CAPTURES) Icons.Filled.PhotoLibrary else Icons.Outlined.PhotoLibrary,
                                    contentDescription = stringResource(id = R.string.nav_captures)
                                )
                            },
                            label = { Text(stringResource(id = R.string.nav_captures)) },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = ObsidianTeal,
                                selectedTextColor = ChampagneGold,
                                indicatorColor = ChampagneGold,
                                unselectedIconColor = MutedSage,
                                unselectedTextColor = MutedSage
                            )
                        )
                        NavigationRailItem(
                            selected = activeTab == AppTab.STUDIO,
                            onClick = { activeTab = AppTab.STUDIO },
                            icon = {
                                Icon(
                                    if (activeTab == AppTab.STUDIO) Icons.Filled.Tune else Icons.Outlined.Tune,
                                    contentDescription = stringResource(id = R.string.nav_studio)
                                )
                            },
                            label = { Text(stringResource(id = R.string.nav_studio)) },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = ObsidianTeal,
                                selectedTextColor = ChampagneGold,
                                indicatorColor = ChampagneGold,
                                unselectedIconColor = MutedSage,
                                unselectedTextColor = MutedSage
                            )
                        )
                    }
                }

                when (activeTab) {
                    AppTab.CAMERA -> CameraScreen(
                        viewModel = viewModel,
                        onOpenStudio = { activeTab = AppTab.STUDIO }
                    )
                    AppTab.CAPTURES -> CapturesScreen(
                        viewModel = viewModel,
                        onOpenCamera = { activeTab = AppTab.CAMERA },
                        onOpenStudio = { activeTab = AppTab.STUDIO }
                    )
                    AppTab.STUDIO -> StudioScreen(viewModel = viewModel)
                }
            }
        }
    }
}
