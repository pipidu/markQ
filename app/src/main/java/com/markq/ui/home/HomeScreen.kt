package com.markq.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NoteAlt
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
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
import com.markq.core.MarkColor
import com.markq.core.MarkListFilter
import com.markq.core.MarkPlace
import com.markq.core.MarkTags
import com.markq.core.SyncErrors
import com.markq.data.local.EntryWithAttachments
import com.markq.ui.CompactTopAppBar
import com.markq.ui.ImageViewer
import com.markq.ui.MapsAppChooser
import com.markq.ui.MapsNavTarget
import com.markq.ui.appViewModel
import com.markq.ui.theme.LocalMarkQUiColors
import com.markq.ui.theme.exclusiveHorizontalScroll
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onAdd: () -> Unit,
    onTemplates: () -> Unit,
    onOpen: (String) -> Unit,
    onSettings: () -> Unit,
    vm: HomeViewModel = appViewModel(),
) {
    val entries by vm.entries.collectAsStateWithLifecycle()
    val hasAnyMarks by vm.hasAnyMarks.collectAsStateWithLifecycle()
    val hasCompleted by vm.hasCompleted.collectAsStateWithLifecycle()
    val availableTags by vm.availableTags.collectAsStateWithLifecycle()
    val listFilter by vm.listFilter.collectAsStateWithLifecycle()
    val pulling by vm.pulling.collectAsStateWithLifecycle()
    val sync by vm.syncState.collectAsStateWithLifecycle()
    val ui = LocalMarkQUiColors.current
    val snackbar = remember { SnackbarHostState() }
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    var viewing by remember { mutableStateOf<Pair<File, String>?>(null) }
    var mapsTarget by remember { mutableStateOf<MapsNavTarget?>(null) }

    LaunchedEffect(sync.error) {
        val err = sync.error
        if (err != null && !SyncErrors.isSilent(err)) snackbar.showSnackbar(err)
    }

    Scaffold(
        topBar = {
            CompactTopAppBar(
                title = stringResource(R.string.app_name),
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
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SmallFloatingActionButton(
                    onClick = onTemplates,
                    containerColor = ui.fab,
                    contentColor = ui.onFab,
                    elevation = FloatingActionButtonDefaults.elevation(
                        defaultElevation = 6.dp,
                        pressedElevation = 8.dp,
                    ),
                ) {
                    Icon(
                        Icons.Filled.NoteAlt,
                        contentDescription = stringResource(R.string.from_template),
                    )
                }
                FloatingActionButton(
                    onClick = onAdd,
                    containerColor = ui.fab,
                    contentColor = ui.onFab,
                    elevation = FloatingActionButtonDefaults.elevation(
                        defaultElevation = 6.dp,
                        pressedElevation = 8.dp,
                    ),
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_mark))
                }
            }
        },
        containerColor = ui.background,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            TagFilterRow(
                tags = availableTags,
                selected = listFilter,
                onSelect = vm::setListFilter,
            )
            PullToRefreshBox(
                isRefreshing = pulling,
                onRefresh = { vm.refresh(fromPull = true) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                if (entries.isEmpty()) {
                    Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            when {
                                sync.running && !hasAnyMarks -> stringResource(R.string.syncing)
                                listFilter is MarkListFilter.Completed -> stringResource(R.string.empty_completed)
                                listFilter is MarkListFilter.Tag -> stringResource(R.string.empty_tag_filter)
                                hasCompleted -> stringResource(R.string.empty_active_marks)
                                else -> stringResource(R.string.empty_marks)
                            },
                            color = ui.onBackground.copy(alpha = 0.7f),
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(entries, key = { it.entry.id }) { row ->
                            SwipeMarkRow(
                                row = row,
                                onToggleComplete = { vm.complete(row.entry.id) },
                                onDeleteRequest = { pendingDelete = row.entry.id },
                                onOpen = { onOpen(row.entry.id) },
                                onOpenImage = { file, name -> viewing = file to name },
                                onOpenMaps = { lat, lng, name ->
                                    mapsTarget = MapsNavTarget(lat, lng, name)
                                },
                            )
                        }
                    }
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

    val openImage = viewing
    if (openImage != null) {
        ImageViewer(
            model = openImage.first,
            contentDescription = openImage.second,
            onDismiss = { viewing = null },
        )
    }

    MapsAppChooser(
        target = mapsTarget,
        onDismiss = { mapsTarget = null },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeMarkRow(
    row: EntryWithAttachments,
    onToggleComplete: () -> Unit,
    onDeleteRequest: () -> Unit,
    onOpen: () -> Unit,
    onOpenImage: (File, String) -> Unit,
    onOpenMaps: (Double, Double, String?) -> Unit,
) {
    val density = LocalDensity.current
    val minDismissPx = with(density) { 160.dp.toPx() }
    val stateHolder = remember { arrayOfNulls<androidx.compose.material3.SwipeToDismissBoxState>(1) }
    val state = rememberSwipeToDismissBoxState(
        positionalThreshold = { distance ->
            maxOf(distance * 0.55f, minDismissPx).coerceAtMost(distance * 0.8f)
        },
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.Settled) return@rememberSwipeToDismissBoxState true
            val travelled = abs(stateHolder[0]?.requireOffset() ?: 0f)
            travelled >= minDismissPx
        },
    )
    stateHolder[0] = state
    LaunchedEffect(state.currentValue, row.entry.id) {
        when (state.currentValue) {
            SwipeToDismissBoxValue.StartToEnd -> {
                onToggleComplete()
                state.snapTo(SwipeToDismissBoxValue.Settled)
            }
            SwipeToDismissBoxValue.EndToStart -> {
                onDeleteRequest()
                state.snapTo(SwipeToDismissBoxValue.Settled)
            }
            else -> Unit
        }
    }
    val completed = row.entry.completed
    var rowWidth by remember { mutableFloatStateOf(1f) }
    SwipeToDismissBox(
        state = state,
        modifier = Modifier.onSizeChanged { rowWidth = it.width.toFloat().coerceAtLeast(1f) },
        backgroundContent = {
            val offset = runCatching { state.requireOffset() }.getOrDefault(0f)
            val fraction = (offset / rowWidth).coerceIn(-1f, 1f)
            val revealingComplete = fraction > 0.02f
            val revealingDelete = fraction < -0.02f
            val color by animateColorAsState(
                when {
                    revealingComplete -> Color(0xFF0B6E4F)
                    revealingDelete -> Color(0xFFB42318)
                    else -> Color.Transparent
                },
                label = "swipe-bg",
            )
            val align = when {
                revealingComplete -> Alignment.CenterStart
                revealingDelete -> Alignment.CenterEnd
                else -> Alignment.Center
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(color)
                    .padding(horizontal = 20.dp),
                contentAlignment = align,
            ) {
                if (abs(fraction) > 0.12f) {
                    if (revealingComplete) {
                        if (completed) {
                            Icon(
                                Icons.AutoMirrored.Filled.Undo,
                                contentDescription = stringResource(R.string.uncomplete),
                                tint = Color.White,
                            )
                        } else {
                            Icon(
                                painterResource(R.drawable.ic_check),
                                contentDescription = stringResource(R.string.complete),
                                tint = Color.White,
                            )
                        }
                    } else if (revealingDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.delete),
                            tint = Color.White,
                        )
                    }
                }
            }
        },
    ) {
        MarkCard(row = row, onOpen = onOpen, onOpenImage = onOpenImage, onOpenMaps = onOpenMaps)
    }
}

@Composable
private fun MarkCard(
    row: EntryWithAttachments,
    onOpen: () -> Unit,
    onOpenImage: (File, String) -> Unit,
    onOpenMaps: (Double, Double, String?) -> Unit,
) {
    val completed = row.entry.completed
    val ui = LocalMarkQUiColors.current
    val accent = MarkColor.parseArgb(row.entry.color)?.let { Color(it.toInt()) }
    val surface = Color.White
    val container = if (accent != null) {
        accent.copy(alpha = if (completed) 0.12f else 0.22f).compositeOver(surface)
    } else {
        surface
    }
    val textStyle: TextStyle = if (completed) {
        MaterialTheme.typography.bodyLarge.copy(
            textDecoration = TextDecoration.LineThrough,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        MaterialTheme.typography.bodyLarge
    }
    val borderColor = when {
        accent == null -> ui.cardBorder
        completed -> accent.copy(alpha = 0.55f)
        else -> accent
    }
    Card(
        onClick = onOpen,
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (completed) 0.72f else 1f),
        colors = CardDefaults.cardColors(containerColor = container),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
        border = BorderStroke(1.5.dp, borderColor),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(
                Modifier
                    .width(8.dp)
                    .fillMaxHeight()
                    .background(accent ?: MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
            )
            Column(Modifier.padding(16.dp).weight(1f)) {
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
                val place = MarkPlace.formatOrNull(
                    row.entry.latitude,
                    row.entry.longitude,
                    row.entry.placeName,
                )
                if (place != null) {
                    Spacer(Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable {
                            val lat = row.entry.latitude
                            val lng = row.entry.longitude
                            if (lat != null && lng != null) {
                                onOpenMaps(lat, lng, row.entry.placeName)
                            }
                        },
                    ) {
                        Icon(
                            Icons.Filled.Place,
                            contentDescription = stringResource(R.string.location_open_maps),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            place,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
                val tags = remember(row.entry.tags) { MarkTags.decode(row.entry.tags) }
                if (tags.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    ) {
                        tags.forEach { tag ->
                            Text(
                                tag,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0x140B6E4F))
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                val images = row.attachments.filter { it.kind == "image" && !it.localPath.isNullOrBlank() }
                val files = row.attachments.filter { it.kind != "image" }
                if (images.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        images.take(3).forEach { att ->
                            AsyncImage(
                                model = File(att.localPath!!),
                                contentDescription = att.name,
                                modifier = Modifier
                                    .size(64.dp)
                                    .clickable { onOpenImage(File(att.localPath!!), att.name) },
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

@Composable
private fun TagFilterRow(
    tags: List<String>,
    selected: MarkListFilter,
    onSelect: (MarkListFilter) -> Unit,
) {
    val scroll = rememberScrollState()
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .then(Modifier.exclusiveHorizontalScroll(scroll)),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = selected is MarkListFilter.All,
            onClick = { onSelect(MarkListFilter.All) },
            label = { Text(stringResource(R.string.tags_all)) },
        )
        tags.forEach { tag ->
            val active = selected is MarkListFilter.Tag &&
                selected.name.equals(tag, ignoreCase = true)
            FilterChip(
                selected = active,
                onClick = { onSelect(if (active) MarkListFilter.All else MarkListFilter.Tag(tag)) },
                label = { Text(tag) },
            )
        }
        FilterChip(
            selected = selected is MarkListFilter.Completed,
            onClick = { onSelect(MarkListFilter.Completed) },
            label = { Text(stringResource(R.string.filter_completed)) },
        )
    }
}
