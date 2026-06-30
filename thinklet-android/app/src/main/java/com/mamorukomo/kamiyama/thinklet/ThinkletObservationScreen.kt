package com.mamorukomo.kamiyama.thinklet

import android.Manifest
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(lifecycleOwner) {
        viewModel.bindCamera(context, lifecycleOwner)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF07130F))
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(modifier = Modifier.widthIn(max = 520.dp)) {
            Text(
                text = "KAMIYAMA CAPTURE",
                color = Color(0xFF63E6BE),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "THINKLETは撮影専用です。図鑑確認はスマホ側で行います。",
                color = Color(0xFFD9E5DC),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 6.dp)
            )
            Text(
                text = state.status,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 12.dp)
            )

            state.latestPayload?.let { payload ->
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "最新: ${payload.label} / ${payload.category}",
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

            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = state.isCameraReady && !state.isCapturing && !state.isSending,
                    onClick = { viewModel.captureObservation(context, sendAfterCapture = true) },
                ) {
                    Text(if (state.isCapturing) "撮影中" else "テスト撮影→送信")
                }
                Button(
                    enabled = state.latestPayload != null && !state.isSending,
                    onClick = { viewModel.sendObservationToSyncApi(context) },
                ) {
                    Text(if (state.isSending) "送信中" else "再送信")
                }
            }
            Text(
                text = "実運用: サイドボタンで撮影→同期API送信",
                color = Color(0xFFD9E5DC),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
    }
}

private fun Double.formatCoord(): String = String.format("%.5f", this)
