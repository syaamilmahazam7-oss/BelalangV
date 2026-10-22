package my.belalang.booking.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** Palet gelap bergaya V4: latar gelap, sempadan neon cyan ke ungu. */
object Neon {
    val Bg = Color(0xFF0A0E1A)
    val Card = Color(0xFF121A2B)
    val Cyan = Color(0xFF00E5FF)
    val Purple = Color(0xFFB04DFF)
    val Ok = Color(0xFF2EE6A6)
    val Warn = Color(0xFFFFC857)
    val Bad = Color(0xFFFF6B7A)
    val Text = Color(0xFFE8EEF9)
    val Muted = Color(0xFF8FA0BC)
    val Gradient: Brush = Brush.linearGradient(listOf(Cyan, Purple))
}

private val NeonColors = darkColorScheme(
    primary = Neon.Cyan,
    onPrimary = Neon.Bg,
    secondary = Neon.Purple,
    onSecondary = Neon.Bg,
    background = Neon.Bg,
    onBackground = Neon.Text,
    surface = Neon.Card,
    onSurface = Neon.Text,
    surfaceVariant = Neon.Card,
    onSurfaceVariant = Neon.Muted,
    error = Neon.Bad,
    outline = Neon.Muted,
)

@Composable
fun BelalangTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = NeonColors, content = content)
}
