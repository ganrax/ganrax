package com.example.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
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
import com.example.ui.screens.ai.AiAdvisorScreen
import com.example.ui.screens.calculator.StakeCalculatorScreen
import com.example.ui.screens.matches.MatchTrackerScreen
import com.example.ui.screens.strategy.StrategyPlanScreen
import com.example.ui.screens.telegram.TelegramAlertParserScreen
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
    TELEGRAM("Telegram", Icons.AutoMirrored.Filled.Send, "nav_telegram"),
    AI_ADVISOR("AI Elemző", Icons.Default.AutoAwesome, "nav_ai_advisor"),
    UPDATER("Frissítő", Icons.Default.SystemUpdate, "nav_updater")
}

@Composable
fun MainNavigation(app: BettingApp) {
    var currentTab by remember { mutableStateOf(NavigationTab.CALCULATOR) }

    val strategyViewModel: StrategyViewModel = viewModel(factory = StrategyViewModel.Factory(app.repository))
    val calculatorViewModel: CalculatorViewModel = viewModel(factory = CalculatorViewModel.Factory(app.repository))
    val matchTrackerViewModel: MatchTrackerViewModel = viewModel(factory = MatchTrackerViewModel.Factory(app.repository))
    val telegramParserViewModel: TelegramParserViewModel = viewModel(factory = TelegramParserViewModel.Factory(app.repository))
    val aiAdvisorViewModel: AiAdvisorViewModel = viewModel()
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
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1
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

                NavigationTab.TELEGRAM -> {
                    TelegramAlertParserScreen(
                        viewModel = telegramParserViewModel,
                        onNavigateToMatches = {
                            currentTab = NavigationTab.MATCHES
                        },
                        onNavigateToCalculator = {
                            currentTab = NavigationTab.CALCULATOR
                        }
                    )
                }

                NavigationTab.AI_ADVISOR -> {
                    AiAdvisorScreen(
                        viewModel = aiAdvisorViewModel
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
