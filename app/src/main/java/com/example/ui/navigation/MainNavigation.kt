package com.example.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.BettingApp
import com.example.ui.screens.calculator.StakeCalculatorScreen
import com.example.ui.screens.matches.MatchTrackerScreen
import com.example.ui.screens.strategy.StrategyPlanScreen
import com.example.ui.screens.updater.InAppUpdateScreen
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.viewmodel.*

enum class NavigationTab(
    val title: String,
    val icon: ImageVector,
    val testTag: String
) {
    STRATEGY("Stratégia", Icons.AutoMirrored.Filled.TrendingUp, "nav_strategy"),
    CALCULATOR("Kalkulátor", Icons.Default.Calculate, "nav_calculator"),
    MATCHES("Meccsek", Icons.Default.SportsSoccer, "nav_matches"),
    UPDATER("Frissítő", Icons.Default.SystemUpdate, "nav_updater")
}

@Composable
fun MainNavigation(app: BettingApp) {
    var currentTab by remember { mutableStateOf(NavigationTab.CALCULATOR) }

    val strategyViewModel: StrategyViewModel = viewModel(factory = StrategyViewModel.Factory(app.repository))
    val calculatorViewModel: CalculatorViewModel = viewModel(factory = CalculatorViewModel.Factory(app.repository))
    val matchTrackerViewModel: MatchTrackerViewModel = viewModel(factory = MatchTrackerViewModel.Factory(app.repository))
    val updateViewModel: UpdateViewModel = viewModel(factory = UpdateViewModel.Factory(app))

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                NavigationTab.entries.forEach { tab ->
                    val selected = currentTab == tab
                    NavigationBarItem(
                        selected = selected,
                        onClick = { currentTab = tab },
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.title
                            )
                        },
                        label = {
                            Text(
                                text = tab.title,
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedTextColor = EmeraldPrimary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.testTag(tab.testTag)
                    )
                }
            }
        }
    ) { innerPadding ->
        Surface(
            modifier = Modifier.padding(innerPadding),
            color = MaterialTheme.colorScheme.background
        ) {
            when (currentTab) {
                NavigationTab.STRATEGY -> {
                    StrategyPlanScreen(
                        viewModel = strategyViewModel,
                        onNavigateToCalculator = { bank, base ->
                            calculatorViewModel.setBankroll(bank.toInt().toString())
                            calculatorViewModel.setBaseStake(base.toInt().toString())
                            currentTab = NavigationTab.CALCULATOR
                        }
                    )
                }

                NavigationTab.CALCULATOR -> {
                    StakeCalculatorScreen(
                        viewModel = calculatorViewModel,
                        onMatchCreated = {
                            currentTab = NavigationTab.MATCHES
                        }
                    )
                }

                NavigationTab.MATCHES -> {
                    MatchTrackerScreen(
                        viewModel = matchTrackerViewModel,
                        onNavigateToCalculator = {
                            currentTab = NavigationTab.CALCULATOR
                        }
                    )
                }

                NavigationTab.UPDATER -> {
                    InAppUpdateScreen(
                        viewModel = updateViewModel
                    )
                }
            }
        }
    }
}
