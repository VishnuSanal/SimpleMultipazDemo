package org.multipaz.simpledemo

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import org.multipaz.context.initializeApplication
import org.multipaz.prompt.AndroidPromptModel

class MainActivity : FragmentActivity() {
    val promptModel = AndroidPromptModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        initializeApplication(this.applicationContext)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            App(promptModel)
        }
    }
}