package org.multipaz.simpledemo.utils

import androidx.compose.runtime.MutableState
import kotlinx.coroutines.launch
import kotlinx.io.bytestring.ByteString
import org.multipaz.cbor.Simple
import org.multipaz.crypto.Crypto
import org.multipaz.crypto.EcCurve
import org.multipaz.crypto.EcPrivateKey
import org.multipaz.crypto.X509Cert
import org.multipaz.mdoc.connectionmethod.MdocConnectionMethod
import org.multipaz.mdoc.connectionmethod.MdocConnectionMethodBle
import org.multipaz.mdoc.engagement.EngagementGenerator
import org.multipaz.mdoc.engagement.EngagementParser
import org.multipaz.mdoc.role.MdocRole
import org.multipaz.mdoc.sessionencryption.SessionEncryption
import org.multipaz.mdoc.transport.MdocTransportFactory
import org.multipaz.mdoc.transport.MdocTransportOptions
import org.multipaz.mdoc.transport.advertise
import org.multipaz.mdoc.transport.waitForConnection
import org.multipaz.models.presentment.MdocPresentmentMechanism
import org.multipaz.models.presentment.PresentmentModel
import org.multipaz.simpledemo.viewmodel.DocumentViewModel
import org.multipaz.util.Constants
import org.multipaz.util.UUID

object PrsentmentUtils {
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
                eSenderKey = eDeviceKey.publicKey,
                version = EngagementGenerator.ENGAGEMENT_VERSION_1_0
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

    suspend fun doReaderFlow(
        encodedDeviceEngagement: ByteString,
        showToast: (String) -> Unit,
        viewModel: DocumentViewModel,
        readerKey: EcPrivateKey,
        readerCert: X509Cert,
        readerRootCert: X509Cert,
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

        val encodedSessionTranscript = EncodingUtils.generateEncodedSessionTranscript(
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

        val selectedRequest = EncodingUtils.provisionedDocumentTypes.first().cannedRequests.first()
        val encodedDeviceRequest = EncodingUtils.generateEncodedDeviceRequest(
            request = selectedRequest,
            encodedSessionTranscript = encodedSessionTranscript,
            readerKey = readerKey,
            readerCert = readerCert,
            readerRootCert = readerRootCert
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
                if (status == Constants.SESSION_DATA_STATUS_SESSION_TERMINATION)
                    showToast("Session termination received: $message $status")
                readerMostRecentDeviceResponse.value = message
                readerSessionTranscript.value = encodedSessionTranscript
                eReaderKeyState.value = eReaderKey
                transport.sendMessage(SessionEncryption.encodeStatus(Constants.SESSION_DATA_STATUS_SESSION_TERMINATION))
                transport.close()
                break
            }
        } finally {
            transport.close()
        }
    }

}