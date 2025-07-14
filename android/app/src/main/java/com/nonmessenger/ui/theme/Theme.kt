package com.nonmessenger.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// NonMessenger Color Scheme - Security-focused dark theme
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF00E676), // Bright green for security/encryption
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF00C853),
    onPrimaryContainer = Color(0xFF000000),
    
    secondary = Color(0xFF03DAC6), // Cyan accent
    onSecondary = Color(0xFF000000),
    secondaryContainer = Color(0xFF018786),
    onSecondaryContainer = Color(0xFFFFFFFF),
    
    tertiary = Color(0xFFBB86FC), // Purple for special actions
    onTertiary = Color(0xFF000000),
    tertiaryContainer = Color(0xFF6200EE),
    onTertiaryContainer = Color(0xFFFFFFFF),
    
    error = Color(0xFFCF6679),
    onError = Color(0xFF000000),
    errorContainer = Color(0xFFB00020),
    onErrorContainer = Color(0xFFFFFFFF),
    
    background = Color(0xFF121212), // Dark background
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF1E1E1E),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF2D2D2D),
    onSurfaceVariant = Color(0xFFE0E0E0),
    
    outline = Color(0xFF404040),
    outlineVariant = Color(0xFF606060)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF00C853), // Green for security
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE8F5E8),
    onPrimaryContainer = Color(0xFF1B5E20),
    
    secondary = Color(0xFF018786), // Teal
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE0F2F1),
    onSecondaryContainer = Color(0xFF004D40),
    
    tertiary = Color(0xFF6200EE), // Purple
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFEDE7F6),
    onTertiaryContainer = Color(0xFF311B92),
    
    error = Color(0xFFB00020),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    
    background = Color(0xFFFFFBFE),
    onBackground = Color(0xFF1C1B1F),
    surface = Color(0xFFFFFBFE),
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFE7E0EC),
    onSurfaceVariant = Color(0xFF49454F),
    
    outline = Color(0xFF79747E),
    outlineVariant = Color(0xFFCAC4D0)
)

@Composable
fun NonMessengerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false, // Disabled for consistent branding
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

// Security-themed colors for specific UI elements
object NonMessengerColors {
    val EncryptedMessage = Color(0xFF1B5E20) // Dark green for encrypted content
    val UnencryptedMessage = Color(0xFFD32F2F) // Red for unencrypted (warning)
    val VerifiedContact = Color(0xFF2E7D32) // Green for verified contacts
    val UnverifiedContact = Color(0xFFFF9800) // Orange for unverified
    val OnlineStatus = Color(0xFF4CAF50) // Green for online
    val OfflineStatus = Color(0xFF9E9E9E) // Gray for offline
    val AwayStatus = Color(0xFFFF9800) // Orange for away
    val BusyStatus = Color(0xFFF44336) // Red for busy
    
    // Message delivery status colors
    val MessageSending = Color(0xFF9E9E9E) // Gray
    val MessageSent = Color(0xFF2196F3) // Blue
    val MessageDelivered = Color(0xFF4CAF50) // Green
    val MessageRead = Color(0xFF00E676) // Bright green
    val MessageFailed = Color(0xFFF44336) // Red
}

// Extension functions for theme-aware colors
@Composable
fun getContactStatusColor(status: String): Color {
    return when (status.lowercase()) {
        "online" -> NonMessengerColors.OnlineStatus
        "offline" -> NonMessengerColors.OfflineStatus
        "away" -> NonMessengerColors.AwayStatus
        "busy" -> NonMessengerColors.BusyStatus
        else -> NonMessengerColors.OfflineStatus
    }
}

@Composable
fun getMessageStatusColor(status: String): Color {
    return when (status.lowercase()) {
        "sending" -> NonMessengerColors.MessageSending
        "sent" -> NonMessengerColors.MessageSent
        "delivered" -> NonMessengerColors.MessageDelivered
        "read" -> NonMessengerColors.MessageRead
        "failed" -> NonMessengerColors.MessageFailed
        else -> NonMessengerColors.MessageSending
    }
}

@Composable
fun getVerificationColor(isVerified: Boolean): Color {
    return if (isVerified) {
        NonMessengerColors.VerifiedContact
    } else {
        NonMessengerColors.UnverifiedContact
    }
}
