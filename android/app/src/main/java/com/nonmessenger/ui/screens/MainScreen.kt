package com.nonmessenger.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nonmessenger.models.Contact
import com.nonmessenger.models.Message
import com.nonmessenger.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel = viewModel(),
    hasCameraPermission: Boolean,
    onRequestCameraPermission: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    var showQRScreen by remember { mutableStateOf(false) }
    var showChatScreen by remember { mutableStateOf(false) }
    
    val contacts by viewModel.contacts.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val selectedContact by viewModel.selectedContact.collectAsState()
    val myContactCode by viewModel.myContactCode.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    
    // Show error messages
    errorMessage?.let { error ->
        LaunchedEffect(error) {
            // TODO: Show snackbar or toast
            viewModel.clearError()
        }
    }
    
    if (showQRScreen) {
        QRCodeScreen(
            myContactCode = myContactCode,
            myPublicKey = userProfile?.publicKey ?: "",
            myDeviceId = userProfile?.deviceId ?: "",
            onQRCodeScanned = { qrData ->
                viewModel.addContact(qrData)
                showQRScreen = false
            },
            onBackPressed = { showQRScreen = false }
        )
    } else if (showChatScreen && selectedContact != null) {
        ChatScreen(
            contact = selectedContact!!,
            messages = messages,
            onSendMessage = { content ->
                viewModel.sendMessage(content)
            },
            onBackPressed = { 
                showChatScreen = false
                viewModel.selectContact(selectedContact!!) // Refresh messages
            }
        )
    } else {
        Column {
            // Top App Bar
            TopAppBar(
                title = { 
                    Text(
                        "NonMessenger",
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(
                        onClick = { 
                            if (hasCameraPermission) {
                                showQRScreen = true
                            } else {
                                onRequestCameraPermission()
                            }
                        }
                    ) {
                        Icon(Icons.Default.QrCode, contentDescription = "QR Code")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
            
            // Tab Row
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Chats") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Contacts") }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Settings") }
                )
            }
            
            // Content based on selected tab
            Box(modifier = Modifier.fillMaxSize()) {
                when (selectedTab) {
                    0 -> ChatsTab(
                        contacts = contacts,
                        onContactClick = { contact ->
                            viewModel.selectContact(contact)
                            showChatScreen = true
                        }
                    )
                    1 -> ContactsTab(
                        contacts = contacts,
                        onAddContactClick = {
                            if (hasCameraPermission) {
                                showQRScreen = true
                            } else {
                                onRequestCameraPermission()
                            }
                        },
                        onContactClick = { contact ->
                            viewModel.selectContact(contact)
                            showChatScreen = true
                        }
                    )
                    2 -> SettingsTab(
                        viewModel = viewModel,
                        myContactCode = myContactCode,
                        userProfile = userProfile,
                        onShowQRCode = { showQRScreen = true }
                    )
                }
                
                // Loading overlay
                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Card {
                            Box(
                                modifier = Modifier.padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatsTab(
    contacts: List<Contact>,
    onContactClick: (Contact) -> Unit
) {
    if (contacts.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "No conversations yet",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Add contacts to start messaging",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(contacts) { contact ->
                ContactChatItem(
                    contact = contact,
                    onClick = { onContactClick(contact) }
                )
            }
        }
    }
}

@Composable
private fun ContactsTab(
    contacts: List<Contact>,
    onAddContactClick: () -> Unit,
    onContactClick: (Contact) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Add Contact Button
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            onClick = onAddContactClick
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    "Add Contact (Scan QR Code)",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        
        // Contact List
        if (contacts.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "No contacts yet",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Scan QR codes to add contacts",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(contacts) { contact ->
                    ContactItem(
                        contact = contact,
                        onClick = { onContactClick(contact) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ContactChatItem(
    contact: Contact,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar placeholder
            Card(
                modifier = Modifier.size(48.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        contact.getDisplayName().take(1).uppercase(),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    contact.getDisplayName(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Tap to start messaging",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Status indicator
            if (contact.isOnline()) {
                Card(
                    modifier = Modifier.size(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {}
            }
        }
    }
}

@Composable
private fun ContactItem(
    contact: Contact,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                contact.getDisplayName(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Status: ${contact.status}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Device: ${contact.deviceId.take(8)}...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
