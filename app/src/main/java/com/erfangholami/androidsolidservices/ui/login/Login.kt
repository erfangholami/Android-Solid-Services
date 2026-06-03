package com.erfangholami.androidsolidservices.ui.login

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.erfangholami.androidsolidservices.R
import com.erfangholami.androidsolidservices.ui.navigation.MainPage
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse

@Composable
fun Login(
    navController: NavHostController,
    viewModel: LoginViewModel,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val doAuthenticationInBrowser =
        rememberLauncherForActivityResult(object : ActivityResultContract<Intent, Intent?>() {
            override fun createIntent(context: Context, input: Intent): Intent = input
            override fun parseResult(resultCode: Int, intent: Intent?): Intent? = intent
        }) { intent: Intent? ->
            if (intent != null) {
                val resp: AuthorizationResponse? = AuthorizationResponse.fromIntent(intent)
                val ex: AuthorizationException? = AuthorizationException.fromIntent(intent)
                viewModel.submitAuthorizationResponse(resp, ex)
            }
        }

    LaunchedEffect(Unit) {
        viewModel.onStart()
    }

    LaunchedEffect(Unit) {
        viewModel.eventsFlow.collect { event ->
            when (event) {
                is LoginEvent.LaunchBrowser -> doAuthenticationInBrowser.launch(event.intent)
                LoginEvent.NavigateToMain -> navController.navigate(MainPage) {
                    popUpTo(navController.graph.id) { inclusive = true }
                }

                LoginEvent.NavigateBack -> navController.popBackStack()
            }
        }
    }

    if (uiState.loading) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator()
        }
    } else {
        LoginContent(
            errorMessage = uiState.errorMessage,
            onLoginInrupt = { viewModel.loginWithInruptCom() },
            onLoginSolidCommunity = { viewModel.loginWithSolidCommunity() },
            onLoginCustomUrl = { viewModel.loginWithCustomIssuer(it) },
        )
    }
}

@Composable
private fun LoginContent(
    errorMessage: String?,
    onLoginInrupt: () -> Unit,
    onLoginSolidCommunity: () -> Unit,
    onLoginCustomUrl: (String) -> Unit,
) {
    var customUrl by remember { mutableStateOf("") }
    var customUrlError by remember { mutableStateOf(false) }

    fun submitCustomUrl() {
        val trimmed = customUrl.trim()
        if (trimmed.isEmpty()) {
            customUrlError = true
        } else {
            onLoginCustomUrl(trimmed)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.login_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.login_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onLoginInrupt,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = stringResource(R.string.login_with_inrupt))
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = onLoginSolidCommunity,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = stringResource(R.string.login_with_solidcommunity))
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HorizontalDivider(modifier = Modifier.weight(1f))
            Text(
                text = stringResource(R.string.login_or),
                modifier = Modifier.padding(horizontal = 12.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider(modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = customUrl,
            onValueChange = {
                customUrl = it
                customUrlError = false
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = stringResource(R.string.login_custom_issuer_label)) },
            placeholder = { Text(text = stringResource(R.string.login_custom_issuer_placeholder)) },
            singleLine = true,
            isError = customUrlError,
            supportingText = if (customUrlError) {
                { Text(text = stringResource(R.string.login_custom_issuer_error)) }
            } else if (errorMessage != null) {
                { Text(text = errorMessage, color = MaterialTheme.colorScheme.error) }
            } else null,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Go,
            ),
            keyboardActions = KeyboardActions(onGo = { submitCustomUrl() }),
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = { submitCustomUrl() },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = stringResource(R.string.login_with_custom_issuer))
        }
    }
}
