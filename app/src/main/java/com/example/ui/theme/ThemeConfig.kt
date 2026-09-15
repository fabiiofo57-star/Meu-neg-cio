package com.example.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PointOfSale
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PointOfSale
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

enum class AppIconStyle(
    val title: String,
    val subtitle: String,
    val isPro: Boolean = false
) {
    CLASSICO(
        title = "Clássico",
        subtitle = "Ícones simples e profissionais"
    ),
    ARREDONDADO(
        title = "Arredondado",
        subtitle = "Aparência suave e moderna"
    ),
    SOLIDO(
        title = "Sólido",
        subtitle = "Preenchidos, mais fortes visualmente"
    ),
    MINIMALISTA(
        title = "Minimalista",
        subtitle = "Traços finos e discretos"
    )
}

data class AppCoverPreset(
    val id: String,
    val title: String,
    val brush: Brush,
    val isPro: Boolean = false
)

object AppCoverPresets {
    val presets = listOf(
        AppCoverPreset(
            id = "gradient_emerald",
            title = "Esmeralda Negócios",
            brush = Brush.linearGradient(
                listOf(Color(0xFF064E3B), Color(0xFF0D9488), Color(0xFF14B8A6))
            )
        ),
        AppCoverPreset(
            id = "gradient_ocean",
            title = "Oceano Corporativo",
            brush = Brush.linearGradient(
                listOf(Color(0xFF1E3A8A), Color(0xFF2563EB), Color(0xFF38BDF8))
            )
        ),
        AppCoverPreset(
            id = "gradient_midnight",
            title = "Noite Executiva",
            brush = Brush.linearGradient(
                listOf(Color(0xFF020617), Color(0xFF0F172A), Color(0xFF334155))
            )
        ),
        AppCoverPreset(
            id = "gradient_amber",
            title = "Ouro & Energia",
            brush = Brush.linearGradient(
                listOf(Color(0xFF78350F), Color(0xFFD97706), Color(0xFFFBBF24))
            )
        ),
        AppCoverPreset(
            id = "gradient_rose",
            title = "Rosa Elegance",
            brush = Brush.linearGradient(
                listOf(Color(0xFF881337), Color(0xFFE11D48), Color(0xFFFB7185))
            )
        ),
        AppCoverPreset(
            id = "gradient_slate",
            title = "Minimalista Clean",
            brush = Brush.linearGradient(
                listOf(Color(0xFF1E293B), Color(0xFF475569), Color(0xFF94A3B8))
            )
        )
    )

    fun getPreset(id: String): AppCoverPreset {
        return presets.firstOrNull { it.id == id } ?: presets.first()
    }
}

object AppIconSet {
    fun home(style: AppIconStyle): ImageVector = when (style) {
        AppIconStyle.CLASSICO -> Icons.Default.Home
        AppIconStyle.ARREDONDADO -> Icons.Rounded.Home
        AppIconStyle.SOLIDO -> Icons.Filled.Home
        AppIconStyle.MINIMALISTA -> Icons.Outlined.Home
    }

    fun sales(style: AppIconStyle): ImageVector = when (style) {
        AppIconStyle.CLASSICO -> Icons.Default.PointOfSale
        AppIconStyle.ARREDONDADO -> Icons.Rounded.PointOfSale
        AppIconStyle.SOLIDO -> Icons.Filled.PointOfSale
        AppIconStyle.MINIMALISTA -> Icons.Outlined.PointOfSale
    }

    fun products(style: AppIconStyle): ImageVector = when (style) {
        AppIconStyle.CLASSICO -> Icons.Default.Inventory2
        AppIconStyle.ARREDONDADO -> Icons.Rounded.Inventory2
        AppIconStyle.SOLIDO -> Icons.Filled.Inventory2
        AppIconStyle.MINIMALISTA -> Icons.Outlined.Inventory2
    }

    fun stock(style: AppIconStyle): ImageVector = when (style) {
        AppIconStyle.CLASSICO -> Icons.Default.Widgets
        AppIconStyle.ARREDONDADO -> Icons.Rounded.Widgets
        AppIconStyle.SOLIDO -> Icons.Filled.Widgets
        AppIconStyle.MINIMALISTA -> Icons.Outlined.Widgets
    }

    fun finance(style: AppIconStyle): ImageVector = when (style) {
        AppIconStyle.CLASSICO -> Icons.AutoMirrored.Filled.TrendingUp
        AppIconStyle.ARREDONDADO -> Icons.AutoMirrored.Rounded.TrendingUp
        AppIconStyle.SOLIDO -> Icons.Filled.AccountBalance
        AppIconStyle.MINIMALISTA -> Icons.AutoMirrored.Outlined.TrendingUp
    }

    fun customers(style: AppIconStyle): ImageVector = when (style) {
        AppIconStyle.CLASSICO -> Icons.Default.People
        AppIconStyle.ARREDONDADO -> Icons.Rounded.People
        AppIconStyle.SOLIDO -> Icons.Filled.People
        AppIconStyle.MINIMALISTA -> Icons.Outlined.People
    }

    fun expenses(style: AppIconStyle): ImageVector = when (style) {
        AppIconStyle.CLASSICO -> Icons.Default.Receipt
        AppIconStyle.ARREDONDADO -> Icons.Rounded.Receipt
        AppIconStyle.SOLIDO -> Icons.Filled.Receipt
        AppIconStyle.MINIMALISTA -> Icons.Outlined.Receipt
    }

    fun profile(style: AppIconStyle): ImageVector = when (style) {
        AppIconStyle.CLASSICO -> Icons.Default.Person
        AppIconStyle.ARREDONDADO -> Icons.Rounded.Person
        AppIconStyle.SOLIDO -> Icons.Filled.Person
        AppIconStyle.MINIMALISTA -> Icons.Outlined.Person
    }

    fun settings(style: AppIconStyle): ImageVector = when (style) {
        AppIconStyle.CLASSICO -> Icons.Default.Settings
        AppIconStyle.ARREDONDADO -> Icons.Rounded.Settings
        AppIconStyle.SOLIDO -> Icons.Filled.Settings
        AppIconStyle.MINIMALISTA -> Icons.Outlined.Settings
    }

    fun profit(style: AppIconStyle): ImageVector = when (style) {
        AppIconStyle.CLASSICO -> Icons.AutoMirrored.Filled.TrendingUp
        AppIconStyle.ARREDONDADO -> Icons.AutoMirrored.Rounded.TrendingUp
        AppIconStyle.SOLIDO -> Icons.Filled.AccountBalance
        AppIconStyle.MINIMALISTA -> Icons.AutoMirrored.Outlined.TrendingUp
    }

    fun more(style: AppIconStyle): ImageVector = when (style) {
        AppIconStyle.CLASSICO -> Icons.Default.Menu
        AppIconStyle.ARREDONDADO -> Icons.Rounded.Menu
        AppIconStyle.SOLIDO -> Icons.Filled.Menu
        AppIconStyle.MINIMALISTA -> Icons.Outlined.Menu
    }
}

enum class AppThemeKey(
    val title: String,
    val subtitle: String,
    val previewColor: Color,
    val isPro: Boolean = false // Architecture ready for future PRO themes
) {
    AMARELO(
        title = "Amarelo",
        subtitle = "Alegre & Energético",
        previewColor = Color(0xFFD97706)
    ),
    BRANCO(
        title = "Branco",
        subtitle = "Minimalista & Clean",
        previewColor = Color(0xFF475569)
    ),
    ROSA(
        title = "Rosa",
        subtitle = "Moderno & Elegante",
        previewColor = Color(0xFFE11D48)
    ),
    PRETO(
        title = "Preto",
        subtitle = "Escuro Sofisticado",
        previewColor = Color(0xFF0F172A)
    ),
    AZUL(
        title = "Azul",
        subtitle = "Profissional & Confiável",
        previewColor = Color(0xFF2563EB)
    ),
    VERDE(
        title = "Verde",
        subtitle = "Crescimento & Negócios",
        previewColor = Color(0xFF0D9488)
    )
}

enum class AppearanceMode(val title: String) {
    LIGHT("Claro"),
    DARK("Escuro"),
    SYSTEM("Sistema")
}

@Immutable
data class AppThemeState(
    val themeKey: AppThemeKey = AppThemeKey.VERDE,
    val appearanceMode: AppearanceMode = AppearanceMode.SYSTEM,
    val iconStyle: AppIconStyle = AppIconStyle.ARREDONDADO,
    val coverPhotoUri: String? = null,
    val coverPresetId: String = "gradient_emerald",
    val profilePhotoUri: String? = null
)

object ThemePaletteFactory {

    fun createLightColorScheme(key: AppThemeKey): ColorScheme {
        return when (key) {
            AppThemeKey.AMARELO -> lightColorScheme(
                primary = Color(0xFFD97706),
                onPrimary = Color.White,
                primaryContainer = Color(0xFFFEF3C7),
                onPrimaryContainer = Color(0xFF78350F),
                secondary = Color(0xFF92400E),
                onSecondary = Color.White,
                secondaryContainer = Color(0xFFFDE68A),
                onSecondaryContainer = Color(0xFF451A03),
                background = SlateBackground,
                onBackground = SlateTextPrimary,
                surface = SlateSurface,
                onSurface = SlateTextPrimary,
                surfaceVariant = Color(0xFFF8FAFC),
                onSurfaceVariant = SlateTextSecondary,
                outline = SlateBorder
            )

            AppThemeKey.BRANCO -> lightColorScheme(
                primary = Color(0xFF334155),
                onPrimary = Color.White,
                primaryContainer = Color(0xFFF1F5F9),
                onPrimaryContainer = Color(0xFF0F172A),
                secondary = Color(0xFF475569),
                onSecondary = Color.White,
                secondaryContainer = Color(0xFFE2E8F0),
                onSecondaryContainer = Color(0xFF1E293B),
                background = Color(0xFFFAFAFA),
                onBackground = Color(0xFF18181B),
                surface = Color(0xFFFFFFFF),
                onSurface = Color(0xFF18181B),
                surfaceVariant = Color(0xFFF4F4F5),
                onSurfaceVariant = Color(0xFF71717A),
                outline = Color(0xFFE4E4E7)
            )

            AppThemeKey.ROSA -> lightColorScheme(
                primary = Color(0xFFE11D48),
                onPrimary = Color.White,
                primaryContainer = Color(0xFFFFE4E6),
                onPrimaryContainer = Color(0xFF881337),
                secondary = Color(0xFF9F1239),
                onSecondary = Color.White,
                secondaryContainer = Color(0xFFFECDD3),
                onSecondaryContainer = Color(0xFF4C0519),
                background = SlateBackground,
                onBackground = SlateTextPrimary,
                surface = SlateSurface,
                onSurface = SlateTextPrimary,
                surfaceVariant = Color(0xFFFFF1F2),
                onSurfaceVariant = SlateTextSecondary,
                outline = SlateBorder
            )

            AppThemeKey.PRETO -> lightColorScheme(
                primary = Color(0xFF0F172A),
                onPrimary = Color.White,
                primaryContainer = Color(0xFFE2E8F0),
                onPrimaryContainer = Color(0xFF020617),
                secondary = Color(0xFF334155),
                onSecondary = Color.White,
                secondaryContainer = Color(0xFFCBD5E1),
                onSecondaryContainer = Color(0xFF0F172A),
                background = Color(0xFFF8FAFC),
                onBackground = Color(0xFF020617),
                surface = Color(0xFFFFFFFF),
                onSurface = Color(0xFF020617),
                surfaceVariant = Color(0xFFF1F5F9),
                onSurfaceVariant = Color(0xFF475569),
                outline = Color(0xFFCBD5E1)
            )

            AppThemeKey.AZUL -> lightColorScheme(
                primary = Color(0xFF2563EB),
                onPrimary = Color.White,
                primaryContainer = Color(0xFFDBEAFE),
                onPrimaryContainer = Color(0xFF1E40AF),
                secondary = Color(0xFF1D4ED8),
                onSecondary = Color.White,
                secondaryContainer = Color(0xFFBFDBFE),
                onSecondaryContainer = Color(0xFF172554),
                background = SlateBackground,
                onBackground = SlateTextPrimary,
                surface = SlateSurface,
                onSurface = SlateTextPrimary,
                surfaceVariant = Color(0xFFEFF6FF),
                onSurfaceVariant = SlateTextSecondary,
                outline = SlateBorder
            )

            AppThemeKey.VERDE -> lightColorScheme(
                primary = EmeraldPrimary,
                onPrimary = Color.White,
                primaryContainer = EmeraldContainer,
                onPrimaryContainer = OnEmeraldContainer,
                secondary = SlateNavy,
                onSecondary = Color.White,
                secondaryContainer = Color(0xFFE2E8F0),
                onSecondaryContainer = SlateNavy,
                background = SlateBackground,
                onBackground = SlateTextPrimary,
                surface = SlateSurface,
                onSurface = SlateTextPrimary,
                surfaceVariant = Color(0xFFF1F5F9),
                onSurfaceVariant = SlateTextSecondary,
                outline = SlateBorder
            )
        }
    }

    fun createDarkColorScheme(key: AppThemeKey): ColorScheme {
        return when (key) {
            AppThemeKey.AMARELO -> darkColorScheme(
                primary = Color(0xFFF59E0B),
                onPrimary = Color(0xFF451A03),
                primaryContainer = Color(0xFF78350F),
                onPrimaryContainer = Color(0xFFFEF3C7),
                secondary = Color(0xFFFBBF24),
                onSecondary = Color(0xFF451A03),
                secondaryContainer = Color(0xFF92400E),
                onSecondaryContainer = Color(0xFFFDE68A),
                background = DarkBackground,
                onBackground = DarkTextPrimary,
                surface = DarkSurface,
                onSurface = DarkTextPrimary,
                surfaceVariant = DarkSurfaceElevated,
                onSurfaceVariant = DarkTextSecondary,
                outline = DarkBorder
            )

            AppThemeKey.BRANCO -> darkColorScheme(
                primary = Color(0xFFF8FAFC),
                onPrimary = Color(0xFF0F172A),
                primaryContainer = Color(0xFF334155),
                onPrimaryContainer = Color(0xFFF8FAFC),
                secondary = Color(0xFFE2E8F0),
                onSecondary = Color(0xFF0F172A),
                secondaryContainer = Color(0xFF475569),
                onSecondaryContainer = Color(0xFFF1F5F9),
                background = Color(0xFF121214),
                onBackground = Color(0xFFEDEDED),
                surface = Color(0xFF18181B),
                onSurface = Color(0xFFEDEDED),
                surfaceVariant = Color(0xFF27272A),
                onSurfaceVariant = Color(0xFFA1A1AA),
                outline = Color(0xFF3F3F46)
            )

            AppThemeKey.ROSA -> darkColorScheme(
                primary = Color(0xFFFB7185),
                onPrimary = Color(0xFF4C0519),
                primaryContainer = Color(0xFF881337),
                onPrimaryContainer = Color(0xFFFFE4E6),
                secondary = Color(0xFFF43F5E),
                onSecondary = Color.White,
                secondaryContainer = Color(0xFF9F1239),
                onSecondaryContainer = Color(0xFFFECDD3),
                background = DarkBackground,
                onBackground = DarkTextPrimary,
                surface = DarkSurface,
                onSurface = DarkTextPrimary,
                surfaceVariant = DarkSurfaceElevated,
                onSurfaceVariant = DarkTextSecondary,
                outline = DarkBorder
            )

            AppThemeKey.PRETO -> darkColorScheme(
                primary = Color(0xFFCBD5E1),
                onPrimary = Color(0xFF020617),
                primaryContainer = Color(0xFF1E293B),
                onPrimaryContainer = Color(0xFFF8FAFC),
                secondary = Color(0xFF94A3B8),
                onSecondary = Color(0xFF020617),
                secondaryContainer = Color(0xFF334155),
                onSecondaryContainer = Color(0xFFE2E8F0),
                background = Color(0xFF05080E),
                onBackground = Color(0xFFF8FAFC),
                surface = Color(0xFF0C121D),
                onSurface = Color(0xFFF8FAFC),
                surfaceVariant = Color(0xFF152030),
                onSurfaceVariant = Color(0xFF94A3B8),
                outline = Color(0xFF22344B)
            )

            AppThemeKey.AZUL -> darkColorScheme(
                primary = Color(0xFF60A5FA),
                onPrimary = Color(0xFF172554),
                primaryContainer = Color(0xFF1E40AF),
                onPrimaryContainer = Color(0xFFDBEAFE),
                secondary = Color(0xFF93C5FD),
                onSecondary = Color(0xFF172554),
                secondaryContainer = Color(0xFF1D4ED8),
                onSecondaryContainer = Color(0xFFBFDBFE),
                background = DarkBackground,
                onBackground = DarkTextPrimary,
                surface = DarkSurface,
                onSurface = DarkTextPrimary,
                surfaceVariant = DarkSurfaceElevated,
                onSurfaceVariant = DarkTextSecondary,
                outline = DarkBorder
            )

            AppThemeKey.VERDE -> darkColorScheme(
                primary = EmeraldLight,
                onPrimary = Color(0xFF003833),
                primaryContainer = EmeraldDark,
                onPrimaryContainer = EmeraldContainer,
                secondary = InfoBlue,
                onSecondary = Color.White,
                secondaryContainer = Color(0xFF034A6E),
                onSecondaryContainer = InfoBlueContainer,
                background = DarkBackground,
                onBackground = DarkTextPrimary,
                surface = DarkSurface,
                onSurface = DarkTextPrimary,
                surfaceVariant = DarkSurfaceElevated,
                onSurfaceVariant = DarkTextSecondary,
                outline = DarkBorder
            )
        }
    }
}
