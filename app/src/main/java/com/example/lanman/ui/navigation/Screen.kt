package com.example.lanman.ui.navigation

sealed class Screen(val route: String) {
    object Onboarding : Screen("onboarding")
    object ChatList : Screen("chat_list")
    object Conversation : Screen("conversation/{contactId}") {
        fun createRoute(contactId: String) = "conversation/$contactId"
    }
    object MyQrCode : Screen("my_qr_code")
    object AddContact : Screen("add_contact")
    object Contacts : Screen("contacts")
    object Settings : Screen("settings")
    object ContactDetail : Screen("contact_detail/{contactId}") {
        fun createRoute(contactId: String) = "contact_detail/$contactId"
    }
    object MessageRequests : Screen("message_requests")
}
