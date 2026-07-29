package com.erfangholami.androidsolidservices.ui.main

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.erfangholami.androidsolidservices.R
import com.erfangholami.androidsolidservices.ui.navigation.MainPage

@Composable
fun MainPage(
    navController: NavController,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var showOverlayDialog by remember { mutableStateOf(!Settings.canDrawOverlays(context)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                showOverlayDialog = !Settings.canDrawOverlays(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (showOverlayDialog) {
        AlertDialog(
            onDismissRequest = {},
            properties = androidx.compose.ui.window.DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            ),
            title = {
                Text(
                    text = stringResource(R.string.overlay_permission_required)
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.overlay_permission_required_description)
                )
            },
            confirmButton = {
                Button(onClick = {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        "package:${context.packageName}".toUri()
                    )
                    context.startActivity(intent)
                }) {
                    Text(
                        text = stringResource(R.string.open_settings)
                    )
                }
            }
        )
    }

    val nestedNavController = rememberNavController()

    val bottomItems = remember {
        listOf(
            MainPage.MainNavBottomItem(
                R.string.access_grant, R.drawable.ic_access_grant,
                MainPage.AccessGrants
            ),
            MainPage.MainNavBottomItem(
                R.string.main, R.drawable.ic_main,
                MainPage.Main
            ),
            MainPage.MainNavBottomItem(
                R.string.setting, R.drawable.ic_setting,
                MainPage.Setting
            ),
        )
    }

    val changeTab: (_: Any) -> Unit = { tabRoute ->
        nestedNavController.navigate(tabRoute) {
            popUpTo(nestedNavController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by nestedNavController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                bottomItems.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(painterResource(screen.icon), contentDescription = null) },
                        label = { Text(stringResource(screen.title)) },
                        selected = currentDestination?.hierarchy?.any { it.hasRoute(screen.route::class) } == true,
                        onClick = {
                            changeTab(screen.route)
                        }
                    )
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = nestedNavController,
            startDestination = MainPage.Main,
            modifier = Modifier
                .systemBarsPadding()
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            composable<MainPage.AccessGrants> {
                AccessGrants(navController, hiltViewModel<AccessGrantViewModel>())
            }
            composable<MainPage.Main> {
                Main(navController, hiltViewModel<MainViewModel>())
            }
            composable<MainPage.Setting> {
                Setting(navController, hiltViewModel<SettingViewModel>())
            }
        }
    }
}
