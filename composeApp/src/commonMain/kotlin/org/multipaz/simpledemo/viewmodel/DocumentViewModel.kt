package org.multipaz.simpledemo.viewmodel

import androidx.compose.runtime.mutableStateListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.encodeToByteString
import org.multipaz.asn1.ASN1Integer
import org.multipaz.crypto.Algorithm
import org.multipaz.crypto.Crypto
import org.multipaz.crypto.EcCurve
import org.multipaz.crypto.X500Name
import org.multipaz.crypto.X509CertChain
import org.multipaz.document.Document
import org.multipaz.document.DocumentStore
import org.multipaz.documenttype.DocumentTypeRepository
import org.multipaz.documenttype.knowntypes.DrivingLicense
import org.multipaz.mdoc.util.MdocUtil
import org.multipaz.securearea.CreateKeySettings
import org.multipaz.securearea.SecureArea
import org.multipaz.securearea.SecureAreaRepository
import org.multipaz.storage.Storage
import kotlin.time.Duration.Companion.days

class DocumentViewModel {
    private val _documents = mutableStateListOf<Document>()
    val documents: List<Document> get() = _documents.toList()

    lateinit var storage: Storage
    lateinit var secureArea: SecureArea
    lateinit var secureAreaRepository: SecureAreaRepository
    lateinit var documentTypeRepository: DocumentTypeRepository
    lateinit var documentStore: DocumentStore

    fun createSecureArea(onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        CoroutineScope(Dispatchers.Default).launch {
            try {
                storage = org.multipaz.util.Platform.getNonBackedUpStorage()
                secureArea = org.multipaz.util.Platform.getSecureArea(storage)
                secureAreaRepository = SecureAreaRepository.Builder().add(secureArea).build()
                onSuccess()
            } catch (e: Exception) {
                e.printStackTrace()
                onError(e)
            }
        }
    }

    fun initializeDocumentStore(onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        CoroutineScope(Dispatchers.Default).launch {
            try {
                documentTypeRepository = DocumentTypeRepository().apply {
                    addDocumentType(DrivingLicense.getDocumentType())
                }

                documentStore = org.multipaz.document.buildDocumentStore(
                    storage = storage, secureAreaRepository = secureAreaRepository
                ) {}

                onSuccess()
            } catch (e: Exception) {
                e.printStackTrace()
                onError(e)
            }
        }
    }

    fun fetchDocuments(onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        CoroutineScope(Dispatchers.Default).launch {
            try {
                for (documentId in documentStore.listDocuments()) {
                    try {
                        documentStore.lookupDocument(documentId)?.let { document ->
                            if (!_documents.contains(document))
                                _documents.add(document)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                onSuccess()
            } catch (e: Exception) {
                e.printStackTrace()
                onError(e)
            }
        }
    }

    fun createMdoc(cardArt: ByteString, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val document = documentStore.createDocument(
                    displayName = "Erika's Driving License",
                    typeDisplayName = "Utopia Driving License - ${
                        Clock.System.now().toEpochMilliseconds()
                    }",
                    cardArt = cardArt
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

                _documents.add(document)
                onSuccess()
            } catch (e: Exception) {
                e.printStackTrace()
                onError(e)
            }
        }
    }

    fun deleteDocument(documentId: String, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val documentToRemove = _documents.find { it.identifier == documentId }
                documentStore.deleteDocument(documentId)
                documentToRemove?.let { _documents.remove(it) }
                onSuccess()
            } catch (e: Exception) {
                e.printStackTrace()
                onError(e)
            }
        }
    }
}