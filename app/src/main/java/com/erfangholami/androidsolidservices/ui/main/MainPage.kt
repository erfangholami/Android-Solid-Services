package com.erfangholami.androidsolidservices.ui.main

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
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
