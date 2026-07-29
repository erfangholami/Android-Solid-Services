package com.erfangholami.androidsolidservices.client.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.erfangholami.androidsolidservices.client.R

/**
 * A ready-made "Sign in with Solid" button styled after Solid's branding.
 *
 * Wire [onClick] to start the sign-in flow, typically via
 * [SolidSignInClient.requestLogin]:
 * ```kotlin
 * SignInButton(onClick = { Solid.getSignInClient(context).requestLogin { webId, _ -> ... } })
 * ```
 *
 * @param onClick Invoked when the button is tapped.
 * @param enabled When `false`, the button is dimmed and non-interactive.
 * @param text The button label; defaults to the localized "Sign in with Solid".
 */
@Composable
public fun SignInButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    text: String = stringResource(R.string.ass_sign_in_with_solid),
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    Surface(
        onClick = onClick,
        modifier = modifier
            .height(48.dp)
            .alpha(if (enabled) 1f else 0.4f),
        enabled = enabled,
        shape = RoundedCornerShape(24.dp),
        color = Color.White,
        border = BorderStroke(
            width = 1.dp,
            color = if (isPressed) Color(0xFF006B6B) else Color(0xFFDADCE0),
        ),
        shadowElevation = if (isPressed) 0.dp else 2.dp,
        interactionSource = interactionSource,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 20.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.ass_ic_solid_logo),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = text,
                style = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF3C4043),
                    letterSpacing = 0.25.sp,
                ),
            )
        }
    }
}
