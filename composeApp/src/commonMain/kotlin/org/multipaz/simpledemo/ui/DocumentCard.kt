package org.multipaz.simpledemo.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.multipaz.compose.decodeImage
import org.multipaz.document.Document

@Composable
fun DocumentCard(
    document: Document,
    onShowQrCode: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        document.metadata.cardArt?.let {
            Image(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                bitmap = decodeImage(it.toByteArray()),
                contentDescription = "Document Card Art"
            )
        }

        Text(
            modifier = Modifier.padding(8.dp),
            text = "Document: ${document.metadata.displayName}",
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = "Type: ${document.metadata.typeDisplayName}",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(8.dp),
        )

        Row {
            Button(
                onClick = { onShowQrCode() },
                modifier = Modifier.padding(16.dp)
            ) {
                Text("Show QR Code")
            }

            Button(
                onClick = { onDelete() },
                modifier = Modifier.padding(16.dp)
            ) {
                Text("Delete")
            }
        }
    }
}

@Composable
fun ActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.padding(vertical = 4.dp)
    ) {
        Text(text)
    }
}