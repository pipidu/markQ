package com.markq.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.markq.ui.theme.LocalMarkQUiColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompactTopAppBar(
    title: String,
    subtitle: String? = null,
    onSubtitleClick: (() -> Unit)? = null,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    val ui = LocalMarkQUiColors.current
    TopAppBar(
        title = {
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = ui.onBar.copy(alpha = 0.88f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = if (onSubtitleClick != null) {
                            Modifier.clickable(onClick = onSubtitleClick)
                        } else {
                            Modifier
                        },
                    )
                }
            }
        },
        navigationIcon = navigationIcon,
        actions = actions,
        expandedHeight = if (subtitle.isNullOrBlank()) 48.dp else 56.dp,
        windowInsets = TopAppBarDefaults.windowInsets,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = ui.bar,
            scrolledContainerColor = ui.bar,
            navigationIconContentColor = ui.onBar,
            titleContentColor = ui.onBar,
            actionIconContentColor = ui.onBar,
        ),
    )
}
