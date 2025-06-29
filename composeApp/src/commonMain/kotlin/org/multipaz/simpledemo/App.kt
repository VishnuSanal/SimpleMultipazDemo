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
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.io.bytestring.ByteString
import org.jetbrains.compose.resources.getDrawableResourceBytes
import org.jetbrains.compose.resources.getSystemResourceEnvironment
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.multipaz.asn1.ASN1Integer
import org.multipaz.cbor.Cbor
import org.multipaz.compose.permissions.rememberBluetoothPermissionState
import org.multipaz.compose.permissions.rememberCameraPermissionState
import org.multipaz.compose.presentment.Presentment
import org.multipaz.compose.prompt.PromptDialogs
import org.multipaz.compose.qrcode.generateQrCode
import org.multipaz.crypto.Crypto
import org.multipaz.crypto.EcCurve
import org.multipaz.crypto.EcPrivateKey
import org.multipaz.crypto.EcPublicKey
import org.multipaz.crypto.X500Name
import org.multipaz.crypto.X509Cert
import org.multipaz.mdoc.util.MdocUtil
import org.multipaz.models.presentment.PresentmentModel
import org.multipaz.models.presentment.SimplePresentmentSource
import org.multipaz.prompt.PromptModel
import org.multipaz.simpledemo.ui.ActionButton
import org.multipaz.simpledemo.ui.DocumentCard
import org.multipaz.simpledemo.ui.ScanQrCodeDialog
import org.multipaz.simpledemo.ui.ShowReaderResult
import org.multipaz.simpledemo.utils.PrsentmentUtils
import org.multipaz.simpledemo.utils.PrsentmentUtils.startEngagement
import org.multipaz.simpledemo.viewmodel.DocumentViewModel
import org.multipaz.storage.StorageTable
import org.multipaz.storage.StorageTableSpec
import org.multipaz.trustmanagement.TrustManager
import org.multipaz.util.fromBase64Url
import org.multipaz.util.toBase64Url
import simplemultipazdemo.composeapp.generated.resources.Res
import simplemultipazdemo.composeapp.generated.resources.compose_multiplatform
import simplemultipazdemo.composeapp.generated.resources.driving_license_card_art
import kotlin.time.Duration.Companion.days

suspend fun initReaderCredentials(
    keyStorage: StorageTable,
    certsValidFrom: kotlinx.datetime.Instant,
    certsValidUntil: kotlinx.datetime.Instant
): Triple<EcPrivateKey, X509Cert, X509Cert> {
    // Bundled root key and cert (replace PEMs with your actual values)
    val bundledReaderRootKey: EcPrivateKey by lazy {
        val readerRootKeyPub = EcPublicKey.fromPem(
            """
                -----BEGIN PUBLIC KEY-----
                MHYwEAYHKoZIzj0CAQYFK4EEACIDYgAE+QDye70m2O0llPXMjVjxVZz3m5k6agT+
                wih+L79b7jyqUl99sbeUnpxaLD+cmB3HK3twkA7fmVJSobBc+9CDhkh3mx6n+YoH
                5RulaSWThWBfMyRjsfVODkosHLCDnbPV
                -----END PUBLIC KEY-----
            """.trimIndent().trim(),
            EcCurve.P384
        )
        EcPrivateKey.fromPem(
            """
                -----BEGIN PRIVATE KEY-----
                MIG2AgEAMBAGByqGSM49AgEGBSuBBAAiBIGeMIGbAgEBBDCcRuzXW3pW2h9W8pu5
                /CSR6JSnfnZVATq+408WPoNC3LzXqJEQSMzPsI9U1q+wZ2yhZANiAAT5APJ7vSbY
                7SWU9cyNWPFVnPebmTpqBP7CKH4vv1vuPKpSX32xt5SenFosP5yYHccre3CQDt+Z
                UlKhsFz70IOGSHebHqf5igflG6VpJZOFYF8zJGOx9U4OSiwcsIOds9U=
                -----END PRIVATE KEY-----
            """.trimIndent().trim(),
            readerRootKeyPub
        )
    }
    val bundledReaderRootCert: X509Cert by lazy {
        MdocUtil.generateReaderRootCertificate(
            readerRootKey = bundledReaderRootKey,
            subject = X500Name.fromName("CN=OWF Multipaz TestApp Reader Root"),
            serial = ASN1Integer.fromRandom(numBits = 128),
            validFrom = certsValidFrom,
            validUntil = certsValidUntil,
            crlUrl = "https://github.com/openwallet-foundation-labs/identity-credential/crl"
        )
    }

    val readerRootKey = keyStorage.get("readerRootKey")
        ?.let { EcPrivateKey.fromDataItem(Cbor.decode(it.toByteArray())) }
        ?: run {
            keyStorage.insert(
                "readerRootKey",
                ByteString(Cbor.encode(bundledReaderRootKey.toDataItem()))
            )
            bundledReaderRootKey
        }


    readerRootCert = keyStorage.get("readerRootCert")
        ?.let { X509Cert.fromDataItem(Cbor.decode(it.toByteArray())) }
        ?: run {
            keyStorage.insert(
                "readerRootCert",
                ByteString(Cbor.encode(bundledReaderRootCert.toDataItem()))
            )
            bundledReaderRootCert
        }

    // Reader key and cert
    readerKey = keyStorage.get("readerKey")?.let {
        EcPrivateKey.fromDataItem(Cbor.decode(it.toByteArray()))
    } ?: run {
        val key = Crypto.createEcPrivateKey(EcCurve.P256)
        keyStorage.insert("readerKey", ByteString(Cbor.encode(key.toDataItem())))
        key
    }
    readerCert = keyStorage.get("readerCert")?.let {
        X509Cert.fromDataItem(Cbor.decode(it.toByteArray()))
    } ?: run {
        val cert = MdocUtil.generateReaderCertificate(
            readerRootCert = readerRootCert,
            readerRootKey = readerRootKey,
            readerKey = readerKey.publicKey,
            subject = X500Name.fromName("CN=OWF IC TestApp Reader Cert"),
            serial = ASN1Integer.fromRandom(numBits = 128),
            validFrom = certsValidFrom,
            validUntil = certsValidUntil,
        )
        keyStorage.insert("readerCert", ByteString(Cbor.encode(cert.toDataItem())))
        cert
    }

    return Triple(readerKey, readerCert, readerRootCert)
}

lateinit var readerKey: EcPrivateKey
lateinit var readerCert: X509Cert
lateinit var readerRootCert: X509Cert

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

    val readerSessionTranscript = remember { mutableStateOf<ByteArray?>(null) }
    val eReaderKeyState = remember { mutableStateOf<EcPrivateKey?>(null) }

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
                    text = "Initialize Keys", onClick = {
                        coroutineScope.launch {
                            initReaderCredentials(
                                viewModel.storage.getTable(
                                    StorageTableSpec(
                                        name = "TestAppKeys",
                                        supportPartitions = false,
                                        supportExpiration = false
                                    )
                                ),
                                Clock.System.now(),
                                Clock.System.now().plus(365.days)
                            )
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
                    text = "Show QR Scanner", onClick = {
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
                        } else if (deviceEngagement.value == null) {
                            startEngagement(presentmentModel, deviceEngagement)
                        } else {
                            deviceEngagement.value = null
                        }
                    })

                var readerJob by remember { mutableStateOf<Job?>(null) }
                val readerMostRecentDeviceResponse =
                    remember { mutableStateOf<ByteArray?>(null) }

                if (showQrScanner) {
                    ScanQrCodeDialog(
                        title = { Text("Scan QR code") },
                        text = { Text("Scan the mdoc QR code") },
                        dismissButton = "Close",
                        onCodeScanned = { data ->
                            if (data.startsWith("mdoc:")) {
                                showQrScanner = false
                                readerJob = coroutineScope.launch {
                                    try {
                                        PrsentmentUtils.doReaderFlow(
                                            encodedDeviceEngagement = ByteString(
                                                data.substring(5).fromBase64Url()
                                            ),
                                            showToast = { showToast(it) },
                                            viewModel = viewModel,
                                            readerKey = readerKey,
                                            readerCert = readerCert,
                                            readerRootCert = readerRootCert,
                                            readerMostRecentDeviceResponse = readerMostRecentDeviceResponse,
                                            readerSessionTranscript = readerSessionTranscript,
                                            eReaderKeyState = eReaderKeyState
                                        )
                                    } catch (e: Throwable) {
                                        showToast("Error: ${e.message}")
                                    }
                                    readerJob = null
                                }
                                true
                            } else {
                                false
                            }
                        },
                        onDismiss = { showQrScanner = false }
                    )
                }

                if (readerMostRecentDeviceResponse.value != null &&
                    readerSessionTranscript.value != null &&
                    eReaderKeyState.value != null
                ) {
                    ShowReaderResult(
                        readerMostRecentDeviceResponse = readerMostRecentDeviceResponse,
                        readerSessionTranscript = readerSessionTranscript,
                        eReaderKey = eReaderKeyState.value!!,
                        viewModel
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