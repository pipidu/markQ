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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.markq.BuildConfig
import com.markq.ui.appViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = appViewModel(),
) {
    val form by vm.form.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                label = { Text("Nickname") },
                singleLine = true,
            )
            OutlinedTextField(
                value = form.url,
                onValueChange = vm::setUrl,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("WebDAV URL") },
                singleLine = true,
            )
            OutlinedTextField(
                value = form.username,
                onValueChange = vm::setUsername,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Username") },
                singleLine = true,
            )
            OutlinedTextField(
                value = form.password,
                onValueChange = vm::setPassword,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = vm::save, enabled = !form.busy, modifier = Modifier.fillMaxWidth()) {
                Text("Save server")
            }
            Spacer(Modifier.height(24.dp))
            Text("Share code", style = MaterialTheme.typography.titleMedium)
            Text(
                "Send this to teammates so they can join the same WebDAV folder.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(form.shareCode, style = MaterialTheme.typography.bodySmall)
            OutlinedButton(
                onClick = { clipboard.setText(AnnotatedString(form.shareCode)) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Copy share code") }
            Spacer(Modifier.height(24.dp))
            Text("Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(
                onClick = vm::checkUpdate,
                enabled = !form.checkingUpdate,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (form.checkingUpdate) "Checking…" else "Check for updates")
            }
            val update = form.update
            if (update != null) {
                Spacer(Modifier.height(8.dp))
                Text("Update ${update.version} is available.")
                Button(
                    onClick = { vm.installUpdate(update) },
                    enabled = !form.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Download and install") }
            }
            if (form.message != null) {
                Spacer(Modifier.height(12.dp))
                Text(form.message!!)
            }
        }
    }
}
