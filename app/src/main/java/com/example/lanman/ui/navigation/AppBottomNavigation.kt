package com.example.lanman.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.lanman.ui.theme.ElectricCyan
import com.example.lanman.ui.theme.Black
import com.example.lanman.ui.theme.TextSecondary

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

    if (currentRoute in items.map { it.route }) {
        NavigationBar(containerColor = Color.Black) {
            items.forEach { screen ->
                val icon = when (screen) {
                    Screen.ChatList -> Icons.Default.Email
                    Screen.Contacts -> Icons.Default.Person
                    Screen.Settings -> Icons.Default.Settings
                    else -> Icons.Default.Email
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
                                    Badge(containerColor = ElectricCyan, contentColor = Black) {
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
                        selectedIconColor = Black,
                        selectedTextColor = ElectricCyan,
                        indicatorColor = ElectricCyan,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary
                    )
                )
            }
        }
    }
}
