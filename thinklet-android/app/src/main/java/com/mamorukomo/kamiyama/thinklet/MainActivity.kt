package com.mamorukomo.kamiyama.thinklet

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: ThinkletObservationViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface {
                    ThinkletObservationScreen(viewModel = viewModel)
                }
            }
        }
        handleAutomationIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAutomationIntent(intent)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (viewModel.handleThinkletButton(event, this)) {
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (viewModel.handleThinkletButton(event, this)) {
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun handleAutomationIntent(intent: Intent?) {
        if (intent?.action != ACTION_CAPTURE_AND_SYNC) {
            return
        }
        lifecycleScope.launch {
            delay(1200)
            viewModel.captureObservation(this@MainActivity, sendAfterCapture = true)
        }
    }

    companion object {
        const val ACTION_CAPTURE_AND_SYNC = "com.mamorukomo.kamiyama.thinklet.CAPTURE_AND_SYNC"
    }
}
