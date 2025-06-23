package org.multipaz.simpledemo

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.io.bytestring.ByteString
import org.jetbrains.compose.resources.getDrawableResourceBytes
import org.jetbrains.compose.resources.getSystemResourceEnvironment
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.multipaz.compose.prompt.PromptDialogs
import org.multipaz.prompt.PromptModel
import org.multipaz.simpledemo.ui.ActionButtons
import org.multipaz.simpledemo.ui.DocumentCard
import org.multipaz.simpledemo.viewmodel.DocumentViewModel
import simplemultipazdemo.composeapp.generated.resources.Res
import simplemultipazdemo.composeapp.generated.resources.driving_license_card_art

@Composable
@Preview
fun App(promptModel: PromptModel) {
    val viewModel = remember { DocumentViewModel() }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope { promptModel }

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
                modifier = Modifier.fillMaxWidth().padding(50.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ActionButtons(
                    coroutineScope = coroutineScope,
                    onCreateSecureArea = {
                        viewModel.createSecureArea(
                            onSuccess = { showToast("Created SecureArea successfully") },
                            onError = { showToast("Create SecureArea failed") }
                        )
                    },
                    onInitializeDocumentStore = {
                        viewModel.initializeDocumentStore(
                            onSuccess = { showToast("Initialized DocumentStore successfully") },
                            onError = { showToast("Initialize DocumentStore failed") }
                        )
                    },
                    onFetchDocuments = {
                        viewModel.fetchDocuments(
                            onSuccess = { showToast("Documents fetched successfully") },
                            onError = { showToast("Fetch documents failed") }
                        )
                    },
                    onCreateMdoc = {
                        val cardArt = ByteString(
                            getDrawableResourceBytes(
                                getSystemResourceEnvironment(),
                                Res.drawable.driving_license_card_art,
                            )
                        )
                        viewModel.createMdoc(
                            cardArt = cardArt,
                            onSuccess = { showToast("mDoc created successfully") },
                            onError = { showToast("Create MDOC failed") }
                        )
                    }
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    items(viewModel.documents) { document ->
                        DocumentCard(
                            document = document,
                            onShowQrCode = { showToast("WIP!") },
                            onDelete = {
                                coroutineScope.launch {
                                    viewModel.deleteDocument(
                                        documentId = document.identifier,
                                        onSuccess = { showToast("Document deleted successfully") },
                                        onError = { showToast("Delete Document failed") }
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}