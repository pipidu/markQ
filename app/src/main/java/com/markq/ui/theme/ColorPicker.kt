package com.markq.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.markq.R
import com.markq.core.MarkColor
import com.markq.core.UiThemeDefaults

@Composable
fun ThemeColorPicker(
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var hexInput by remember(selected) { mutableStateOf(selected) }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UiThemeDefaults.SWATCHES.forEach { hex ->
                val argb = MarkColor.parseArgb(hex) ?: return@forEach
                ColorDot(
                    color = Color(argb.toInt()),
                    selected = selected.equals(hex, ignoreCase = true),
                    onClick = { onSelect(hex) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = hexInput,
            onValueChange = { raw ->
                hexInput = raw
                MarkColor.normalize(raw)?.let(onSelect)
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.theme_hex_hint)) },
            singleLine = true,
        )
    }
}

@Composable
fun EntryColorPicker(
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ColorDot(
            color = Color.White,
            selected = selected.isNullOrBlank(),
            onClick = { onSelect(null) },
        )
        MarkColor.PRESETS.forEach { hex ->
            val argb = MarkColor.parseArgb(hex) ?: return@forEach
            ColorDot(
                color = Color(argb.toInt()),
                selected = selected.equals(hex, ignoreCase = true),
                onClick = { onSelect(hex) },
            )
        }
    }
}

@Composable
fun ColorDot(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) Color(0xFF14221C) else Color(0xFF8AA396),
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
    )
}
