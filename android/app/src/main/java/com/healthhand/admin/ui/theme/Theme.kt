package com.healthhand.admin.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Color(0xFF176B5B), onPrimary = Color.White,
    primaryContainer = Color(0xFFD5F3EA), onPrimaryContainer = Color(0xFF073D33),
    secondary = Color(0xFFA4653C), background = Color(0xFFF8FAF7), surface = Color.White,
    onSurface = Color(0xFF18201D), onSurfaceVariant = Color(0xFF53605B),
    outline = Color(0xFFBAC7C1), error = Color(0xFFB3261E)
)

@Composable
fun HealthHandTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = Typography(),
        shapes = Shapes(
            extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(26.dp)
        ),
        content = content
    )
}
