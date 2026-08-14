package com.gee.eatapp.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object ShikePalette {
    val Orange = Color(0xFFE07A3F)
    val OnOrange = Color(0xFFFFFFFF)
    val OrangeContainer = Color(0xFFFBE9DD)
    val OnOrangeContainer = Color(0xFF5A2B10)
    val NutritionGreen = Color(0xFF4F8A61)
    val OnNutritionGreen = Color(0xFFFFFFFF)
    val NutritionGreenContainer = Color(0xFFDCEEDD)
    val OnNutritionGreenContainer = Color(0xFF173823)
    val Earth = Color(0xFF8E6847)
    val EarthContainer = Color(0xFFF5E3D2)
    val Ink = Color(0xFF2D2A26)
    val MutedInk = Color(0xFF756F66)
    val Background = Color(0xFFFAF7F2)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceSubtle = Color(0xFFF7F3ED)
    val SurfaceRaised = Color(0xFFFCF9F5)
    val Outline = Color(0xFFD8D1C6)
    val Divider = Color(0xFFEAE4DB)
    val Error = Color(0xFFC75146)
}

val ShikeLightColorScheme = lightColorScheme(
    primary = ShikePalette.Orange,
    onPrimary = ShikePalette.OnOrange,
    primaryContainer = ShikePalette.OrangeContainer,
    onPrimaryContainer = ShikePalette.OnOrangeContainer,
    inversePrimary = Color(0xFFFFB68B),
    secondary = ShikePalette.NutritionGreen,
    onSecondary = ShikePalette.OnNutritionGreen,
    secondaryContainer = ShikePalette.NutritionGreenContainer,
    onSecondaryContainer = ShikePalette.OnNutritionGreenContainer,
    tertiary = ShikePalette.Earth,
    onTertiary = Color.White,
    tertiaryContainer = ShikePalette.EarthContainer,
    onTertiaryContainer = ShikePalette.Ink,
    background = ShikePalette.Background,
    onBackground = ShikePalette.Ink,
    surface = ShikePalette.Surface,
    onSurface = ShikePalette.Ink,
    surfaceVariant = ShikePalette.SurfaceSubtle,
    onSurfaceVariant = ShikePalette.MutedInk,
    surfaceTint = ShikePalette.Orange,
    inverseSurface = ShikePalette.Ink,
    inverseOnSurface = ShikePalette.Background,
    error = ShikePalette.Error,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD5),
    onErrorContainer = Color(0xFF410002),
    outline = ShikePalette.Outline,
    outlineVariant = ShikePalette.Divider,
    scrim = Color.Black,
    surfaceBright = ShikePalette.Surface,
    surfaceDim = Color(0xFFE7E0D8),
    surfaceContainerLowest = ShikePalette.Surface,
    surfaceContainerLow = ShikePalette.SurfaceRaised,
    surfaceContainer = ShikePalette.SurfaceSubtle,
    surfaceContainerHigh = Color(0xFFF1ECE5),
    surfaceContainerHighest = Color(0xFFECE6DE),
)

private val ShikeTypography = Typography(
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        letterSpacing = 1.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
    ),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
    ),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 13.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp),
)

private val ShikeShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
)

object ShikeDimensions {
    val ScreenHorizontal = 16.dp
    val ScreenTop = 12.dp
    val ScreenBottom = 32.dp
    val CardPadding = 20.dp
    val CompactCardPadding = 16.dp
    val SectionGap = 16.dp
    val SmallGap = 8.dp
    val TouchTarget = 48.dp
    val ContentMaxWidth = 960.dp
    val SheetMaxWidth = 560.dp
    val WideBreakpoint = 760.dp
}

@Composable
fun ShikeTheme(
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        dynamicLightColorScheme(LocalContext.current)
    } else {
        ShikeLightColorScheme
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = ShikeTypography,
        shapes = ShikeShapes,
        content = content,
    )
}
