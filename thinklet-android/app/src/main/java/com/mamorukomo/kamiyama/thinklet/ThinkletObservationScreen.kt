package com.mamorukomo.kamiyama.thinklet

import android.Manifest
import androidx.camera.compose.CameraXViewfinder
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ThinkletObservationScreen(viewModel: ThinkletObservationViewModel) {
    val permissions = rememberMultiplePermissionsState(
        listOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
    )
    val hasCameraPermission = permissions.permissions.any { permission ->
        permission.permission == Manifest.permission.CAMERA && permission.status.isGranted
    }

    if (hasCameraPermission) {
        ThinkletCameraContent(viewModel = viewModel)
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "神山図鑑 Thinklet",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text("カメラと位置情報を許可すると、サイドボタンで観察を記録できます。")
            Spacer(modifier = Modifier.height(20.dp))
            Button(onClick = { permissions.launchMultiplePermissionRequest() }) {
                Text("権限を許可")
            }
        }
    }
}

@Composable
private fun ThinkletCameraContent(viewModel: ThinkletObservationViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val surfaceRequest by viewModel.surfaceRequest.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(lifecycleOwner) {
        viewModel.bindCamera(context, lifecycleOwner)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        surfaceRequest?.let { request ->
            CameraXViewfinder(
                surfaceRequest = request,
                modifier = Modifier.fillMaxSize()
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color(0xCC122018))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(modifier = Modifier.widthIn(max = 520.dp)) {
                Text(
                    text = "神山図鑑 Thinklet",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = state.status,
                    color = Color(0xFFD9E5DC),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )

                state.latestPayload?.let { payload ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "候補: ${payload.label} / ${payload.category}",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                    val locationText = if (payload.latitude != null && payload.longitude != null) {
                        "${payload.latitude.formatCoord()}, ${payload.longitude.formatCoord()}"
                    } else {
                        "位置情報なし"
                    }
                    Text(
                        text = locationText,
                        color = Color(0xFFD9E5DC),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = state.isCameraReady && !state.isCapturing,
                        onClick = { viewModel.captureObservation(context) },
                    ) {
                        Text(if (state.isCapturing) "撮影中" else "撮影")
                    }
                    Button(
                        enabled = state.latestPayload != null,
                        onClick = { viewModel.openWebApp(context) },
                    ) {
                        Text("Webへ送る")
                    }
                }
                Text(
                    text = "サイドボタンでも撮影できます",
                    color = Color(0xFFD9E5DC),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

private fun Double.formatCoord(): String = String.format("%.5f", this)
