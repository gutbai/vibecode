package com.vibecode.mobile.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors=darkColorScheme(
    background=Color(0xFF090D16),surface=Color(0xFF111827),surfaceVariant=Color(0xFF182033),
    primary=Color(0xFF9B5CFF),secondary=Color(0xFF45A3FF),tertiary=Color(0xFF35D07F),
    onBackground=Color(0xFFF1F5F9),onSurface=Color(0xFFF1F5F9)
)
@Composable fun VibeTheme(content:@Composable()->Unit){MaterialTheme(colorScheme=DarkColors,typography=Typography(),content=content)}
