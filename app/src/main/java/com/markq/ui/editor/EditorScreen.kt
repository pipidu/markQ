package com.markq.ui.editor

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import com.markq.ui.CompactTopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.material3.AlertDialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.markq.LocaleHelper
import com.markq.R
import com.markq.core.MarkPlace
import com.markq.ui.ImageViewer
import com.markq.ui.MapsLauncher
import com.markq.ui.appViewModel
import com.markq.ui.theme.EntryColorPicker
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import androidx.core.content.FileProvider

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditorScreen(
    entryId: String? = null,
    templateId: String? = null,
    onDone: () -> Unit,
    vm: EditorViewModel = appViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<DraftAttachment?>(null) }
    var pendingCapturePath by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(entryId, templateId) { vm.load(entryId, templateId) }

    LaunchedEffect(state.loaded) {
        if (state.loaded) vm.prepareLocation(context)
    }

    LaunchedEffect(state.saved) {
        if (state.saved) onDone()
    }

    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        vm.onLocationPermission(context, granted)
    }

    LaunchedEffect(state.needLocationPermission) {
        if (state.needLocationPermission) {
            locationPermission.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(),
    ) { uris -> if (uris.isNotEmpty()) vm.addUris(context, uris, fromImagePicker = true) }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris -> if (uris.isNotEmpty()) vm.addUris(context, uris, fromImagePicker = false) }

    val takePicture = rememberLauncherForActivityResult(
        TakePictureToCache(),
    ) { success ->
        val path = pendingCapturePath
        pendingCapturePath = null
        val file = path?.let(::File)
        if (file != null) {
            runCatching {
                val uri = FileProvider.getUriForFile(
                    context,
                    CaptureUris.authority(context),
                    file,
                )
                CaptureUris.revoke(context, uri)
            }
        }
        if (success && file != null) {
            vm.addCameraCapture(context, file)
        } else {
            CaptureUris.deleteQuietly(file)
        }
    }

    fun launchCamera() {
        val (file, uri) = CaptureUris.createTempJpeg(context)
        pendingCapturePath = file.absolutePath
        runCatching { takePicture.launch(uri) }.onFailure {
            CaptureUris.revoke(context, uri)
            CaptureUris.deleteQuietly(file)
            pendingCapturePath = null
            vm.showError(context.getString(R.string.error_no_camera))
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CompactTopAppBar(
                title = stringResource(
                    if (state.entryId == null) R.string.new_mark else R.string.edit_mark,
                ),
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            OutlinedTextField(
                value = state.text,
                onValueChange = vm::setText,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.text)) },
                minLines = 4,
            )
            if (!state.createdBy.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.attr_by, state.createdBy!!),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.color_label), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            EntryColorPicker(selected = state.color, onSelect = vm::setColor)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.tags_label), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            if (state.tags.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.tags.forEach { tag ->
                        InputChip(
                            selected = false,
                            onClick = { vm.removeTag(tag) },
                            label = { Text(tag) },
                            trailingIcon = {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = stringResource(R.string.remove),
                                )
                            },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = state.tagDraft,
                    onValueChange = vm::setTagDraft,
                    modifier = Modifier.weight(1f),
                    label = { Text(stringResource(R.string.tag_add)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { vm.addTag() }),
                )
                OutlinedButton(
                    onClick = vm::addTag,
                    enabled = state.tagDraft.isNotBlank(),
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Text(stringResource(R.string.tag_add_action))
                }
            }
            Spacer(Modifier.height(12.dp))
            val whenLabel = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
                .withLocale(LocaleHelper.appLocale)
                .format(state.date.atTime(state.time))
            Text(stringResource(R.string.date_and_time), style = MaterialTheme.typography.labelLarge)
            Text(whenLabel, style = MaterialTheme.typography.bodyLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showDate = true }) { Text(stringResource(R.string.change_date)) }
                OutlinedButton(onClick = { showTime = true }) { Text(stringResource(R.string.change_time)) }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = state.includeLocation,
                    onCheckedChange = { vm.setIncludeLocation(context, it) },
                )
                Text(
                    stringResource(R.string.include_location),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.clickable { vm.setIncludeLocation(context, !state.includeLocation) },
                )
            }
            if (state.includeLocation) {
                val place = MarkPlace.formatOrNull(state.latitude, state.longitude, state.placeName)
                val canOpenMaps = place != null
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = if (canOpenMaps) {
                        Modifier.clickable {
                            MapsLauncher.open(context, state.latitude, state.longitude, state.placeName)
                        }
                    } else {
                        Modifier
                    },
                ) {
                    Icon(
                        Icons.Filled.Place,
                        contentDescription = if (canOpenMaps) stringResource(R.string.location_open_maps) else null,
                        tint = if (canOpenMaps) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        when {
                            state.locating && place == null -> stringResource(R.string.locating)
                            place != null -> place
                            else -> stringResource(R.string.location_unavailable)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (canOpenMaps) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = {
                    imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) { Text(stringResource(R.string.add_images)) }
                OutlinedButton(onClick = { launchCamera() }) {
                    Icon(
                        Icons.Filled.PhotoCamera,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.take_photo))
                }
                OutlinedButton(onClick = {
                    filePicker.launch(arrayOf("*/*"))
                }) { Text(stringResource(R.string.add_files)) }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = state.compressImages,
                    onCheckedChange = vm::setCompressImages,
                )
                Text(
                    stringResource(R.string.compress_images),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.clickable { vm.setCompressImages(!state.compressImages) },
                )
            }
            if (state.attachments.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                state.attachments.forEach { att ->
                    val isImage = att.mime.startsWith("image/") || att.fromImagePicker
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (isImage && att.uri != Uri.EMPTY) {
                            AsyncImage(
                                model = att.uri,
                                contentDescription = att.name,
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .size(64.dp)
                                    .clickable { viewing = att },
                                contentScale = ContentScale.Crop,
                            )
                        }
                        Text(att.name, modifier = Modifier.weight(1f))
                        TextButton(onClick = { vm.removeAttachment(att.key) }) { Text(stringResource(R.string.remove)) }
                    }
                }
            }
            if (state.error != null) {
                Spacer(Modifier.height(8.dp))
                Text(state.error!!, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = vm::save,
                enabled = !state.busy && !state.saved,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.busy) stringResource(R.string.saving) else stringResource(R.string.save))
            }
        }
    }

    if (showDate) {
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = state.date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let { millis ->
                        vm.setDate(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate())
                    }
                    showDate = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text(stringResource(R.string.cancel)) } },
        ) {
            DatePicker(state = picker)
        }
    }

    if (showTime) {
        val picker = rememberTimePickerState(
            initialHour = state.time.hour,
            initialMinute = state.time.minute,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showTime = false },
            confirmButton = {
                TextButton(onClick = {
                    vm.setTime(java.time.LocalTime.of(picker.hour, picker.minute))
                    showTime = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text(stringResource(R.string.cancel)) } },
            text = { TimePicker(state = picker) },
        )
    }

    val open = viewing
    if (open != null && open.uri != Uri.EMPTY) {
        ImageViewer(
            model = open.uri,
            contentDescription = open.name,
            onDismiss = { viewing = null },
        )
    }
}
