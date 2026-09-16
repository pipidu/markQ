package com.markq.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.markq.R

data class MapsNavTarget(
    val latitude: Double,
    val longitude: Double,
    val placeName: String?,
)

@Composable
fun MapsAppChooser(
    target: MapsNavTarget?,
    onDismiss: () -> Unit,
) {
    if (target == null) return
    val context = LocalContext.current
    var missingRes by remember(target) { mutableStateOf<Int?>(null) }

    fun pick(app: MapApp) {
        when (MapsLauncher.open(context, app, target.latitude, target.longitude, target.placeName)) {
            is MapLaunch.Missing -> missingRes = app.missingMessageRes
            MapLaunch.Opened, MapLaunch.NoLocation -> onDismiss()
        }
    }

    val missing = missingRes
    if (missing != null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.location_open_maps)) },
            text = { Text(stringResource(missing)) },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) }
            },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.maps_choose)) },
        text = {
            Column {
                TextButton(
                    onClick = { pick(MapApp.Baidu) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(R.string.maps_baidu),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                TextButton(
                    onClick = { pick(MapApp.Amap) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(R.string.maps_amap),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
