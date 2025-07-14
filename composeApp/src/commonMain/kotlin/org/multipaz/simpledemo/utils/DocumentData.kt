package org.multipaz.simpledemo.utils

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.toLocalDateTime
import org.multipaz.cbor.Bstr
import org.multipaz.cbor.Cbor
import org.multipaz.cbor.DiagnosticOption
import org.multipaz.compose.decodeImage
import org.multipaz.documenttype.DocumentAttributeType
import org.multipaz.documenttype.DocumentTypeRepository
import org.multipaz.mdoc.response.DeviceResponseParser
import org.multipaz.trustmanagement.LocalTrustManager
import org.multipaz.trustmanagement.OriginTrustPoint
import org.multipaz.trustmanagement.TrustManager
import org.multipaz.trustmanagement.TrustPoint
import org.multipaz.trustmanagement.VicalTrustManager
import org.multipaz.trustmanagement.X509CertTrustPoint
import org.multipaz.util.Logger

data class DocumentData(
    val infoTexts: List<String>,
    val warningTexts: List<String>,
    val kvPairs: List<DocumentKeyValuePair>
) {
    companion object {
        suspend fun fromMdocDeviceResponseDocument(
            document: DeviceResponseParser.Document,
            documentTypeRepository: DocumentTypeRepository,
            issuerTrustManager: TrustManager
        ): DocumentData {
            val infos = mutableListOf<String>()
            val warnings = mutableListOf<String>()
            val kvPairs = mutableListOf<DocumentKeyValuePair>()
            Logger.e(
                "vishnu",
                "fromMdocDeviceResponseDocument() called with: document = $document, " +
                        "issuerTrustManager = $issuerTrustManager"
            )
            Logger.e(
                "vishnu",
                "fromMdocDeviceResponseDocument: ${issuerTrustManager.getTrustPoints().size}"
            )
            issuerTrustManager.getTrustPoints().forEach {
                Logger.e("vishnu", "fromMdocDeviceResponseDocument() called ${it}")
                Logger.e(
                    "vishnu",
                    "fromMdocDeviceResponseDocument() called ${(it as X509CertTrustPoint).certificate}"
                )
                Logger.e(
                    "vishnu",
                    "fromMdocDeviceResponseDocument() called ${(it as X509CertTrustPoint).certificate.encodedCertificate}"
                )
                Logger.e(
                    "vishnu",
                    "fromMdocDeviceResponseDocument() called ${(it as X509CertTrustPoint).certificate.subject}"
                )
                Logger.e(
                    "vishnu",
                    "fromMdocDeviceResponseDocument() called ${(it as X509CertTrustPoint).certificate.toPem()}"
                )
                Logger.e("vishnu", "fromMdocDeviceResponseDocument() called ${it.metadata}")
                Logger.e("vishnu", "fromMdocDeviceResponseDocument() called ${it.identifier}")
            }
            if (document.issuerSignedAuthenticated) {
                val trustResult =
                    issuerTrustManager.verify(document.issuerCertificateChain.certificates)
                if (trustResult.isTrusted) {
                    val primaryTrustPoint = trustResult.trustPoints[0]
                    val trustPointInfo = buildTrustPointInfo(primaryTrustPoint)
                    infos.add(trustPointInfo)

                    if (trustResult.trustPoints.size > 1) {
                        infos.add("${trustResult.trustPoints.size} trust points matched this issuer")
                        for (i in 1 until trustResult.trustPoints.size) {
                            val additionalInfo =
                                buildTrustPointInfo(trustResult.trustPoints[i], isAdditional = true)
                            infos.add(additionalInfo)
                        }
                    }

                    if (trustResult.trustChain != null) {
                        val chainLength = trustResult.trustChain!!.certificates.size
                        infos.add("Certificate chain validated with $chainLength certificate(s)")
                    }
                } else {
                    warnings.add("Issuer is not in any trust list")

                    val issuerSubject =
                        document.issuerCertificateChain.certificates.firstOrNull()?.subject?.name
                    if (issuerSubject != null) {
                        warnings.add("Untrusted issuer: $issuerSubject")
                    }

                    if (trustResult.error != null) {
                        warnings.add("Trust verification error: ${trustResult.error!!.message}")
                    }
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
                    // Some DocTypes not known by [documentTypeRepository] - could be they are
                    // private or was just never added - may use namespaces from existing
                    // DocTypes... support that as well.
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

data class DocumentKeyValuePair(
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

/**
 * Build comprehensive trust point information string for display
 */
private fun buildTrustPointInfo(trustPoint: TrustPoint, isAdditional: Boolean = false): String {
    val prefix = if (isAdditional) "Additional trust point: " else "Trusted issuer: "
    val displayName = trustPoint.metadata.displayName
    val trustManagerType = when (trustPoint.trustManager) {
        is LocalTrustManager -> "Local Trust Store"
        is VicalTrustManager -> "VICAL Trust List"
        else -> "Trust Manager"
    }

    return when (trustPoint) {
        is X509CertTrustPoint -> {
            val certSubject = trustPoint.certificate.subject
            val certIssuer = trustPoint.certificate.issuer
            val validFrom = formatTime(trustPoint.certificate.validityNotBefore)
            val validUntil = formatTime(trustPoint.certificate.validityNotAfter)

            buildString {
                append(prefix)
                if (displayName != null) {
                    append("'$displayName' ")
                } else {
                    append("Certificate with subject '$certSubject' ")
                }
                append("(Source: $trustManagerType)")
                if (!isAdditional) {
                    append("\nCertificate valid from $validFrom to $validUntil")
                    if (certIssuer != certSubject) {
                        append("\nIssued by: $certIssuer")
                    }
                }
            }
        }

        is OriginTrustPoint -> {
            buildString {
                append(prefix)
                if (displayName != null) {
                    append("'$displayName' ")
                } else {
                    append("Origin '${trustPoint.origin}' ")
                }
                append("(Source: $trustManagerType)")
            }
        }
    }
}
