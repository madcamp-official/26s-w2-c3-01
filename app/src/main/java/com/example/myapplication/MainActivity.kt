package com.example.myapplication

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.example.myapplication.core.model.SharingState
import com.example.myapplication.service.SharingForegroundService
import com.example.myapplication.ui.MelodyViewModel
import com.example.myapplication.ui.MelodyBubbleApp
import com.example.myapplication.ui.theme.MelodyBubbleTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MelodyViewModel by lazy(LazyThreadSafetyMode.NONE) {
        ViewModelProvider(this)[MelodyViewModel::class.java]
    }
    private var receiverRegistered = false

    private val sharingStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == SharingForegroundService.ACTION_SHARING_STATE_CHANGED) {
                if (intent.getBooleanExtra(SharingForegroundService.EXTRA_SHARING_ACTIVE, false)) {
                    viewModel.startSharing()
                } else {
                    viewModel.stopSharing()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        setContent {
            MelodyBubbleTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground
                ) {
                    MelodyBubbleApp(viewModel = viewModel)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (!receiverRegistered) {
            ContextCompat.registerReceiver(
                this,
                sharingStateReceiver,
                IntentFilter(SharingForegroundService.ACTION_SHARING_STATE_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            receiverRegistered = true
        }
    }

    override fun onResume() {
        super.onResume()
        reconcileSharingService()
    }

    override fun onStop() {
        if (receiverRegistered) {
            unregisterReceiver(sharingStateReceiver)
            receiverRegistered = false
        }
        super.onStop()
    }

    private fun reconcileSharingService() {
        val serviceActive = getSharedPreferences(
            SharingForegroundService.PREFERENCES_NAME,
            MODE_PRIVATE
        ).getBoolean(SharingForegroundService.KEY_SHARING_ACTIVE, false)

        when {
            serviceActive && viewModel.uiState.value.sharingState != SharingState.ACTIVE -> {
                viewModel.startSharing()
            }
            !serviceActive && viewModel.uiState.value.sharingState == SharingState.ACTIVE -> {
                viewModel.stopSharing()
            }
        }
    }

}
