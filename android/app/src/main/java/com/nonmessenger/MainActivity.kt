package com.nonmessenger

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nonmessenger.crypto.NonMessengerCrypto
import com.nonmessenger.network.MessagePoolClient
import com.nonmessenger.ui.theme.NonMessengerTheme
import com.nonmessenger.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    private lateinit var crypto: NonMessengerCrypto
    private lateinit var messageClient: MessagePoolClient

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Camera permission granted, can proceed with QR scanning
        } else {
            Toast.makeText(this, "Camera permission required for QR code scanning", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        crypto = NonMessengerCrypto()
        messageClient = MessagePoolClient()
        
        setContent {
            NonMessengerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MainScreen(viewModel: MainViewModel = viewModel()) {
        val context = LocalContext.current
        var selectedTab by remember { mutableStateOf(0) }
        
        Column {
            // Top App Bar
            TopAppBar(
                title = { 
                    Text(
                        "NonMessenger",
                        fontWeight = FontWeight.Bold
                    )
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
                    text = { Text("Contacts") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Messages") }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Settings") }
                )
            }
            
            // Content based on selected tab
            when (selectedTab) {
                0 -> ContactsScreen(viewModel)
                1 -> MessagesScreen(viewModel)
                2 -> SettingsScreen(viewModel)
            }
        }
    }

    @Composable
    fun ContactsScreen(viewModel: MainViewModel) {
        val contacts by viewModel.contacts.collectAsState()
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Add Contact Button
            Button(
                onClick = { 
                    checkCameraPermissionAndScan()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Add Contact (QR Code)")
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Generate Contact Code Button
            Button(
                onClick = { 
                    viewModel.generateContactCode()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Generate My Contact Code")
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Contact List
            if (contacts.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No contacts yet. Add contacts by scanning QR codes.",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                LazyColumn {
                    items(contacts) { contact ->
                        ContactItem(
                            contact = contact,
                            onContactClick = { viewModel.selectContact(contact) }
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun MessagesScreen(viewModel: MainViewModel) {
        val messages by viewModel.messages.collectAsState()
        val selectedContact by viewModel.selectedContact.collectAsState()
        
        if (selectedContact == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Select a contact to start messaging",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Messages List
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    reverseLayout = true
                ) {
                    items(messages.reversed()) { message ->
                        MessageItem(message = message)
                    }
                }
                
                // Message Input
                MessageInput(
                    onSendMessage = { text ->
                        viewModel.sendMessage(text)
                    }
                )
            }
        }
    }

    @Composable
    fun SettingsScreen(viewModel: MainViewModel) {
        val myContactCode by viewModel.myContactCode.collectAsState()
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                "Settings",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // My Contact Code Section
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        "My Contact Code",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    if (myContactCode.isNotEmpty()) {
                        Text(
                            myContactCode.joinToString(" "),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Button(
                            onClick = { viewModel.shareContactCode() }
                        ) {
                            Text("Share QR Code")
                        }
                    } else {
                        Text("No contact code generated yet")
                        
                        Button(
                            onClick = { viewModel.generateContactCode() }
                        ) {
                            Text("Generate Contact Code")
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Server Settings
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        "Server Settings",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text("Primary Server: ${viewModel.primaryServer}")
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Button(
                        onClick = { viewModel.testServerConnection() }
                    ) {
                        Text("Test Connection")
                    }
                }
            }
        }
    }

    @Composable
    fun ContactItem(
        contact: Contact,
        onContactClick: (Contact) -> Unit
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            onClick = { onContactClick(contact) }
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    contact.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Status: ${contact.status}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }

    @Composable
    fun MessageItem(message: Message) {
        val isFromMe = message.isFromMe
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = if (isFromMe) Arrangement.End else Arrangement.Start
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isFromMe) 
                        MaterialTheme.colorScheme.primary 
                    else 
                        MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.widthIn(max = 280.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    Text(
                        message.content,
                        color = if (isFromMe) 
                            MaterialTheme.colorScheme.onPrimary 
                        else 
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        message.timestamp,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isFromMe) 
                            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                        else 
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }

    @Composable
    fun MessageInput(onSendMessage: (String) -> Unit) {
        var text by remember { mutableStateOf("") }
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("Type a message...") },
                modifier = Modifier.weight(1f)
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Button(
                onClick = {
                    if (text.isNotBlank()) {
                        onSendMessage(text)
                        text = ""
                    }
                }
            ) {
                Text("Send")
            }
        }
    }

    private fun checkCameraPermissionAndScan() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                // Permission already granted, proceed with QR scanning
                startQRScanning()
            }
            else -> {
                // Request permission
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun startQRScanning() {
        // TODO: Implement QR code scanning
        Toast.makeText(this, "QR scanning not yet implemented", Toast.LENGTH_SHORT).show()
    }
}
