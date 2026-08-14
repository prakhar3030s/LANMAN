package com.example.lanman.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.lanman.ui.theme.ElectricCyan
import com.example.lanman.ui.theme.Black
import com.example.lanman.ui.theme.TextPrimary
import com.example.lanman.ui.theme.TextSecondary
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeChild
import dev.chrisbanes.haze.HazeStyle

@androidx.compose.runtime.Composable
@androidx.annotation.OptIn(dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi::class)
fun AppBottomNavigation(
    navController: NavController,
    hazeState: HazeState
) {
    val items = listOf(
        Screen.ChatList,
        Screen.Contacts,
        Screen.Settings
    )
    val navBackStackEntry = navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry.value?.destination?.route

    if (currentRoute in items.map { it.route }) {
        NavigationBar(
            containerColor = Color.Transparent,
            modifier = Modifier.hazeChild(
                state = hazeState,
                style = dev.chrisbanes.haze.materials.HazeMaterials.thin(Black)
            )
        ) {
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
                    icon = { Icon(icon, contentDescription = label) },
                    label = { Text(label) },
                    selected = currentRoute == screen.route,
                    onClick = {
                        if (currentRoute != screen.route) {
                            navController.navigate(screen.route) {
                                popUpTo(Screen.ChatList.route) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
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
