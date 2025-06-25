package org.multipaz.simpledemo

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.toLocalDateTime
import kotlinx.io.bytestring.ByteString
import org.jetbrains.compose.resources.getDrawableResourceBytes
import org.jetbrains.compose.resources.getSystemResourceEnvironment
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.multipaz.asn1.ASN1Integer
import org.multipaz.cbor.Bstr
import org.multipaz.cbor.Cbor
import org.multipaz.cbor.DiagnosticOption
import org.multipaz.cbor.Simple
import org.multipaz.compose.cards.InfoCard
import org.multipaz.compose.cards.WarningCard
import org.multipaz.compose.decodeImage
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
import org.multipaz.documenttype.DocumentAttributeType
import org.multipaz.documenttype.DocumentCannedRequest
import org.multipaz.documenttype.DocumentType
import org.multipaz.documenttype.DocumentTypeRepository
import org.multipaz.mdoc.connectionmethod.MdocConnectionMethod
import org.multipaz.mdoc.connectionmethod.MdocConnectionMethodBle
import org.multipaz.mdoc.engagement.EngagementGenerator
import org.multipaz.mdoc.engagement.EngagementParser
import org.multipaz.mdoc.response.DeviceResponseParser
import org.multipaz.mdoc.role.MdocRole
import org.multipaz.mdoc.sessionencryption.SessionEncryption
import org.multipaz.mdoc.transport.MdocTransportFactory
import org.multipaz.mdoc.transport.MdocTransportOptions
import org.multipaz.mdoc.transport.advertise
import org.multipaz.mdoc.transport.waitForConnection
import org.multipaz.mdoc.util.MdocUtil
import org.multipaz.models.presentment.MdocPresentmentMechanism
import org.multipaz.models.presentment.PresentmentModel
import org.multipaz.models.presentment.SimplePresentmentSource
import org.multipaz.prompt.PromptModel
import org.multipaz.simpledemo.ui.ActionButton
import org.multipaz.simpledemo.ui.DocumentCard
import org.multipaz.simpledemo.ui.ScanQrCodeDialog
import org.multipaz.simpledemo.viewmodel.DocumentViewModel
import org.multipaz.storage.StorageTable
import org.multipaz.storage.StorageTableSpec
import org.multipaz.trustmanagement.TrustManager
import org.multipaz.util.Constants
import org.multipaz.util.Logger
import org.multipaz.util.UUID
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
    val readerRootCert = keyStorage.get("readerRootCert")
        ?.let { X509Cert.fromDataItem(Cbor.decode(it.toByteArray())) }
        ?: run {
            keyStorage.insert(
                "readerRootCert",
                ByteString(Cbor.encode(bundledReaderRootCert.toDataItem()))
            )
            bundledReaderRootCert
        }

    // Reader key and cert
    val readerKey = keyStorage.get("readerKey")?.let {
        EcPrivateKey.fromDataItem(Cbor.decode(it.toByteArray()))
    } ?: run {
        val key = Crypto.createEcPrivateKey(EcCurve.P256)
        keyStorage.insert("readerKey", ByteString(Cbor.encode(key.toDataItem())))
        key
    }
    val readerCert = keyStorage.get("readerCert")?.let {
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

lateinit var keys: Triple<EcPrivateKey, X509Cert, X509Cert>

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

    // --- Add state for reader session transcript and eReaderKey ---
    val readerSessionTranscript = remember { mutableStateOf<ByteArray?>(null) }
    val eReaderKeyState = remember { mutableStateOf<EcPrivateKey?>(null) }
    // -------------------------------------------------------------

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
                            keys = initReaderCredentials(
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
                                        // --- Call doReaderFlow and set session transcript and eReaderKey ---
                                        doReaderFlow(
                                            encodedDeviceEngagement = ByteString(
                                                data.substring(5).fromBase64Url()
                                            ),
                                            showToast = { showToast(it) },
                                            viewModel = viewModel,
                                            keys = keys,
                                            readerMostRecentDeviceResponse = readerMostRecentDeviceResponse,
                                            readerSessionTranscript = readerSessionTranscript,
                                            eReaderKeyState = eReaderKeyState
                                        )
                                        // -----------------------------------------------------------------
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
                    ShowReaderResults(
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

private data class RequestPickerEntry(
    val displayName: String,
    val documentType: DocumentType,
    val sampleRequest: DocumentCannedRequest
)

// --- Retrofit doReaderFlow to update new states ---
private suspend fun doReaderFlow(
    encodedDeviceEngagement: ByteString,
    showToast: (String) -> Unit,
    viewModel: DocumentViewModel,
    keys: Triple<EcPrivateKey, X509Cert, X509Cert>,
    readerMostRecentDeviceResponse: MutableState<ByteArray?>,
    readerSessionTranscript: MutableState<ByteArray?>,
    eReaderKeyState: MutableState<EcPrivateKey?>
) {
    val deviceEngagement = EngagementParser(encodedDeviceEngagement.toByteArray()).parse()
    val eDeviceKey = deviceEngagement.eSenderKey
    val eReaderKey = Crypto.createEcPrivateKey(eDeviceKey.curve)

    val connectionMethods = MdocConnectionMethod.disambiguate(
        deviceEngagement.connectionMethods,
        MdocRole.MDOC_READER
    )
    val connectionMethod = connectionMethods.firstOrNull() ?: return
    val transport = MdocTransportFactory.Default.createTransport(
        connectionMethod,
        MdocRole.MDOC_READER,
        MdocTransportOptions(bleUseL2CAP = false)
    )

    val encodedSessionTranscript = TestAppUtils.generateEncodedSessionTranscript(
        encodedDeviceEngagement.toByteArray(),
        Simple.NULL,
        eReaderKey.publicKey
    )
    val sessionEncryption = SessionEncryption(
        MdocRole.MDOC_READER,
        eReaderKey,
        eDeviceKey,
        encodedSessionTranscript
    )

    val selectedRequest = TestAppUtils.provisionedDocumentTypes.first().cannedRequests.first()
    val encodedDeviceRequest = TestAppUtils.generateEncodedDeviceRequest(
        request = selectedRequest,
        encodedSessionTranscript = encodedSessionTranscript,
        readerKey = keys.first,
        readerCert = keys.second,
        readerRootCert = keys.third
    )

    try {
        transport.open(eDeviceKey)
        transport.sendMessage(sessionEncryption.encryptMessage(encodedDeviceRequest, null))
        while (true) {
            val sessionData = transport.waitForMessage()
            if (sessionData.isEmpty()) {
                showToast("Session terminated by holder")
                transport.close()
                break
            }
            val (message, status) = sessionEncryption.decryptMessage(sessionData)
            if (status == Constants.SESSION_DATA_STATUS_SESSION_TERMINATION) {

                readerMostRecentDeviceResponse.value = message
                readerSessionTranscript.value = encodedSessionTranscript
                eReaderKeyState.value = eReaderKey

                showToast("Session termination received: $message $status")
                transport.close()
                break
            }
            // Only handle one request/response for simplicity
            // --- Set the states for display ---
            readerMostRecentDeviceResponse.value = message
            readerSessionTranscript.value = encodedSessionTranscript
            eReaderKeyState.value = eReaderKey
            // -----------------------------------
            transport.sendMessage(SessionEncryption.encodeStatus(Constants.SESSION_DATA_STATUS_SESSION_TERMINATION))
            transport.close()
            break
        }
    } finally {
        transport.close()
    }
}

@Composable
private fun ShowReaderResults(
    readerMostRecentDeviceResponse: MutableState<ByteArray?>,
    readerSessionTranscript: MutableState<ByteArray?>,
    eReaderKey: EcPrivateKey,
    viewModel: DocumentViewModel
) {
    val deviceResponse1 = readerMostRecentDeviceResponse.value
    if (deviceResponse1 == null || deviceResponse1.isEmpty()) {
        Text(
            text = "Waiting for data",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
        )
    } else {
        val parser = DeviceResponseParser(
            encodedDeviceResponse = deviceResponse1,
            encodedSessionTranscript = readerSessionTranscript.value!!,
        )
        parser.setEphemeralReaderKey(eReaderKey)
        val deviceResponse2 = parser.parse()
        if (deviceResponse2.documents.isEmpty()) {
            Text(
                text = "No documents in response",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
            )
        } else {
            // TODO: show multiple documents
            val documentData = DocumentData.fromMdocDeviceResponseDocument(
                deviceResponse2.documents[0],
                viewModel.documentTypeRepository,
                TrustManager()
            )
            ShowDocumentData(documentData, 0, deviceResponse2.documents.size)
        }
    }
}

@Composable
private fun ShowDocumentData(
    documentData: DocumentData,
    documentIndex: Int,
    numDocuments: Int
) {
    Column(
        Modifier
            .padding(8.dp)
    ) {

        for (text in documentData.infoTexts) {
            InfoCard {
                Text(text)
            }
        }
        for (text in documentData.warningTexts) {
            WarningCard {
                Text(text)
            }
        }

        if (numDocuments > 1) {
            ShowKeyValuePair(
                DocumentKeyValuePair(
                    "Document Number",
                    "${documentIndex + 1} of $numDocuments"
                )
            )
        }

        for (kvPair in documentData.kvPairs) {
            ShowKeyValuePair(kvPair)
        }

    }
}

@Composable
private fun ShowKeyValuePair(kvPair: DocumentKeyValuePair) {
    Column(
        Modifier
            .padding(8.dp)
            .fillMaxWidth()
    ) {
        Text(
            text = kvPair.key,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = kvPair.textValue,
            style = MaterialTheme.typography.bodyMedium
        )
        if (kvPair.bitmap != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Image(
                    bitmap = kvPair.bitmap,
                    modifier = Modifier.size(200.dp),
                    contentDescription = null
                )
            }

        }
    }
}

private data class DocumentData(
    val infoTexts: List<String>,
    val warningTexts: List<String>,
    val kvPairs: List<DocumentKeyValuePair>
) {
    companion object {

        fun fromMdocDeviceResponseDocument(
            document: DeviceResponseParser.Document,
            documentTypeRepository: DocumentTypeRepository,
            issuerTrustManager: TrustManager
        ): DocumentData {
            val infos = mutableListOf<String>()
            val warnings = mutableListOf<String>()
            val kvPairs = mutableListOf<DocumentKeyValuePair>()

            if (document.issuerSignedAuthenticated) {
                val trustResult =
                    issuerTrustManager.verify(document.issuerCertificateChain.certificates)
                if (trustResult.isTrusted) {
                    if (trustResult.trustPoints[0].displayName != null) {
                        infos.add("Issuer '${trustResult.trustPoints[0].displayName}' is in a trust list")
                    } else {
                        infos.add(
                            "Issuer with name '${trustResult.trustPoints[0].certificate.subject.name}' " +
                                    "is in a trust list"
                        )
                    }
                } else {
                    warnings.add("Issuer is not in trust list")
                }
            }
            if (!document.deviceSignedAuthenticated) {
                warnings.add("Device Authentication failed")
            }
            if (!document.issuerSignedAuthenticated) {
                warnings.add("Issuer Authentication failed")
            }
            if (document.numIssuerEntryDigestMatchFailures > 0) {
                warnings.add("One or more issuer provided data elements failed to authenticate")
            }
            val now = Clock.System.now()
            if (now < document.validityInfoValidFrom || now > document.validityInfoValidUntil) {
                warnings.add("Document information is not valid at this point in time.")
            }

            kvPairs.add(DocumentKeyValuePair("Type", "ISO mdoc (ISO/IEC 18013-5:2021)"))
            kvPairs.add(DocumentKeyValuePair("DocType", document.docType))
            kvPairs.add(
                DocumentKeyValuePair(
                    "Valid From",
                    formatTime(document.validityInfoValidFrom)
                )
            )
            kvPairs.add(
                DocumentKeyValuePair(
                    "Valid Until",
                    formatTime(document.validityInfoValidUntil)
                )
            )
            kvPairs.add(DocumentKeyValuePair("Signed At", formatTime(document.validityInfoSigned)))
            kvPairs.add(
                DocumentKeyValuePair(
                    "Expected Update",
                    document.validityInfoExpectedUpdate?.let { formatTime(it) } ?: "Not Set"
                ))

            val mdocType =
                documentTypeRepository.getDocumentTypeForMdoc(document.docType)?.mdocDocumentType

            // TODO: Handle DeviceSigned data
            for (namespaceName in document.issuerNamespaces) {
                val mdocNamespace = if (mdocType != null) {
                    mdocType.namespaces.get(namespaceName)
                } else {
                    documentTypeRepository.getDocumentTypeForMdocNamespace(namespaceName)
                        ?.mdocDocumentType?.namespaces?.get(namespaceName)
                }

                kvPairs.add(DocumentKeyValuePair("Namespace", namespaceName))
                for (dataElementName in document.getIssuerEntryNames(namespaceName)) {
                    val mdocDataElement = mdocNamespace?.dataElements?.get(dataElementName)
                    val encodedDataElementValue =
                        document.getIssuerEntryData(namespaceName, dataElementName)
                    val dataElement = Cbor.decode(encodedDataElementValue)
                    var bitmap: ImageBitmap? = null
                    val (key, value) = if (mdocDataElement != null) {
                        if (dataElement is Bstr && mdocDataElement.attribute.type == DocumentAttributeType.Picture) {
                            try {
                                bitmap = decodeImage(dataElement.value)
                            } catch (e: Throwable) {
                                Logger.w(
                                    "vishnu",
                                    "Error decoding image for data element $dataElement in " +
                                            "namespace $namespaceName",
                                    e
                                )
                            }
                        }
                        Pair(
                            mdocDataElement.attribute.displayName,
                            mdocDataElement.renderValue(dataElement)
                        )
                    } else {
                        Pair(
                            dataElementName,
                            Cbor.toDiagnostics(
                                dataElement, setOf(
                                    DiagnosticOption.PRETTY_PRINT,
                                    DiagnosticOption.EMBEDDED_CBOR,
                                    DiagnosticOption.BSTR_PRINT_LENGTH,
                                )
                            )
                        )
                    }
                    kvPairs.add(DocumentKeyValuePair(key, value, bitmap = bitmap))
                }
            }
            return DocumentData(infos, warnings, kvPairs)
        }
    }
}

private data class DocumentKeyValuePair(
    val key: String,
    val textValue: String,
    val bitmap: ImageBitmap? = null
)

private fun formatTime(instant: Instant): String {
    val tz = TimeZone.currentSystemDefault()
    val isoStr = instant.toLocalDateTime(tz).format(LocalDateTime.Formats.ISO)
    // Get rid of the middle 'T'
    return isoStr.substring(0, 10) + " " + isoStr.substring(11)
}
