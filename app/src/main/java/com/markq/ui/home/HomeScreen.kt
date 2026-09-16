package com.markq.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.markq.LocaleHelper
import com.markq.R
import com.markq.data.local.EntryWithAttachments
import com.markq.ui.appViewModel
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onAdd: () -> Unit,
    onSettings: () -> Unit,
    vm: HomeViewModel = appViewModel(),
) {
    val entries by vm.entries.collectAsStateWithLifecycle()
    val sync by vm.syncState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var pendingDelete by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(sync.error) {
        val err = sync.error
        if (err != null) snackbar.showSnackbar(err)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = vm::refresh, enabled = !sync.running) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.sync))
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_mark))
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (entries.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (sync.running) stringResource(R.string.syncing) else stringResource(R.string.empty_marks),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(entries, key = { it.entry.id }) { row ->
                    SwipeMarkRow(
                        row = row,
                        onComplete = { vm.complete(row.entry.id) },
                        onDeleteRequest = { pendingDelete = row.entry.id },
                    )
                }
            }
        }
    }

    val deleteId = pendingDelete
    if (deleteId != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.delete_title)) },
            text = { Text(stringResource(R.string.delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(deleteId)
                    pendingDelete = null
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeMarkRow(
    row: EntryWithAttachments,
    onComplete: () -> Unit,
    onDeleteRequest: () -> Unit,
) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    onComplete()
                    false
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    onDeleteRequest()
                    false
                }
                else -> false
            }
        },
    )
    SwipeToDismissBox(
        state = state,
        backgroundContent = {
            val target = state.targetValue
            val color by animateColorAsState(
                when (target) {
                    SwipeToDismissBoxValue.StartToEnd -> Color(0xFF0B6E4F)
                    SwipeToDismissBoxValue.EndToStart -> Color(0xFFB42318)
                    else -> Color.Transparent
                },
                label = "swipe-bg",
            )
            val align = when (target) {
                SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                else -> Alignment.Center
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(color, RoundedCornerShape(16.dp))
                    .padding(horizontal = 20.dp),
                contentAlignment = align,
            ) {
                when (target) {
                    SwipeToDismissBoxValue.StartToEnd -> Icon(
                        painterResource(R.drawable.ic_check),
                        contentDescription = stringResource(R.string.complete),
                        tint = Color.White,
                    )
                    SwipeToDismissBoxValue.EndToStart -> Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.delete),
                        tint = Color.White,
                    )
                    else -> Unit
                }
            }
        },
    ) {
        MarkCard(row = row)
    }
}

@Composable
private fun MarkCard(row: EntryWithAttachments) {
    val completed = row.entry.completed
    val textStyle: TextStyle = if (completed) {
        MaterialTheme.typography.bodyLarge.copy(
            textDecoration = TextDecoration.LineThrough,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        MaterialTheme.typography.bodyLarge
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (completed) 0.55f else 1f),
        colors = CardDefaults.cardColors(
            containerColor = if (completed) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                if (completed) {
                    Icon(
                        painterResource(R.drawable.ic_check),
                        contentDescription = stringResource(R.string.complete),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(end = 8.dp, top = 2.dp)
                            .size(18.dp),
                    )
                }
                Text(
                    text = row.entry.text.ifBlank { stringResource(R.string.no_text) },
                    style = textStyle,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                formatWhen(row.entry.occurredAt),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                attributionText(row),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val images = row.attachments.filter { it.kind == "image" && !it.localPath.isNullOrBlank() }
            val files = row.attachments.filter { it.kind != "image" }
            if (images.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    images.take(3).forEach { att ->
                        AsyncImage(
                            model = File(att.localPath!!),
                            contentDescription = att.name,
                            modifier = Modifier.size(64.dp),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
            }
            if (files.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    files.joinToString { it.name },
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun attributionText(row: EntryWithAttachments): String {
    val created = row.entry.createdBy
    return if (row.entry.completed) {
        val who = row.entry.completedBy?.takeIf { it.isNotBlank() }
        if (who != null) stringResource(R.string.attr_completed_by, created, who)
        else stringResource(R.string.attr_completed, created)
    } else {
        stringResource(R.string.attr_by, created)
    }
}

private val dateFmt: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
        .withLocale(LocaleHelper.appLocale)
        .withZone(ZoneId.systemDefault())

fun formatWhen(epochMs: Long): String = dateFmt.format(Instant.ofEpochMilli(epochMs))
