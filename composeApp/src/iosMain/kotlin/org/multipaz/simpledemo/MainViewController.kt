package org.multipaz.samples.wallet.cmp

import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.window.ComposeUIViewController
import org.multipaz.prompt.PromptModel
import org.multipaz.simpledemo.App

fun MainViewController() = ComposeUIViewController {
    val coroutineScope = rememberCoroutineScope()

    // todo: not tested. cannot use IosPromptModel() here because it won't build for me on linux
    App(PromptModel.get(coroutineScope.coroutineContext))
}