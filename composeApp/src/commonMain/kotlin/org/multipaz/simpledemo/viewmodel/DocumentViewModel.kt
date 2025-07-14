package org.multipaz.simpledemo.viewmodel

import androidx.compose.runtime.mutableStateListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.encodeToByteString
import org.multipaz.asn1.ASN1Integer
import org.multipaz.cbor.Cbor
import org.multipaz.crypto.Algorithm
import org.multipaz.crypto.Crypto
import org.multipaz.crypto.EcCurve
import org.multipaz.crypto.EcPrivateKey
import org.multipaz.crypto.EcPublicKey
import org.multipaz.crypto.X500Name
import org.multipaz.crypto.X509Cert
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
import org.multipaz.storage.StorageTableSpec
import org.multipaz.trustmanagement.LocalTrustManager
import org.multipaz.trustmanagement.TrustManager
import org.multipaz.trustmanagement.TrustPointAlreadyExistsException
import org.multipaz.trustmanagement.TrustPointMetadata
import org.multipaz.util.Platform
import org.multipaz.util.fromHex
import kotlin.time.Duration.Companion.days

class DocumentViewModel {
    private val _documents = mutableStateListOf<Document>()
    val documents: List<Document> get() = _documents.toList()

    lateinit var storage: Storage
    lateinit var secureArea: SecureArea
    lateinit var secureAreaRepository: SecureAreaRepository
    lateinit var documentTypeRepository: DocumentTypeRepository
    lateinit var documentStore: DocumentStore

    lateinit var iacaKey: EcPrivateKey
    lateinit var iacaCert: X509Cert

    lateinit var readerKey: EcPrivateKey
    lateinit var readerCert: X509Cert
    lateinit var readerRootCert: X509Cert

    lateinit var readerTrustManager: LocalTrustManager
    lateinit var issuerTrustManager: TrustManager

    lateinit var dsKey: EcPrivateKey
    lateinit var dsCert: X509Cert

    fun createSecureArea(onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        CoroutineScope(Dispatchers.Default).launch {
            try {
                storage = org.multipaz.util.Platform.storage
                secureArea = org.multipaz.util.Platform.getSecureArea()
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
                val now = Clock.System.now()
                val signedAt = now
                val validFrom = now
                val validUntil = now + 365.days

                dsKey = Crypto.createEcPrivateKey(EcCurve.P256)
                dsCert = MdocUtil.generateDsCertificate(
                    iacaCert = iacaCert,
                    iacaKey = iacaKey,
                    dsKey = dsKey.publicKey,
                    subject = X500Name.fromName(name = "CN=Test DS Key"),
                    serial = ASN1Integer.fromRandom(numBits = 128),
                    validFrom = validFrom,
                    validUntil = validUntil
                )

                val document = documentStore.createDocument(
                    displayName = "Erika's Driving License",
                    typeDisplayName = "Utopia Driving License - ${
                        Clock.System.now().toEpochMilliseconds()
                    }",
                    cardArt = cardArt
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

    fun initReaderKeys(
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val keyStorage = storage.getTable(
                    StorageTableSpec(
                        name = "TestAppKeys",
                        supportPartitions = false,
                        supportExpiration = false
                    )
                )

                val now = Clock.System.now()
                val validFrom = now
                val validUntil = now + 365.days
                val certsValidFrom = validFrom
                val certsValidUntil = validUntil

                // Bundled root key and cert (replace PEMs with your actual values)
                val bundledReaderRootKey: EcPrivateKey by lazy {
                    val readerRootKeyPub = EcPublicKey.fromPem(
                        """-----BEGIN PUBLIC KEY-----
                            MHYwEAYHKoZIzj0CAQYFK4EEACIDYgAE81WfsnJnwApnLwMczciUbMXUrNJWWV1e
                            0e8+VRPbiO9w0peId2W5UYOgv0bCbu3gKlet50iZypKGkKu18K8Kru2x2zCrY6Tc
                            IPHKzX+y6EVKuYUll+iUU6gNMJ49NO/k
                            -----END PUBLIC KEY-----""".trimIndent().trim(),
                        EcCurve.P384
                    )

                    EcPrivateKey.fromPem(
                        """-----BEGIN PRIVATE KEY-----
                            ME4CAQAwEAYHKoZIzj0CAQYFK4EEACIENzA1AgEBBDCpXMxIk+bxGb8IjOZ4eGbg
                            iDEf0o9KxQiukTHQZ33GQ8vSbUzDISrgAyEehDM07+Q=
                            -----END PRIVATE KEY-----""".trimIndent().trim(),
                        readerRootKeyPub
                    )
                }
                val bundledReaderRootCert: X509Cert by lazy {
                    MdocUtil.generateReaderRootCertificate(
                        readerRootKey = bundledReaderRootKey,
                        subject = X500Name.fromName("CN=Vishnu's Simple Multipaz Demo Reader Root"),
                        serial = ASN1Integer.fromRandom(numBits = 128),
                        validFrom = certsValidFrom,
                        validUntil = certsValidUntil,
                        crlUrl = "https://vishnusanal.github.io/multipaz/crl"
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

                iacaKey = Crypto.createEcPrivateKey(EcCurve.P256)
                iacaCert = MdocUtil.generateIacaCertificate(
                    iacaKey = iacaKey,
                    subject = X500Name.fromName(name = "CN=Vishnu's Simple Multipaz Demo IACA Key"),
                    serial = ASN1Integer.fromRandom(numBits = 128),
                    validFrom = validFrom,
                    validUntil = validUntil,
                    issuerAltNameUrl = "https://vishnusanal.github.io/multipaz/iaca/issuer",
                    crlUrl = "https://vishnusanal.github.io/multipaz/iaca/crl"
                )

                onSuccess()
            } catch (e: Exception) {
                e.printStackTrace()
                onError(e)
            }
        }
    }

    fun initTrustManager(
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        CoroutineScope(Dispatchers.Default).launch {
            try {
                readerTrustManager = LocalTrustManager(
                    partitionId = "BuiltInTrustedReaders",
                    storage = Platform.storage,
                    identifier = "Built-in Trusted Readers"
                )

                if (readerTrustManager.getTrustPoints().isEmpty()) {
                    try {
                        readerTrustManager.addTrustPoint(
                            certificate = readerRootCert,
                            metadata = TrustPointMetadata(
                                displayName = "Vishnu's Simple Multipaz Demo",
                                privacyPolicyUrl = "https://vishnusanal.github.io"
                            )
                        )
                    } catch (e: TrustPointAlreadyExistsException) {
                        // Do nothing, it's possible our certificate is in the list above.
                    }
                    try {
                        readerTrustManager.addTrustPoint(
                            certificate = X509Cert(
                                "30820269308201efa0030201020210b7352f14308a2d40564006785270b0e7300a06082a8648ce3d0403033037310b300906035504060c0255533128302606035504030c1f76657269666965722e6d756c746970617a2e6f726720526561646572204341301e170d3235303631393232313633325a170d3330303631393232313633325a3037310b300906035504060c0255533128302606035504030c1f76657269666965722e6d756c746970617a2e6f7267205265616465722043413076301006072a8648ce3d020106052b81040022036200046baa02cc2f2b7c77f054e9907fcdd6c87110144f07acb2be371b2e7c90eb48580c5e3851bcfb777c88e533244069ff78636e54c7db5783edbc133cc1ff11bbabc3ff150f67392264c38710255743fee7cde7df6e55d7e9d5445d1bde559dcba8a381bf3081bc300e0603551d0f0101ff04040302010630120603551d130101ff040830060101ff02010030560603551d1f044f304d304ba049a047864568747470733a2f2f6769746875622e636f6d2f6f70656e77616c6c65742d666f756e646174696f6e2d6c6162732f6964656e746974792d63726564656e7469616c2f63726c301d0603551d0e04160414b18439852f4a6eeabfea62adbc51d081f7488729301f0603551d23041830168014b18439852f4a6eeabfea62adbc51d081f7488729300a06082a8648ce3d040303036800306502302a1f3bb0afdc31bcee73d3c5bf289245e76bd91a0fd1fb852b45fc75d3a98ba84430e6a91cbfc6b3f401c91382a43a64023100db22d2243644bb5188f2e0a102c0c167024fb6fe4a1d48ead55a6893af52367fb3cdbd66369aa689ecbeb5c84f063666".fromHex()
                            ),
                            metadata = TrustPointMetadata(
                                displayName = "Multipaz Verifier",
                                privacyPolicyUrl = "https://apps.multipaz.org"
                            )
                        )
                    } catch (e: TrustPointAlreadyExistsException) {
                        // Do nothing, it's possible our certificate is in the list above.
                    }
                }

                val builtInIssuerTrustManager = LocalTrustManager(
                    storage = Platform.storage,
                    partitionId = "BuiltInTrustedIssuers",
                    identifier = "Built-in Trusted Issuers"
                )
                if (builtInIssuerTrustManager.getTrustPoints().isEmpty()) {
                    builtInIssuerTrustManager.addTrustPoint(
                        certificate = iacaCert,
                        metadata = TrustPointMetadata(displayName = "Vishnu's Simple Multipaz Demo Issuer"),
                    )
                }
                issuerTrustManager = builtInIssuerTrustManager

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