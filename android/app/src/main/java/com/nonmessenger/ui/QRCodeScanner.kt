package com.nonmessenger.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.CompoundBarcodeView
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import com.google.zxing.BarcodeFormat
import com.google.zxing.ResultPoint

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QRCodeScannerScreen(
    onQRCodeScanned: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var isFlashOn by remember { mutableStateOf(false) }
    var scannedCode by remember { mutableStateOf<String?>(null) }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }
    
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
    
    if (!hasCameraPermission) {
        PermissionDeniedScreen(onDismiss = onDismiss)
        return
    }
    
    if (scannedCode != null) {
        QRCodeResultScreen(
            qrCode = scannedCode!!,
            onAccept = { onQRCodeScanned(scannedCode!!) },
            onRetry = { scannedCode = null },
            onDismiss = onDismiss
        )
        return
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        // Camera Preview
        AndroidView(
            factory = { context ->
                CompoundBarcodeView(context).apply {
                    val formats = listOf(BarcodeFormat.QR_CODE)
                    barcodeView.decoderFactory = DefaultDecoderFactory(formats)
                    
                    val callback = object : BarcodeCallback {
                        override fun barcodeResult(result: BarcodeResult) {
                            scannedCode = result.text
                        }
                        
                        override fun possibleResultPoints(resultPoints: List<ResultPoint>) {
                            // Handle possible result points if needed
                        }
                    }
                    
                    barcodeView.decodeContinuous(callback)
                    resume()
                }
            },
            modifier = Modifier.fillMaxSize()
        ) { view ->
            // Update flash state
            if (isFlashOn) {
                view.setTorchOn()
            } else {
                view.setTorchOff()
            }
        }
        
        // Overlay UI
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Top bar
            TopAppBar(
                title = { Text("Scan QR Code", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { isFlashOn = !isFlashOn }) {
                        Icon(
                            if (isFlashOn) Icons.Default.FlashOff else Icons.Default.FlashOn,
                            contentDescription = if (isFlashOn) "Turn off flash" else "Turn on flash",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Scanning instructions
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Black.copy(alpha = 0.7f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Position QR code within the frame",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "The QR code will be scanned automatically",
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
        
        // Scanning frame overlay
        ScanningFrame(
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

@Composable
fun ScanningFrame(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(250.dp)
    ) {
        // Corner indicators
        val cornerSize = 20.dp
        val strokeWidth = 4.dp
        
        // Top-left corner
        Canvas(
            modifier = Modifier
                .size(cornerSize)
                .align(Alignment.TopStart)
        ) {
            drawLine(
                color = Color.White,
                start = Offset(0f, strokeWidth.toPx()),
                end = Offset(cornerSize.toPx(), strokeWidth.toPx()),
                strokeWidth = strokeWidth.toPx()
            )
            drawLine(
                color = Color.White,
                start = Offset(strokeWidth.toPx(), 0f),
                end = Offset(strokeWidth.toPx(), cornerSize.toPx()),
                strokeWidth = strokeWidth.toPx()
            )
        }
        
        // Top-right corner
        Canvas(
            modifier = Modifier
                .size(cornerSize)
                .align(Alignment.TopEnd)
        ) {
            drawLine(
                color = Color.White,
                start = Offset(0f, strokeWidth.toPx()),
                end = Offset(cornerSize.toPx(), strokeWidth.toPx()),
                strokeWidth = strokeWidth.toPx()
            )
            drawLine(
                color = Color.White,
                start = Offset(cornerSize.toPx() - strokeWidth.toPx(), 0f),
                end = Offset(cornerSize.toPx() - strokeWidth.toPx(), cornerSize.toPx()),
                strokeWidth = strokeWidth.toPx()
            )
        }
        
        // Bottom-left corner
        Canvas(
            modifier = Modifier
                .size(cornerSize)
                .align(Alignment.BottomStart)
        ) {
            drawLine(
                color = Color.White,
                start = Offset(0f, cornerSize.toPx() - strokeWidth.toPx()),
                end = Offset(cornerSize.toPx(), cornerSize.toPx() - strokeWidth.toPx()),
                strokeWidth = strokeWidth.toPx()
            )
            drawLine(
                color = Color.White,
                start = Offset(strokeWidth.toPx(), 0f),
                end = Offset(strokeWidth.toPx(), cornerSize.toPx()),
                strokeWidth = strokeWidth.toPx()
            )
        }
        
        // Bottom-right corner
        Canvas(
            modifier = Modifier
                .size(cornerSize)
                .align(Alignment.BottomEnd)
        ) {
            drawLine(
                color = Color.White,
                start = Offset(0f, cornerSize.toPx() - strokeWidth.toPx()),
                end = Offset(cornerSize.toPx(), cornerSize.toPx() - strokeWidth.toPx()),
                strokeWidth = strokeWidth.toPx()
            )
            drawLine(
                color = Color.White,
                start = Offset(cornerSize.toPx() - strokeWidth.toPx(), 0f),
                end = Offset(cornerSize.toPx() - strokeWidth.toPx(), cornerSize.toPx()),
                strokeWidth = strokeWidth.toPx()
            )
        }
    }
}

@Composable
fun PermissionDeniedScreen(onDismiss: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Camera Permission Required",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            "To scan QR codes, NonMessenger needs access to your camera. Please grant camera permission in your device settings.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(onClick = onDismiss) {
            Text("Close")
        }
    }
}

@Composable
fun QRCodeResultScreen(
    qrCode: String,
    onAccept: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "QR Code Scanned",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    "Scanned Content:",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    qrCode,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            OutlinedButton(onClick = onRetry) {
                Text("Scan Again")
            }
            
            Button(onClick = onAccept) {
                Text("Add Contact")
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        TextButton(onClick = onDismiss) {
            Text("Cancel")
        }
    }
}
