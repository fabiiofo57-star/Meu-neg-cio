package com.example.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.ui.theme.AppIconSet
import com.example.ui.theme.AppIconStyle

sealed class Screen(val route: String) {
    data object Onboarding : Screen("onboarding")
    data object Login : Screen("login")
    data object BusinessSetup : Screen("business_setup")
    data object Main : Screen("main")
}

enum class BottomNavItem(
    val title: String,
    val defaultIcon: ImageVector,
    val testTag: String
) {
    INICIO("Início", Icons.Default.Home, "nav_inicio"),
    VENDAS("Vendas", Icons.Default.PointOfSale, "nav_vendas"),
    PRODUTOS("Produtos", Icons.Default.Inventory2, "nav_produtos"),
    FINANCEIRO("Financeiro", Icons.AutoMirrored.Filled.TrendingUp, "nav_financeiro"),
    MAIS("Mais", Icons.Default.Menu, "nav_mais");

    val icon: ImageVector get() = defaultIcon

    fun getIcon(style: AppIconStyle): ImageVector = when (this) {
        INICIO -> AppIconSet.home(style)
        VENDAS -> AppIconSet.sales(style)
        PRODUTOS -> AppIconSet.products(style)
        FINANCEIRO -> AppIconSet.finance(style)
        MAIS -> AppIconSet.more(style)
    }
}
