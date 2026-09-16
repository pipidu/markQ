package com.markq.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.markq.R
import com.markq.ui.appViewModel

@Composable
fun SetupScreen(vm: SetupViewModel = appViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Top,
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.setup_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = state.nickname,
            onValueChange = vm::setNickname,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.nickname)) },
            supportingText = { Text(stringResource(R.string.nickname_required_hint)) },
            isError = state.nicknameError,
            singleLine = true,
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = state.shareCode,
            onValueChange = vm::setShareCode,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.share_code_optional)) },
            supportingText = { Text(stringResource(R.string.share_code_hint)) },
        )
        OutlinedButton(
            onClick = vm::applyShareCode,
            enabled = !state.busy && state.shareCode.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.apply_share_code))
        }
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = state.url,
            onValueChange = vm::setUrl,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.webdav_url)) },
            singleLine = true,
        )
        OutlinedTextField(
            value = state.username,
            onValueChange = vm::setUsername,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.username_optional)) },
            singleLine = true,
        )
        OutlinedTextField(
            value = state.password,
            onValueChange = vm::setPassword,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.password_optional)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
        )
        if (state.error != null) {
            Spacer(Modifier.height(12.dp))
            Text(state.error!!, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = vm::connect,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.busy) {
                CircularProgressIndicator(strokeWidth = 2.dp)
            } else {
                Text(stringResource(R.string.save_and_connect))
            }
        }
    }
}
