package com.erfangholami.androidsolidservices.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.erfangholami.androidsolidservices.ui.login.LoginViewModel
import com.erfangholami.androidsolidservices.ui.main.MainPage
import com.erfangholami.androidsolidservices.ui.startup.Startup
import com.erfangholami.androidsolidservices.ui.startup.StartupViewModel
import kotlinx.serialization.Serializable
import com.erfangholami.androidsolidservices.ui.login.Login as LoginScreen

@Composable
fun ASSAppNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController,
    startAtLogin: Boolean = false,
    onAddAccountComplete: ((webId: String?) -> Unit)? = null,
) {
    NavHost(
        modifier = modifier,
        navController = navController,
        startDestination = if (startAtLogin) Login(isAddingAccount = true) else Startup
    ) {
        composable<Startup> {
            Startup(navController, hiltViewModel<StartupViewModel>())
        }
        composable<Login> {
            LoginScreen(
                navController = navController,
                viewModel = hiltViewModel<LoginViewModel>(),
                onAddAccountComplete = onAddAccountComplete.takeIf { startAtLogin },
            )
        }
        composable<MainPage> {
            MainPage(navController)
        }
    }
}

@Serializable
object Startup

@Serializable
data class Login(val isAddingAccount: Boolean = false)

@Serializable
object MainPage {

    @Serializable
    data class MainNavBottomItem<T : Any>(
        @field:StringRes val title: Int,
        @field:DrawableRes val icon: Int,
        val route: T,
    )

    @Serializable
    object AccessGrants

    @Serializable
    object Main

    @Serializable
    object Setting
}
