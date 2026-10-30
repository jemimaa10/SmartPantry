package com.example.mts.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Cream = Color(0xFFFBF6EE)
val CreamDeep = Color(0xFFF3EADB)
val Leaf = Color(0xFF2F7D4F)
val LeafDark = Color(0xFF1F5A38)
val LeafSoft = Color(0xFFDDEEDF)
val Carrot = Color(0xFFE8792F)
val CarrotSoft = Color(0xFFFCE6D4)
val Ink = Color(0xFF26231E)
val InkSoft = Color(0xFF6F685D)
val Tomato = Color(0xFFC93C2C)
val TomatoSoft = Color(0xFFF9DFDA)
val Honey = Color(0xFFB9820F)
val HoneySoft = Color(0xFFFBEFCB)

private val Scheme = lightColorScheme(
    primary = Leaf,
    onPrimary = Color.White,
    primaryContainer = LeafSoft,
    onPrimaryContainer = LeafDark,
    secondary = Carrot,
    onSecondary = Color.White,
    secondaryContainer = CarrotSoft,
    onSecondaryContainer = Color(0xFF7A3A0E),
    tertiary = Honey,
    background = Cream,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = CreamDeep,
    onSurfaceVariant = InkSoft,
    surfaceContainer = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = CreamDeep,
    outline = Color(0xFFD9CFBF),
    outlineVariant = Color(0xFFE9E0D2),
    error = Tomato,
)

private val Display = FontFamily.Serif

private val AppTypography = Typography(
    displaySmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 38.sp),
    headlineMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp),
    headlineSmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun SmartPantryTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = AppTypography, shapes = AppShapes, content = content)
}
