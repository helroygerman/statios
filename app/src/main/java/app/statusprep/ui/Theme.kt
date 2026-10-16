package app.statusprep.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Palette fixe (vert profond) : contrastes AA vérifiés à l'œil, identiques sur tous les téléphones. */
private val LightColors = lightColorScheme(
    primary = Color(0xFF0B5A4A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC3F2E1),
    onPrimaryContainer = Color(0xFF00201A),
    secondary = Color(0xFF45605A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD7E8E2),
    onSecondaryContainer = Color(0xFF0F201C),
    tertiary = Color(0xFF7A5A00),
    tertiaryContainer = Color(0xFFFFE3A3),
    onTertiaryContainer = Color(0xFF261A00),
    error = Color(0xFFB3261E),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    background = Color(0xFFF6FAF8),
    onBackground = Color(0xFF171D1B),
    surface = Color(0xFFF6FAF8),
    onSurface = Color(0xFF171D1B),
    surfaceVariant = Color(0xFFDAE5E0),
    onSurfaceVariant = Color(0xFF3F4945),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEFF5F2),
    surfaceContainer = Color(0xFFE9F0ED),
    surfaceContainerHigh = Color(0xFFE3EBE7),
    surfaceContainerHighest = Color(0xFFDDE5E1),
    outline = Color(0xFF6F7975),
    outlineVariant = Color(0xFFBFC9C4),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8ED6C0),
    onPrimary = Color(0xFF00382C),
    primaryContainer = Color(0xFF0F4F40),
    onPrimaryContainer = Color(0xFFC3F2E1),
    secondary = Color(0xFFB2CCC4),
    onSecondary = Color(0xFF1D3530),
    secondaryContainer = Color(0xFF2E4540),
    onSecondaryContainer = Color(0xFFD7E8E2),
    tertiary = Color(0xFFEFC24D),
    tertiaryContainer = Color(0xFF4F3B00),
    onTertiaryContainer = Color(0xFFFFE3A3),
    error = Color(0xFFF2B8B5),
    errorContainer = Color(0xFF601410),
    onErrorContainer = Color(0xFFF9DEDC),
    background = Color(0xFF0E1412),
    onBackground = Color(0xFFDEE4E0),
    surface = Color(0xFF0E1412),
    onSurface = Color(0xFFDEE4E0),
    surfaceVariant = Color(0xFF3F4945),
    onSurfaceVariant = Color(0xFFBFC9C4),
    surfaceContainerLowest = Color(0xFF090F0D),
    surfaceContainerLow = Color(0xFF161C1A),
    surfaceContainer = Color(0xFF1A201E),
    surfaceContainerHigh = Color(0xFF242B28),
    surfaceContainerHighest = Color(0xFF2F3633),
    outline = Color(0xFF89938F),
    outlineVariant = Color(0xFF3F4945),
)

private val AppTypography = Typography(
    displaySmall = TextStyle(fontSize = 40.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
)

@Composable
fun StatusPrepTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
