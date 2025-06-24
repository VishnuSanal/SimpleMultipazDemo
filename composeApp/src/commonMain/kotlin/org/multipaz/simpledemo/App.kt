package org.multipaz.simpledemo

import androidx.compose.foundation.Image
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.io.bytestring.ByteString
import org.jetbrains.compose.resources.getDrawableResourceBytes
import org.jetbrains.compose.resources.getSystemResourceEnvironment
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.multipaz.cbor.Simple
import org.multipaz.compose.permissions.rememberBluetoothPermissionState
import org.multipaz.compose.permissions.rememberCameraPermissionState
import org.multipaz.compose.presentment.Presentment
import org.multipaz.compose.prompt.PromptDialogs
import org.multipaz.compose.qrcode.generateQrCode
import org.multipaz.crypto.Crypto
import org.multipaz.crypto.EcCurve
import org.multipaz.mdoc.connectionmethod.MdocConnectionMethodBle
import org.multipaz.mdoc.engagement.EngagementGenerator
import org.multipaz.mdoc.role.MdocRole
import org.multipaz.mdoc.transport.MdocTransportFactory
import org.multipaz.mdoc.transport.MdocTransportOptions
import org.multipaz.mdoc.transport.advertise
import org.multipaz.mdoc.transport.waitForConnection
import org.multipaz.models.presentment.MdocPresentmentMechanism
import org.multipaz.models.presentment.PresentmentModel
import org.multipaz.models.presentment.SimplePresentmentSource
import org.multipaz.prompt.PromptModel
import org.multipaz.simpledemo.ui.ActionButton
import org.multipaz.simpledemo.ui.DocumentCard
import org.multipaz.simpledemo.ui.ScanQrCodeDialog
import org.multipaz.simpledemo.viewmodel.DocumentViewModel
import org.multipaz.trustmanagement.TrustManager
import org.multipaz.util.UUID
import org.multipaz.util.toBase64Url
import simplemultipazdemo.composeapp.generated.resources.Res
import simplemultipazdemo.composeapp.generated.resources.compose_multiplatform
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

    val presentmentModel = PresentmentModel().apply { setPromptModel(promptModel) }
    val deviceEngagement = remember { mutableStateOf<ByteString?>(null) }

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
                    text = "Request Bluetooth Permission", onClick = {
                        coroutineScope.launch {
                            bluetoothPermissionState.launchPermissionRequest()
                        }
                    })

                ActionButton(
                    text = "Request Camera Permission", onClick = {
                        coroutineScope.launch {
                            cameraPermissionState.launchPermissionRequest()
                        }
                    })

                ActionButton(
                    text = "Create SecureArea", onClick = {
                        coroutineScope.launch {
                            viewModel.createSecureArea(
                                onSuccess = { showToast("Created SecureArea successfully") },
                                onError = { showToast("Create SecureArea failed") })
                        }
                    })

                ActionButton(
                    text = "Initialize DocumentStore", onClick = {
                        coroutineScope.launch {
                            viewModel.initializeDocumentStore(
                                onSuccess = { showToast("Initialized DocumentStore successfully") },
                                onError = { showToast("Initialize DocumentStore failed") })
                        }
                    })

                ActionButton(
                    text = "Fetch mDocs from DocumentStore", onClick = {
                        coroutineScope.launch {
                            viewModel.fetchDocuments(
                                onSuccess = { showToast("Documents fetched successfully") },
                                onError = { showToast("Fetch documents failed") })
                        }
                    })

                ActionButton(
                    text = "Create mDoc", onClick = {
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
                                onError = { showToast("Create MDOC failed") })
                        }
                    })

                ActionButton(
                    text = "Toggle QR Scanner", onClick = {
                        if (!cameraPermissionState.isGranted) {
                            showToast("Camera permission is required to scan QR codes")
                        } else {
                            showQrScanner = !showQrScanner
                        }
                    })

                ActionButton(
                    text = "Toggle QR Code", onClick = {
                        if (!bluetoothPermissionState.isGranted) {
                            showToast("Bluetooth permission is required to start engagement")
                        } else {
                            startEngagement(presentmentModel, deviceEngagement)
                        }
                    })

                if (showQrScanner) {
                    val qrCode = remember { mutableStateOf<String?>(null) }
                    ScanQrCodeDialog(
                        title = { Text("Scan code") },
                        text = { Text("If a QR code is detected, it is printed out at the bottom of the dialog") },
                        dismissButton = "Close",
                        onCodeScanned = { data ->
                            qrCode.value = data
                            false
                        },
                        onNoCodeDetected = {
                            qrCode.value = null
                        },
                        additionalContent = {
                            if (qrCode.value == null) {
                                Text("No QR Code detected")
                            } else {
                                Text("QR: ${qrCode.value}")
                            }

                        },
                        onDismiss = { showQrScanner = false }
                    )
                }

                val state = presentmentModel.state.collectAsState()
                println(state.value)
                when (state.value) {
                    PresentmentModel.State.IDLE -> {
                    }

                    PresentmentModel.State.CONNECTING -> {
                        if (deviceEngagement.value != null) {
                            val mdocUrl =
                                "mdoc:" + deviceEngagement.value!!.toByteArray().toBase64Url()
                            val qrCodeBitmap = remember { generateQrCode(mdocUrl) }
                            Image(
                                modifier = Modifier.fillMaxWidth(),
                                bitmap = qrCodeBitmap,
                                contentDescription = null,
                                contentScale = ContentScale.Inside
                            )
                        }
                    }

                    PresentmentModel.State.WAITING_FOR_SOURCE,
                    PresentmentModel.State.PROCESSING,
                    PresentmentModel.State.WAITING_FOR_DOCUMENT_SELECTION,
                    PresentmentModel.State.WAITING_FOR_CONSENT,
                    PresentmentModel.State.COMPLETED -> {
                        Presentment(
                            presentmentModel = presentmentModel,
                            documentTypeRepository = viewModel.documentTypeRepository,
                            presentmentSource = SimplePresentmentSource(
                                documentStore = viewModel.documentStore,
                                documentTypeRepository = viewModel.documentTypeRepository,
                                readerTrustManager = TrustManager(), // fixme
                                preferSignatureToKeyAgreement = true,
                                domainMdocSignature = "mdoc",
                            ),
                            onPresentmentComplete = {
                                presentmentModel.reset()
                            },
                            appName = "MpzCmpWallet",
                            appIconPainter = painterResource(Res.drawable.compose_multiplatform),
                            modifier = Modifier
                        )
                    }
                }

                viewModel.documents.forEach { document ->
                    DocumentCard(document = document, onDelete = {
                        coroutineScope.launch {
                            viewModel.deleteDocument(
                                documentId = document.identifier,
                                onSuccess = { showToast("Document deleted successfully") },
                                onError = { showToast("Delete Document failed") })
                        }
                    })
                }
            }
        }
    }
}

fun startEngagement(
    presentmentModel: PresentmentModel,
    showQrCode: MutableState<ByteString?>
) {
    presentmentModel.reset()
    presentmentModel.setConnecting()
    presentmentModel.presentmentScope.launch() {
        val connectionMethods = listOf(
            MdocConnectionMethodBle(
                supportsPeripheralServerMode = false,
                supportsCentralClientMode = true,
                peripheralServerModeUuid = null,
                centralClientModeUuid = UUID.randomUUID(),
            )
        )
        val eDeviceKey = Crypto.createEcPrivateKey(EcCurve.P256)
        val advertisedTransports = connectionMethods.advertise(
            role = MdocRole.MDOC,
            transportFactory = MdocTransportFactory.Default,
            options = MdocTransportOptions(bleUseL2CAP = true),
        )
        val engagementGenerator = EngagementGenerator(
            eSenderKey = eDeviceKey.publicKey, version = EngagementGenerator.ENGAGEMENT_VERSION_1_0
        )
        engagementGenerator.addConnectionMethods(advertisedTransports.map {
            it.connectionMethod
        })
        val encodedDeviceEngagement = ByteString(engagementGenerator.generate())
        showQrCode.value = encodedDeviceEngagement
        val transport = advertisedTransports.waitForConnection(
            eSenderKey = eDeviceKey.publicKey,
            coroutineScope = presentmentModel.presentmentScope
        )
        presentmentModel.setMechanism(
            MdocPresentmentMechanism(
                transport = transport,
                eDeviceKey = eDeviceKey,
                encodedDeviceEngagement = encodedDeviceEngagement,
                handover = Simple.NULL,
                engagementDuration = null,
                allowMultipleRequests = false
            )
        )
        showQrCode.value = null
    }
}