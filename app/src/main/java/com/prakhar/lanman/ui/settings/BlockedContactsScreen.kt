package com.prakhar.lanman.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prakhar.lanman.ui.common.AppIcons
import com.prakhar.lanman.ui.common.ContactAvatar
import com.prakhar.lanman.ui.common.EmptyState
import com.prakhar.lanman.ui.common.GlassButton
import com.prakhar.lanman.ui.common.GlassSurface
import com.prakhar.lanman.ui.common.LanmanTopBar
import com.prakhar.lanman.ui.common.contentWidth
import com.prakhar.lanman.ui.theme.LanmanTheme

@Composable
fun BlockedContactsScreen(
    onNavigateBack: () -> Unit,
    viewModel: BlockedContactsViewModel = hiltViewModel()
) {
    val blocked by viewModel.blocked.collectAsStateWithLifecycle()
    val colors = LanmanTheme.colors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        LanmanTopBar(
            title = "Blocked contacts",
            subtitle = when {
                blocked.isEmpty() -> "Nobody is blocked"
                blocked.size == 1 -> "1 contact blocked"
                else -> "${blocked.size} contacts blocked"
            },
            onBack = onNavigateBack
        )

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            if (blocked.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        icon = AppIcons.Block,
                        title = "No blocked contacts",
                        message = "Contacts you block from Message Requests or Contact Info appear here, and their connections are rejected."
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .contentWidth()
                        .fillMaxHeight(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(blocked, key = { it.id }) { contact ->
                        GlassSurface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ContactAvatar(displayName = contact.displayName, size = 40.dp)
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = contact.displayName,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = colors.textPrimary
                                    )
                                    Text(
                                        text = contact.id.take(16),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.textSecondary
                                    )
                                }
                                GlassButton(
                                    text = "Unblock",
                                    onClick = { viewModel.unblock(contact.id) },
                                    modifier = Modifier.widthIn(min = 104.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
