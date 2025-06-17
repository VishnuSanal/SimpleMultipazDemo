package org.multipaz.simpledemo

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.encodeToByteString
import org.jetbrains.compose.resources.getDrawableResourceBytes
import org.jetbrains.compose.resources.getSystemResourceEnvironment
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.multipaz.asn1.ASN1Integer
import org.multipaz.compose.decodeImage
import org.multipaz.compose.prompt.PromptDialogs
import org.multipaz.crypto.Algorithm
import org.multipaz.crypto.Crypto
import org.multipaz.crypto.EcCurve
import org.multipaz.crypto.X500Name
import org.multipaz.crypto.X509CertChain
import org.multipaz.document.Document
import org.multipaz.document.DocumentStore
import org.multipaz.document.buildDocumentStore
import org.multipaz.documenttype.DocumentTypeRepository
import org.multipaz.documenttype.knowntypes.DrivingLicense
import org.multipaz.mdoc.util.MdocUtil
import org.multipaz.prompt.PromptModel
import org.multipaz.securearea.CreateKeySettings
import org.multipaz.securearea.SecureArea
import org.multipaz.securearea.SecureAreaRepository
import org.multipaz.storage.Storage
import simplemultipazdemo.composeapp.generated.resources.Res
import simplemultipazdemo.composeapp.generated.resources.driving_license_card_art
import kotlin.time.Duration.Companion.days

private lateinit var snackbarHostState: SnackbarHostState

lateinit var storage: Storage
lateinit var secureArea: SecureArea
lateinit var secureAreaRepository: SecureAreaRepository

lateinit var documentTypeRepository: DocumentTypeRepository
lateinit var documentStore: DocumentStore

private val documents = mutableStateListOf<Document>()

private fun showToast(message: String) {
    println("vishnu: $message")
    CoroutineScope(Dispatchers.Main).launch {
        when (snackbarHostState.showSnackbar(
            message = message,
            actionLabel = "OK",
            duration = SnackbarDuration.Short,
        )) {
            SnackbarResult.Dismissed -> {
            }

            SnackbarResult.ActionPerformed -> {
            }
        }
    }
}

@Composable
@Preview
fun App(promptModel: PromptModel) {
    MaterialTheme {
        snackbarHostState = remember { SnackbarHostState() }

        PromptDialogs(promptModel)

        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        ) { innerPadding ->

            val coroutineScope = rememberCoroutineScope { promptModel }

            Column(
                modifier = Modifier.fillMaxWidth().padding(50.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {

                Button(onClick = {
                    coroutineScope.launch {
                        try {

                            storage = org.multipaz.util.Platform.getNonBackedUpStorage()
                            secureArea = org.multipaz.util.Platform.getSecureArea(storage)
                            secureAreaRepository =
                                SecureAreaRepository.Builder().add(secureArea).build()

                            showToast("Created SecureArea successfully")
                        } catch (e: Exception) {
                            e.printStackTrace()
                            showToast("Create SecureArea")
                        }
                    }
                }) {
                    Text("Create SecureArea")
                }

                Button(onClick = {
                    coroutineScope.launch {
                        try {

                            documentTypeRepository = DocumentTypeRepository().apply {
                                addDocumentType(DrivingLicense.getDocumentType())
                            }

                            documentStore = buildDocumentStore(
                                storage = storage, secureAreaRepository = secureAreaRepository
                            ) {}

                            showToast("Initialized DocumentStore successfully")

                        } catch (e: Exception) {
                            e.printStackTrace()
                            showToast("Initialize DocumentStore failed")
                        }
                    }
                }) {
                    Text("Initialize DocumentStore")
                }

                Button(onClick = {
                    coroutineScope.launch {
                        try {

                            for (documentId in documentStore.listDocuments()) {
                                try {
                                    documentStore.lookupDocument(documentId).let { document ->
                                        if (document != null && !documents.contains(document))
                                            documents.add(document)
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    showToast("Failed to fetch credential infos for $documentId")
                                }
                            }

                            showToast("Documents fetched successfully")

                        } catch (e: Exception) {
                            e.printStackTrace()
                            showToast("Initialize DocumentStore failed")
                        }
                    }
                }) {
                    Text("Fetch mDocs from DocumentStore")
                }

                Button(onClick = {
                    coroutineScope.launch {
                        try {

                            val document = documentStore.createDocument(
                                displayName = "Erika's Driving License",
                                typeDisplayName = "Utopia Driving License",
                                cardArt = ByteString(
                                    getDrawableResourceBytes(
                                        getSystemResourceEnvironment(),
                                        Res.drawable.driving_license_card_art,
                                    )
                                ),
//                                issuerLogo = null,
//                                other = null
                            )

                            val now = Clock.System.now()
                            val signedAt = now
                            val validFrom = now
                            val validUntil = now + 365.days

                            val iacaKey = Crypto.createEcPrivateKey(EcCurve.P256)
                            val iacaCert = MdocUtil.generateIacaCertificate(
                                iacaKey = iacaKey,
                                subject = X500Name.fromName(name = "CN=Test IACA Key"),
                                serial = ASN1Integer.fromRandom(numBits = 128),
                                validFrom = validFrom,
                                validUntil = validUntil,
                                issuerAltNameUrl = "https://issuer.example.com",
                                crlUrl = "https://issuer.example.com/crl"
                            )

                            val dsKey = Crypto.createEcPrivateKey(EcCurve.P256)
                            val dsCert = MdocUtil.generateDsCertificate(
                                iacaCert = iacaCert,
                                iacaKey = iacaKey,
                                dsKey = dsKey.publicKey,
                                subject = X500Name.fromName(name = "CN=Test DS Key"),
                                serial = ASN1Integer.fromRandom(numBits = 128),
                                validFrom = validFrom,
                                validUntil = validUntil
                            )

                            val mdocCredential =
                                DrivingLicense.getDocumentType().createMdocCredentialWithSampleData(
                                    document = document,
                                    secureArea = secureArea,
                                    createKeySettings = CreateKeySettings(
                                        algorithm = Algorithm.ESP256,
                                        nonce = "Challenge".encodeToByteString(),
                                        userAuthenticationRequired = true
                                    ),
                                    dsKey = dsKey,
                                    dsCertChain = X509CertChain(listOf(dsCert)),
                                    signedAt = signedAt,
                                    validFrom = validFrom,
                                    validUntil = validUntil,
                                )

                            documents.add(document)
                            showToast("mDoc created successfully")

                        } catch (e: Exception) {
                            e.printStackTrace()
                            showToast("Create MDOC failed")
                        }
                    }
                }) {
                    Text("Create mDoc")
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    items(documents.toList()) { documentInfo ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp, horizontal = 16.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                        ) {

                            documentInfo.metadata.cardArt?.let {
                                Image(
                                    modifier = Modifier
                                        .fillMaxWidth(0.9f)
                                        .padding(16.dp),
                                    bitmap = decodeImage(it.toByteArray()),
                                    contentDescription = "Document Card Art"
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {

                                Column(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = "Document: ${documentInfo.metadata.displayName}",
                                        style = MaterialTheme.typography.headlineSmall
                                    )
                                    Text(
                                        text = "Type: ${documentInfo.metadata.typeDisplayName}",
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Button(
                                        onClick = {
                                            coroutineScope.launch {
                                                try {
                                                    showToast("WIP!")
                                                } catch (e: Exception) {
                                                    e.printStackTrace()
                                                    showToast("QR Display failed")
                                                }
                                            }
                                        },
                                        modifier = Modifier.padding(start = 16.dp)
                                    ) {
                                        Text("Show QR Code")
                                    }

                                    Button(
                                        onClick = {
                                            coroutineScope.launch {
                                                try {
                                                    documentStore.deleteDocument(documentInfo.identifier)
                                                    documents.remove(documentInfo)
                                                    showToast("Document deleted successfully")
                                                } catch (e: Exception) {
                                                    e.printStackTrace()
                                                    showToast("Delete Document failed")
                                                }
                                            }
                                        },
                                        modifier = Modifier.padding(start = 16.dp)
                                    ) {
                                        Text("Delete")
                                    }
                                }
                            }
                        }
                    }
                }

            }
        }
    }
}