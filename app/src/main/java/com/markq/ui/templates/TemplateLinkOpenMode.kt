package com.markq.ui.templates

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.markq.R
import com.markq.core.MarkQLink

@Composable
fun TemplateLinkOpenModeGroup(
    mode: MarkQLink.OpenMode,
    onModeChange: (MarkQLink.OpenMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.selectableGroup()) {
        Text(
            stringResource(R.string.template_link_open_mode),
            style = MaterialTheme.typography.labelLarge,
        )
        TemplateLinkOpenModeRow(
            selected = mode == MarkQLink.OpenMode.Edit,
            label = stringResource(R.string.template_link_open_edit),
            onClick = { onModeChange(MarkQLink.OpenMode.Edit) },
        )
        TemplateLinkOpenModeRow(
            selected = mode == MarkQLink.OpenMode.Camera,
            label = stringResource(R.string.template_link_open_camera),
            onClick = { onModeChange(MarkQLink.OpenMode.Camera) },
        )
        TemplateLinkOpenModeRow(
            selected = mode == MarkQLink.OpenMode.Save,
            label = stringResource(R.string.template_link_open_save),
            onClick = { onModeChange(MarkQLink.OpenMode.Save) },
        )
    }
}

@Composable
private fun TemplateLinkOpenModeRow(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton,
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}
