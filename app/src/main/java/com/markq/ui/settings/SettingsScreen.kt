package com.markq.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.markq.ui.CompactTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.markq.BuildConfig
import com.markq.R
import com.markq.ui.appViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = appViewModel(),
) {
    val form by vm.form.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    Scaffold(
        topBar = {
            CompactTopAppBar(
                title = stringResource(R.string.settings),
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
                value = form.nickname,
                onValueChange = vm::setNickname,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.nickname)) },
                supportingText = { Text(stringResource(R.string.nickname_required_hint)) },
                isError = form.nicknameError,
                singleLine = true,
            )
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.nutstore_label), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.nutstore_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = form.url,
                onValueChange = vm::setUrl,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.webdav_url)) },
                singleLine = true,
            )
            OutlinedTextField(
                value = form.remoteDir,
                onValueChange = vm::setRemoteDir,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.remote_dir)) },
                supportingText = { Text(stringResource(R.string.remote_dir_hint)) },
                singleLine = true,
            )
            OutlinedTextField(
                value = form.username,
                onValueChange = vm::setUsername,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.username_optional)) },
                singleLine = true,
            )
            OutlinedTextField(
                value = form.password,
                onValueChange = vm::setPassword,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.password_optional)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = vm::save, enabled = !form.busy, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.save_server))
            }
            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.share_code), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.share_code_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(form.shareCode, style = MaterialTheme.typography.bodySmall)
            OutlinedButton(
                onClick = { clipboard.setText(AnnotatedString(form.shareCode)) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.copy_share_code)) }
            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(R.string.version_label, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(
                onClick = vm::checkUpdate,
                enabled = !form.checkingUpdate,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    when {
                        form.downloading -> stringResource(R.string.downloading_update)
                        form.checkingUpdate -> stringResource(R.string.checking_updates)
                        else -> stringResource(R.string.check_for_updates)
                    },
                )
            }
            if (form.downloading) {
                Spacer(Modifier.height(8.dp))
                if (form.downloadIndeterminate) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(
                        progress = { form.downloadPercent / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(
                            R.string.update_download_progress,
                            form.downloadPercent,
                            com.markq.core.ByteFormat.speed(form.downloadBytesPerSec),
                        ),
                    )
                }
            }
            val update = form.update
            if (update != null) {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.update_ready, update.version))
                Button(
                    onClick = { vm.installUpdate(context) },
                    enabled = !form.busy && !form.checkingUpdate,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.update_install)) }
            }
            if (form.message != null) {
                Spacer(Modifier.height(12.dp))
                Text(form.message!!)
            }
        }
    }
}
