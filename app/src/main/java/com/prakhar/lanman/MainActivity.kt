package com.prakhar.lanman

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navDeepLink
import com.prakhar.lanman.data.engine.RadarService
import com.prakhar.lanman.ui.chat.ChatBadgeViewModel
import com.prakhar.lanman.ui.chat.ChatListScreen
import com.prakhar.lanman.ui.chat.ConversationScreen
import com.prakhar.lanman.ui.chat.MessageRequestsScreen
import com.prakhar.lanman.ui.contacts.ContactDetailScreen
import com.prakhar.lanman.ui.contacts.ContactListScreen
import com.prakhar.lanman.ui.navigation.AppBottomNavigation
import com.prakhar.lanman.ui.navigation.Screen
import com.prakhar.lanman.ui.onboarding.OnboardingScreen
import com.prakhar.lanman.ui.qr.MyQrCodeScreen
import com.prakhar.lanman.ui.qr.ScanContactScreen
import com.prakhar.lanman.ui.settings.BlockedContactsScreen
import com.prakhar.lanman.ui.settings.SettingsScreen
import com.prakhar.lanman.ui.startup.StartupState
import com.prakhar.lanman.ui.startup.StartupViewModel
import com.prakhar.lanman.ui.theme.LANMANTheme
import com.prakhar.lanman.ui.theme.LanmanTheme
import com.prakhar.lanman.ui.theme.MotionDurationShort
import com.prakhar.lanman.util.AppLogger
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var appLogger: AppLogger

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        appLogger.log("MainActivity onCreate")
        enableEdgeToEdge()
        setContent {
            val startupViewModel: StartupViewModel = hiltViewModel()
            val startupState by startupViewModel.state.collectAsStateWithLifecycle()
            val themeMode by startupViewModel.themeMode.collectAsStateWithLifecycle()

            LANMANTheme(themeMode = themeMode) {
                val colors = LanmanTheme.colors
                when (val state = startupState) {
                    is StartupState.Loading -> {
                        Scaffold(
                            modifier = Modifier.fillMaxSize(),
                            containerColor = colors.background
                        ) { innerPadding ->
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = colors.accent)
                            }
                        }
                    }

                    is StartupState.NeedsOnboarding, is StartupState.Ready -> {
                        val navController = rememberNavController()
                        val startDestination = if (state is StartupState.NeedsOnboarding) {
                            Screen.Onboarding.route
                        } else {
                            Screen.ChatList.route
                        }

                        val badgeViewModel: ChatBadgeViewModel = hiltViewModel()
                        val unreadTotal by badgeViewModel.unreadTotal.collectAsStateWithLifecycle()

                        val currentBackStack by navController.currentBackStackEntryAsState()
                        val currentRoute = currentBackStack?.destination?.route

                        val notificationPermissionLauncher = rememberLauncherForActivityResult(
                            ActivityResultContracts.RequestPermission()
                        ) { _ ->
                            if (startupViewModel.isDiscoveryEnabled) {
                                runCatching {
                                    ContextCompat.startForegroundService(
                                        this,
                                        Intent(this, RadarService::class.java)
                                    )
                                }
                            }
                        }

                        // Start RadarService once past onboarding without re-triggering on every recomposition.
                        LaunchedEffect(currentRoute) {
                            if (currentRoute != null &&
                                currentRoute != Screen.Onboarding.route &&
                                startupViewModel.isDiscoveryEnabled
                            ) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    ContextCompat.checkSelfPermission(
                                        this@MainActivity,
                                        Manifest.permission.POST_NOTIFICATIONS
                                    ) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    runCatching {
                                        ContextCompat.startForegroundService(
                                            this@MainActivity,
                                            Intent(this@MainActivity, RadarService::class.java)
                                        )
                                    }
                                }
                            }
                        }

                        Scaffold(
                            modifier = Modifier.fillMaxSize(),
                            containerColor = colors.background,
                            bottomBar = {
                                AppBottomNavigation(
                                    navController = navController,
                                    unreadCount = unreadTotal
                                )
                            }
                        ) { innerPadding ->
                            NavHost(
                                navController = navController,
                                startDestination = startDestination,
                                modifier = Modifier.padding(innerPadding),
                                enterTransition = { fadeIn(animationSpec = tween(MotionDurationShort)) },
                                exitTransition = { fadeOut(animationSpec = tween(MotionDurationShort)) }
                            ) {
                                composable(Screen.Onboarding.route) {
                                    OnboardingScreen(
                                        onNavigateToHome = {
                                            navController.navigate(Screen.ChatList.route) {
                                                popUpTo(Screen.Onboarding.route) { inclusive = true }
                                            }
                                        }
                                    )
                                }

                                composable(
                                    route = Screen.MessageRequests.route,
                                    deepLinks = listOf(navDeepLink { uriPattern = "lanman://requests" })
                                ) {
                                    MessageRequestsScreen(
                                        onNavigateBack = { navController.popBackStack() }
                                    )
                                }

                                composable(Screen.ChatList.route) {
                                    ChatListScreen(
                                        onNavigateToMyQr = { navController.navigate(Screen.MyQrCode.route) },
                                        onNavigateToScanQr = { navController.navigate(Screen.AddContact.route) },
                                        onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                                        onNavigateToConversation = { contactId ->
                                            navController.navigate(Screen.Conversation.createRoute(contactId))
                                        },
                                        onNavigateToMessageRequests = {
                                            navController.navigate(Screen.MessageRequests.route)
                                        },
                                        onNavigateToNewChat = {
                                            navController.navigate(Screen.Contacts.route) {
                                                launchSingleTop = true
                                            }
                                        }
                                    )
                                }

                                composable(
                                    route = Screen.Conversation.route,
                                    deepLinks = listOf(navDeepLink { uriPattern = "lanman://conversation/{contactId}" })
                                ) {
                                    ConversationScreen(
                                        onNavigateBack = { navController.popBackStack() },
                                        onNavigateToContactDetail = { contactId ->
                                            navController.navigate(Screen.ContactDetail.createRoute(contactId))
                                        }
                                    )
                                }

                                composable(Screen.MyQrCode.route) {
                                    MyQrCodeScreen(
                                        onNavigateBack = { navController.popBackStack() },
                                        onNavigateToScanner = {
                                            navController.navigate(Screen.AddContact.route) {
                                                launchSingleTop = true
                                            }
                                        }
                                    )
                                }

                                composable(Screen.AddContact.route) {
                                    ScanContactScreen(
                                        onNavigateBack = { navController.popBackStack() },
                                        onNavigateToMyQr = {
                                            navController.navigate(Screen.MyQrCode.route) {
                                                launchSingleTop = true
                                            }
                                        }
                                    )
                                }

                                composable(Screen.Contacts.route) {
                                    ContactListScreen(
                                        onNavigateToConversation = { contactId ->
                                            navController.navigate(Screen.Conversation.createRoute(contactId))
                                        },
                                        onNavigateToContactDetail = { contactId ->
                                            navController.navigate(Screen.ContactDetail.createRoute(contactId))
                                        },
                                        onNavigateToMyQr = { navController.navigate(Screen.MyQrCode.route) },
                                        onNavigateToAddContact = { navController.navigate(Screen.AddContact.route) }
                                    )
                                }

                                composable(Screen.Settings.route) {
                                    SettingsScreen(
                                        onNavigateToMyQr = { navController.navigate(Screen.MyQrCode.route) },
                                        onNavigateToOnboarding = {
                                            navController.navigate(Screen.Onboarding.route) {
                                                popUpTo(0) { inclusive = true }
                                            }
                                        },
                                        onNavigateToBlockedContacts = {
                                            navController.navigate(Screen.BlockedContacts.route)
                                        }
                                    )
                                }

                                composable(Screen.BlockedContacts.route) {
                                    BlockedContactsScreen(
                                        onNavigateBack = { navController.popBackStack() }
                                    )
                                }

                                composable(Screen.ContactDetail.route) { backStackEntry ->
                                    val contactId = backStackEntry.arguments?.getString("contactId")
                                        ?: return@composable
                                    ContactDetailScreen(
                                        contactId = contactId,
                                        onNavigateBack = { navController.popBackStack() },
                                        onNavigateToConversation = { id ->
                                            navController.navigate(Screen.Conversation.createRoute(id)) {
                                                launchSingleTop = true
                                            }
                                        },
                                        onContactRemoved = {
                                            if (!navController.popBackStack(Screen.Conversation.route, inclusive = true)) {
                                                navController.popBackStack()
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
