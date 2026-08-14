package com.example.lanman

import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.lanman.ui.navigation.Screen
import com.example.lanman.ui.onboarding.OnboardingScreen
import com.example.lanman.ui.startup.StartupState
import com.example.lanman.ui.startup.StartupViewModel
import com.example.lanman.ui.theme.LANMANTheme
import com.example.lanman.util.AppLogger
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
            LANMANTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val startupViewModel: StartupViewModel = hiltViewModel()
                    val startupState by startupViewModel.state.collectAsStateWithLifecycle()

                    when (val state = startupState) {
                        is StartupState.Loading -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                        is StartupState.NeedsOnboarding, is StartupState.Ready -> {
                            val navController = rememberNavController()
                            val startDestination = if (state is StartupState.NeedsOnboarding) {
                                Screen.Onboarding.route
                            } else {
                                Screen.ChatList.route
                            }
                            
                            val hazeState = androidx.compose.runtime.remember { dev.chrisbanes.haze.HazeState() }

                            Scaffold(
                                modifier = Modifier.fillMaxSize(),
                                containerColor = com.example.lanman.ui.theme.Black,
                                bottomBar = {
                                    com.example.lanman.ui.navigation.AppBottomNavigation(
                                        navController = navController,
                                        hazeState = hazeState
                                    )
                                }
                            ) { innerPadding ->
                                NavHost(
                                    navController = navController,
                                    startDestination = startDestination,
                                    modifier = Modifier.padding(innerPadding)
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
                                    
                                    composable(Screen.MessageRequests.route) {
                                        com.example.lanman.ui.chat.MessageRequestsScreen(
                                            onNavigateBack = { navController.popBackStack() }
                                        )
                                    }
                                    
                                    composable(Screen.ChatList.route) {
                                        val context = androidx.compose.ui.platform.LocalContext.current
                                        val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                                            androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
                                        ) { isGranted ->
                                            // Regardless of permission, start the service.
                                            androidx.core.content.ContextCompat.startForegroundService(
                                                context,
                                                android.content.Intent(context, com.example.lanman.data.engine.RadarService::class.java)
                                            )
                                        }

                                        androidx.compose.runtime.LaunchedEffect(Unit) {
                                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                                if (androidx.core.content.ContextCompat.checkSelfPermission(
                                                        context,
                                                        android.Manifest.permission.POST_NOTIFICATIONS
                                                    ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                                                ) {
                                                    permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                                } else {
                                                    androidx.core.content.ContextCompat.startForegroundService(
                                                        context,
                                                        android.content.Intent(context, com.example.lanman.data.engine.RadarService::class.java)
                                                    )
                                                }
                                            } else {
                                                androidx.core.content.ContextCompat.startForegroundService(
                                                    context,
                                                    android.content.Intent(context, com.example.lanman.data.engine.RadarService::class.java)
                                                )
                                            }
                                        }
                                        
                                        com.example.lanman.ui.chat.ChatListScreen(
                                            onNavigateToQr = { navController.navigate(Screen.MyQrCode.route) },
                                            onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                                            onNavigateToConversation = { contactId ->
                                                navController.navigate(Screen.Conversation.createRoute(contactId))
                                            },
                                            onNavigateToMessageRequests = {
                                                navController.navigate(Screen.MessageRequests.route)
                                            },
                                            hazeState = hazeState
                                        )
                                    }

                                    composable(
                                        route = Screen.Conversation.route,
                                        deepLinks = listOf(androidx.navigation.navDeepLink { uriPattern = "lanman://conversation/{contactId}" })
                                    ) {
                                        com.example.lanman.ui.chat.ConversationScreen(
                                            onNavigateBack = { navController.popBackStack() }
                                        )
                                    }

                                    composable(Screen.MyQrCode.route) {
                                        com.example.lanman.ui.qr.MyQrCodeScreen()
                                    }
                                    composable(Screen.AddContact.route) {
                                        com.example.lanman.ui.qr.ScanContactScreen(
                                            onNavigateBack = { navController.popBackStack() }
                                        )
                                    }
                                    composable(Screen.Contacts.route) {
                                        com.example.lanman.ui.contacts.ContactListScreen(
                                            onNavigateToConversation = { contactId ->
                                                navController.navigate(Screen.Conversation.createRoute(contactId))
                                            },
                                            onNavigateToContactDetail = { contactId ->
                                                navController.navigate(Screen.ContactDetail.createRoute(contactId))
                                            },
                                            onNavigateToMyQr = { navController.navigate(Screen.MyQrCode.route) },
                                            onNavigateToAddContact = { navController.navigate(Screen.AddContact.route) },
                                            hazeState = hazeState
                                        )
                                    }
                                    composable(Screen.Settings.route) {
                                        com.example.lanman.ui.settings.SettingsScreen(
                                            onNavigateToMyQr = { navController.navigate(Screen.MyQrCode.route) },
                                            onNavigateToOnboarding = {
                                                navController.navigate(Screen.Onboarding.route) {
                                                    popUpTo(0) { inclusive = true }
                                                }
                                            },
                                            hazeState = hazeState
                                        )
                                    }
                                    composable(Screen.ContactDetail.route) { backStackEntry ->
                                        val contactId = backStackEntry.arguments?.getString("contactId") ?: return@composable
                                        com.example.lanman.ui.contacts.ContactDetailScreen(
                                            contactId = contactId,
                                            onNavigateBack = { navController.popBackStack() }
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
}