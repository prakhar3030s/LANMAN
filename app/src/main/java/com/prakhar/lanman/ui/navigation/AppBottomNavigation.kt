package com.prakhar.lanman.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.prakhar.lanman.ui.common.AppIcons
import com.prakhar.lanman.ui.theme.LanmanTheme

@Composable
fun AppBottomNavigation(
    navController: NavController,
    unreadCount: Int = 0
) {
    val items = listOf(
        Screen.ChatList,
        Screen.Contacts,
        Screen.Settings
    )
    val navBackStackEntry = navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry.value?.destination?.route
    val colors = LanmanTheme.colors

    if (currentRoute in items.map { it.route }) {
        Column {
            HorizontalDivider(thickness = 1.dp, color = colors.border)
            NavigationBar(containerColor = colors.surfaceElevated) {
                items.forEach { screen ->
                    val icon = when (screen) {
                        Screen.ChatList -> AppIcons.Chat
                        Screen.Contacts -> Icons.Default.Person
                        Screen.Settings -> Icons.Default.Settings
                        else -> AppIcons.Chat
                    }
                    val label = when (screen) {
                        Screen.ChatList -> "Chats"
                        Screen.Contacts -> "Contacts"
                        Screen.Settings -> "Settings"
                        else -> ""
                    }

                    NavigationBarItem(
                        icon = {
                            if (screen == Screen.ChatList && unreadCount > 0) {
                                BadgedBox(
                                    badge = {
                                        Badge(containerColor = colors.accent, contentColor = colors.onAccent) {
                                            Text(
                                                text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                ) {
                                    Icon(icon, contentDescription = label)
                                }
                            } else {
                                Icon(icon, contentDescription = label)
                            }
                        },
                        label = { Text(label) },
                        selected = currentRoute == screen.route,
                        onClick = {
                            if (currentRoute != screen.route) {
                                navController.navigate(screen.route) {
                                    popUpTo(Screen.ChatList.route) { inclusive = false }
                                    launchSingleTop = true
                                }
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = colors.onAccent,
                            selectedTextColor = colors.accent,
                            indicatorColor = colors.accent,
                            unselectedIconColor = colors.textSecondary,
                            unselectedTextColor = colors.textSecondary
                        )
                    )
                }
            }
        }
    }
}
