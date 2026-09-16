package com.markq.ui.templates

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.markq.R
import com.markq.ui.CompactTopAppBar
import com.markq.ui.appViewModel
import com.markq.ui.theme.EntryColorPicker

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TemplateEditorScreen(
    templateId: String? = null,
    onDone: () -> Unit,
    vm: TemplateEditorViewModel = appViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(templateId) { vm.load(templateId) }
    LaunchedEffect(state.saved) {
        if (state.saved) onDone()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CompactTopAppBar(
                title = stringResource(
                    if (state.templateId == null) R.string.create_template else R.string.edit_template,
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
                value = state.name,
                onValueChange = vm::setName,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.template_name)) },
                singleLine = true,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.text,
                onValueChange = vm::setText,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.text)) },
                minLines = 4,
            )
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
                Text(
                    if (state.busy) stringResource(R.string.saving) else stringResource(R.string.save_template),
                )
            }
        }
    }
}
