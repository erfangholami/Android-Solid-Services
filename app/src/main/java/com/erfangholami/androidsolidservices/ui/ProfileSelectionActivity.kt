package com.erfangholami.androidsolidservices.ui

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.erfangholami.androidsolidservices.R
import com.erfangholami.androidsolidservices.services.PendingLoginRequests
import com.erfangholami.androidsolidservices.shared.model.profile.SolidAccount
import com.erfangholami.androidsolidservices.ui.theme.ASSAppTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ProfileSelectionActivity : ComponentActivity() {

    companion object {
        const val EXTRA_REQUEST_ID = "request_id"
    }

    private val viewModel: ProfileSelectionViewModel by viewModels()

    @Inject
    lateinit var pendingLoginRequests: PendingLoginRequests

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val requestId = intent.getStringExtra(EXTRA_REQUEST_ID)
        val request = requestId?.let { pendingLoginRequests.get(it) }

        if (request == null) {
            finish()
            return
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                request.callback.onResult(false, "")
                pendingLoginRequests.remove(requestId)
                finish()
            }
        })

        val callerIcon: Bitmap? = try {
            packageManager.getApplicationIcon(request.callerPackage)
                .toBitmap(config = Bitmap.Config.ARGB_8888)
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }

        setContent {
            ASSAppTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()

                ProfileSelectionScreen(
                    callerName = request.callerName,
                    callerIcon = callerIcon,
                    profiles = uiState.profiles,
                    onProfileSelected = { selectedWebId ->
                        viewModel.grant(request.callerPackage, request.callerName, selectedWebId)
                        request.callback.onResult(true, selectedWebId)
                        pendingLoginRequests.remove(requestId)
                        finish()
                    },
                    onDismiss = {
                        request.callback.onResult(false, "")
                        pendingLoginRequests.remove(requestId)
                        finish()
                    }
                )
            }
        }
    }

}

@Composable
private fun ProfileSelectionScreen(
    callerName: String,
    callerIcon: Bitmap?,
    profiles: List<SolidAccount>,
    onProfileSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clickable(enabled = false, onClick = {}),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (callerIcon != null) {
                    Image(
                        bitmap = callerIcon.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                    Spacer(Modifier.height(12.dp))
                }

                Text(
                    text = callerName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.wants_to_access_your_solid_pod),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(20.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.choose_an_account),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Start),
                )

                Spacer(Modifier.height(8.dp))

                if (profiles.isEmpty()) {
                    Text(
                        text = stringResource(R.string.no_accounts_logged_in),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(profiles) { profile ->
                            val webId = profile.userInfo?.webId ?: return@items
                            ProfileItem(webId = webId, onClick = { onProfileSelected(webId) })
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))

                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.cancel)
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileItem(webId: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_main),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = webId,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
