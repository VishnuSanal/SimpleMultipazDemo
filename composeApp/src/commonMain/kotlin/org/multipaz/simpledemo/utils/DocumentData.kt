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
import org.multipaz.trustmanagement.TrustManager
import org.multipaz.util.Logger

data class DocumentData(
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
