package com.nonmessenger.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanIntentResult
import com.journeyapps.barcodescanner.ScanOptions
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.material.icons.filled.QrCodeScanner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QRCodeScreen(
    myContactCode: List<String>,
    myPublicKey: String,
    myDeviceId: String,
    onQRCodeScanned: (String) -> Unit,
    onBackPressed: () -> Unit
) {
    val context = LocalContext.current
    var showMyQRCode by remember { mutableStateOf(true) }
    var qrCodeBitmap by remember { mutableStateOf<Bitmap?>(null) }
    
    // QR Code scanner launcher
    val scanLauncher = rememberLauncherForActivityResult(
        contract = ScanContract()
    ) { result: ScanIntentResult ->
        if (result.contents != null) {
            onQRCodeScanned(result.contents)
        }
    }
    
    // Generate QR code when screen loads
    LaunchedEffect(myContactCode, myPublicKey, myDeviceId) {
        if (myContactCode.isNotEmpty() && myPublicKey.isNotEmpty()) {
            qrCodeBitmap = generateQRCode(myContactCode, myPublicKey, myDeviceId)
        }
    }
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Top App Bar
        TopAppBar(
            title = { Text("Contact Exchange") },
            navigationIcon = {
                IconButton(onClick = onBackPressed) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                if (showMyQRCode && qrCodeBitmap != null) {
                    IconButton(onClick = { 
                        // TODO: Share QR code image
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Share")
                    }
                }
            }
        )
        
        // Toggle buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { showMyQRCode = true },
                modifier = Modifier.weight(1f),
                colors = if (showMyQRCode) {
                    ButtonDefaults.buttonColors()
                } else {
                    ButtonDefaults.outlinedButtonColors()
                }
            ) {
                Text("Show My QR")
            }
            
            Button(
                onClick = { 
                    showMyQRCode = false
                    // Start QR scanner
                    val options = ScanOptions().apply {
                        setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                        setPrompt("Scan contact's QR code")
                        setCameraId(0)
                        setBeepEnabled(true)
                        setBarcodeImageEnabled(true)
                        setOrientationLocked(false)
                    }
                    scanLauncher.launch(options)
                },
                modifier = Modifier.weight(1f),
                colors = if (!showMyQRCode) {
                    ButtonDefaults.buttonColors()
                } else {
                    ButtonDefaults.outlinedButtonColors()
                }
            ) {
                Icon(
                    Icons.Default.QrCodeScanner,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Scan QR")
            }
        }
        
        // Content
        if (showMyQRCode) {
            MyQRCodeContent(
                contactCode = myContactCode,
                qrCodeBitmap = qrCodeBitmap
            )
        } else {
            ScanInstructionsContent()
        }
    }
}

@Composable
private fun MyQRCodeContent(
    contactCode: List<String>,
    qrCodeBitmap: Bitmap?
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Share this QR code with contacts",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // QR Code
        if (qrCodeBitmap != null) {
            Card(
                modifier = Modifier.size(300.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = qrCodeBitmap.asImageBitmap(),
                        contentDescription = "My QR Code",
                        modifier = Modifier.size(280.dp)
                    )
                }
            }
        } else {
            Card(
                modifier = Modifier.size(300.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Contact code display
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    "Your Contact Code",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                if (contactCode.isNotEmpty()) {
                    Text(
                        contactCode.joinToString(" "),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text(
                        "No contact code generated",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Instructions
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    "How to add contacts:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    "1. Show this QR code to your contact\n" +
                    "2. Have them scan it with their NonMessenger app\n" +
                    "3. Scan their QR code in return\n" +
                    "4. Both devices will exchange public keys securely",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun ScanInstructionsContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.QrCodeScanner,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            "Scan Contact's QR Code",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            "Point your camera at the contact's QR code to add them to your contact list",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun generateQRCode(
    contactCode: List<String>,
    publicKey: String,
    deviceId: String
): Bitmap? {
    return try {
        // Create QR data JSON
        val qrData = mapOf(
            "version" to "1.0",
            "type" to "nonmessenger_contact",
            "publicKey" to publicKey,
            "deviceId" to deviceId,
            "contactWords" to contactCode,
            "timestamp" to System.currentTimeMillis()
        )
        
        val qrString = com.google.gson.Gson().toJson(qrData)
        
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(
            qrString,
            BarcodeFormat.QR_CODE,
            512,
            512,
            mapOf(EncodeHintType.MARGIN to 1)
        )
        
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(
                    x, y,
                    if (bitMatrix[x, y]) android.graphics.Color.BLACK
                    else android.graphics.Color.WHITE
                )
            }
        }
        
        bitmap
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
