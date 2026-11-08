package com.airpodsapp.ui.popup

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airpodsapp.MainActivity
import com.airpodsapp.bluetooth.PodsRepository
import com.airpodsapp.bluetooth.PodsState
import com.airpodsapp.protocol.PodsStatus
import com.airpodsapp.R
import com.airpodsapp.ui.components.MiniBattery
import com.airpodsapp.ui.components.PodPart
import com.airpodsapp.ui.components.hasShortStem
import com.airpodsapp.ui.theme.PodsTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Transparent activity showing a bottom sheet with the battery levels, opened from the
 * "connected" heads-up notification. It closes itself after a few seconds.
 */
class ConnectionPopupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PodsTheme {
                val state by PodsRepository.state.collectAsStateWithLifecycle()
                ConnectionSheet(
                    state = state,
                    onDismiss = ::finish,
                    onOpenApp = {
                        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        finish()
                    },
                )
            }
        }
    }

    private companion object {
        const val AUTO_DISMISS_MS = 8_000L
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun ConnectionSheet(state: PodsState, onDismiss: () -> Unit, onOpenApp: () -> Unit) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val scope = rememberCoroutineScope()
        val hide: (() -> Unit) -> Unit = { then -> scope.launch { sheetState.hide() }.invokeOnCompletion { then() } }

        LaunchedEffect(Unit) {
            delay(AUTO_DISMISS_MS)
            hide(onDismiss)
        }
        LaunchedEffect(state.isConnected) {
            if (!state.isConnected) hide(onDismiss)
        }

        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            shape = MaterialTheme.shapes.extraLarge,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Text(
                    text = state.deviceName ?: stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Batteries(state.status)
                Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = { hide(onDismiss) }, modifier = Modifier.weight(1f).height(48.dp)) {
                        Text(stringResource(R.string.popup_close))
                    }
                    Button(onClick = onOpenApp, modifier = Modifier.weight(1f).height(48.dp)) {
                        Text(stringResource(R.string.popup_open_app))
                    }
                }
            }
        }
    }

    @Composable
    private fun Batteries(status: PodsStatus?) {
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            when (status) {
                is PodsStatus.Stereo -> {
                    val short = status.model.hasShortStem
                    MiniBattery(stringResource(R.string.left), status.left, PodPart.LEFT, short)
                    MiniBattery(stringResource(R.string.right), status.right, PodPart.RIGHT, short)
                    MiniBattery(stringResource(R.string.case_label), status.case, PodPart.CASE)
                }
                is PodsStatus.Single -> MiniBattery(stringResource(R.string.battery), status.battery, PodPart.HEADSET)
                null -> Text(
                    stringResource(R.string.notification_waiting),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
