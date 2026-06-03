package com.erfangholami.androidsolidservices.ui.elements

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.erfangholami.androidsolidservices.R
import com.erfangholami.androidsolidservices.domain.model.GrantedApp

@Composable
fun RevokePermissionDialog(
    app: GrantedApp,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onConfirm) {
                Text(
                    text = stringResource(R.string.revoke)
                )
            }
        },
        dismissButton = {
            Button(onDismiss) {
                Text(
                    text = stringResource(R.string.cancel)
                )
            }
        },
        title = {
            Text(
                text = stringResource(R.string.revoke_permission)
            )
        },
        text = {
            Text(
                text = stringResource(R.string.revoke_permission_description, app.name, app.webId)
            )
        }
    )
}