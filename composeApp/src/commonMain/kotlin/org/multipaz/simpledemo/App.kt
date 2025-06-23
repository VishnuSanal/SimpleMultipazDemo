package org.multipaz.simpledemo

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.io.bytestring.ByteString
import org.jetbrains.compose.resources.getDrawableResourceBytes
import org.jetbrains.compose.resources.getSystemResourceEnvironment
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.multipaz.compose.camera.CameraCaptureResolution
import org.multipaz.compose.camera.CameraSelection
import org.multipaz.compose.permissions.rememberBluetoothPermissionState
import org.multipaz.compose.permissions.rememberCameraPermissionState
import org.multipaz.compose.prompt.PromptDialogs
import org.multipaz.compose.qrcode.QrCodeScanner
import org.multipaz.prompt.PromptModel
import org.multipaz.simpledemo.ui.ActionButton
import org.multipaz.simpledemo.ui.DocumentCard
import org.multipaz.simpledemo.viewmodel.DocumentViewModel
import simplemultipazdemo.composeapp.generated.resources.Res
import simplemultipazdemo.composeapp.generated.resources.driving_license_card_art

@Composable
@Preview
fun App(promptModel: PromptModel) {
    val viewModel = remember { DocumentViewModel() }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope { promptModel }

    var showQrScanner by remember { mutableStateOf(false) }

    val cameraPermissionState = rememberCameraPermissionState()
    val bluetoothPermissionState = rememberBluetoothPermissionState()

    fun showToast(message: String) {
        println("vishnu: $message")
        CoroutineScope(Dispatchers.Main).launch {
            when (snackbarHostState.showSnackbar(
                message = message,
                actionLabel = "OK",
                duration = SnackbarDuration.Short,
            )) {
                SnackbarResult.Dismissed -> {}
                SnackbarResult.ActionPerformed -> {}
            }
        }
    }

    MaterialTheme {
        PromptDialogs(promptModel)

        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        ) { innerPadding ->
            Column(
                modifier = Modifier.fillMaxWidth().padding(32.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ActionButton(
                    text = "Request Bluetooth Permission",
                    onClick = {
                        coroutineScope.launch {
                            bluetoothPermissionState.launchPermissionRequest()
                        }
                    }
                )

                ActionButton(
                    text = "Request Camera Permission",
                    onClick = {
                        coroutineScope.launch {
                            cameraPermissionState.launchPermissionRequest()
                        }
                    }
                )

                ActionButton(
                    text = "Create SecureArea",
                    onClick = {
                        coroutineScope.launch {
                            viewModel.createSecureArea(
                                onSuccess = { showToast("Created SecureArea successfully") },
                                onError = { showToast("Create SecureArea failed") }
                            )
                        }
                    }
                )

                ActionButton(
                    text = "Initialize DocumentStore",
                    onClick = {
                        coroutineScope.launch {
                            viewModel.initializeDocumentStore(
                                onSuccess = { showToast("Initialized DocumentStore successfully") },
                                onError = { showToast("Initialize DocumentStore failed") }
                            )
                        }
                    }
                )

                ActionButton(
                    text = "Fetch mDocs from DocumentStore",
                    onClick = {
                        coroutineScope.launch {
                            viewModel.fetchDocuments(
                                onSuccess = { showToast("Documents fetched successfully") },
                                onError = { showToast("Fetch documents failed") }
                            )
                        }
                    }
                )

                ActionButton(
                    text = "Create mDoc",
                    onClick = {
                        coroutineScope.launch {
                            val cardArt = ByteString(
                                getDrawableResourceBytes(
                                    getSystemResourceEnvironment(),
                                    Res.drawable.driving_license_card_art,
                                )
                            )
                            viewModel.createMdoc(
                                cardArt = cardArt,
                                onSuccess = { showToast("mDoc created successfully") },
                                onError = { showToast("Create MDOC failed") }
                            )
                        }
                    }
                )

                ActionButton(
                    text = "Toggle QR Code Scanner",
                    onClick = {
                        if (!cameraPermissionState.isGranted) {
                            showToast("Camera permission is required to scan QR codes")
                        } else {
                            showQrScanner = !showQrScanner
                        }
                    }
                )

                if (showQrScanner) {
                    QrCodeScanner(
                        modifier = Modifier.padding(16.dp),
                        cameraSelection = CameraSelection.DEFAULT_BACK_CAMERA,
                        captureResolution = CameraCaptureResolution.HIGH,
                        showCameraPreview = true,
                        onCodeScanned = { qrCode ->
                            println("vishnu: QR Code scanned: $qrCode")
                            if (qrCode != null) {
                                showToast("QR Code scanned: $qrCode")

                                showQrScanner = false
                            } else {
                                showToast("No QR code detected")
                            }
                        }
                    )
                }

                viewModel.documents.forEach { document ->
                    DocumentCard(
                        document = document,
                        onShowQrCode = {
                            if (!cameraPermissionState.isGranted) {
                                showToast("Camera permission is required to scan QR codes")
                            } else {
                                showToast("WIP!")
                            }
                        },
                        onDelete = {
                            coroutineScope.launch {
                                viewModel.deleteDocument(
                                    documentId = document.identifier,
                                    onSuccess = { showToast("Document deleted successfully") },
                                    onError = { showToast("Delete Document failed") }
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}