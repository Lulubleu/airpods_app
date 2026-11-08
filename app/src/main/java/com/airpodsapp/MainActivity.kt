package com.airpodsapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.airpodsapp.bluetooth.AppleDevices
import com.airpodsapp.bluetooth.PodsPermissions
import com.airpodsapp.bluetooth.PodsRepository
import com.airpodsapp.service.PodsService
import com.airpodsapp.ui.home.HomeScreen
import com.airpodsapp.ui.theme.PodsTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PodsTheme {
                val context = LocalContext.current
                var granted by remember { mutableStateOf(PodsPermissions.hasBluetooth(context)) }
                val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
                    granted = PodsPermissions.hasBluetooth(context)
                    if (granted) startIfAlreadyConnected()
                }
                val state by PodsRepository.state.collectAsStateWithLifecycle()

                HomeScreen(
                    state = state,
                    hasPermission = granted,
                    onRequestPermission = { launcher.launch(PodsPermissions.all) },
                    onNoiseModeSelected = PodsRepository::setNoiseControl,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (PodsPermissions.hasBluetooth(this)) startIfAlreadyConnected()
    }

    /** The AirPods may have connected before the app was installed or granted permissions. */
    private fun startIfAlreadyConnected() {
        lifecycleScope.launch {
            val device = AppleDevices.findConnected(this@MainActivity) ?: return@launch
            PodsService.start(this@MainActivity, device)
        }
    }
}
