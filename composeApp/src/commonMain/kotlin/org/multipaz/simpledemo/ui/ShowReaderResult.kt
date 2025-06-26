package org.multipaz.simpledemo.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.multipaz.compose.cards.InfoCard
import org.multipaz.compose.cards.WarningCard
import org.multipaz.crypto.EcPrivateKey
import org.multipaz.mdoc.response.DeviceResponseParser
import org.multipaz.simpledemo.utils.DocumentData
import org.multipaz.simpledemo.utils.DocumentKeyValuePair
import org.multipaz.simpledemo.viewmodel.DocumentViewModel
import org.multipaz.trustmanagement.TrustManager

@Composable
fun ShowReaderResult(
    readerMostRecentDeviceResponse: MutableState<ByteArray?>,
    readerSessionTranscript: MutableState<ByteArray?>,
    eReaderKey: EcPrivateKey,
    viewModel: DocumentViewModel
) {
    val deviceResponse = readerMostRecentDeviceResponse.value
    if (deviceResponse == null || deviceResponse.isEmpty()) {
        Text(
            text = "Waiting for data",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
        )
    } else {
        val parser = DeviceResponseParser(
            encodedDeviceResponse = deviceResponse,
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