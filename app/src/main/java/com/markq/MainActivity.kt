package com.markq

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.markq.core.ByteFormat
import com.markq.ui.MainViewModel
import com.markq.ui.appViewModel
import com.markq.ui.editor.EditorScreen
import com.markq.ui.home.HomeScreen
import com.markq.ui.settings.SettingsScreen
import com.markq.ui.setup.SetupScreen
import com.markq.ui.theme.MarkQTheme

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MarkQTheme {
                val main: MainViewModel = appViewModel()
                val settings by main.settings.collectAsStateWithLifecycle()
                val update by main.update.collectAsStateWithLifecycle()
                val snackbar = remember { SnackbarHostState() }
                val nav = rememberNavController()
                val context = LocalContext.current
                val start = if (settings.isConfigured) "home" else "setup"

                LaunchedEffect(settings.isConfigured) {
                    val dest = if (settings.isConfigured) "home" else "setup"
                    val current = nav.currentDestination?.route
                    if (current != dest && (current == "setup" || current == "home" || current == null)) {
                        nav.navigate(dest) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                }

                LaunchedEffect(update.error) {
                    val msg = update.error ?: return@LaunchedEffect
                    snackbar.showSnackbar(msg)
                    main.consumeUpdateMessage()
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    snackbarHost = { SnackbarHost(snackbar) },
                ) { padding ->
                    NavHost(
                        navController = nav,
                        startDestination = start,
                        modifier = Modifier.padding(padding),
                    ) {
                        composable("setup") { SetupScreen() }
                        composable("home") {
                            HomeScreen(
                                onAdd = { nav.navigate("editor") },
                                onOpen = { id -> nav.navigate("editor?entryId=$id") },
                                onSettings = { nav.navigate("settings") },
                            )
                        }
                        composable("settings") {
                            SettingsScreen(onBack = { nav.popBackStack() })
                        }
                        composable(
                            route = "editor?entryId={entryId}",
                            arguments = listOf(
                                navArgument("entryId") {
                                    type = NavType.StringType
                                    nullable = true
                                    defaultValue = null
                                },
                            ),
                        ) { back ->
                            EditorScreen(
                                entryId = back.arguments?.getString("entryId"),
                                onDone = { nav.popBackStack() },
                            )
                        }
                    }
                }

                if (update.showUpdateDialog) {
                    val version = update.versionLabel
                    AlertDialog(
                        onDismissRequest = { main.dismissUpdate() },
                        title = { Text(stringResource(R.string.update_available_title)) },
                        text = {
                            Column(Modifier.fillMaxWidth()) {
                                if (update.downloading) {
                                    Text(stringResource(R.string.update_available_downloading, version))
                                    Spacer(Modifier.height(12.dp))
                                    if (update.downloadTotal > 0L) {
                                        LinearProgressIndicator(
                                            progress = { update.progressPercent / 100f },
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            stringResource(
                                                R.string.update_download_progress,
                                                update.progressPercent,
                                                ByteFormat.speed(update.downloadBytesPerSec),
                                            ),
                                        )
                                    } else {
                                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                    }
                                } else {
                                    Text(stringResource(R.string.update_available_body, version))
                                }
                            }
                        },
                        confirmButton = {
                            if (update.readyToInstall) {
                                TextButton(onClick = { main.installUpdate(context) }) {
                                    Text(stringResource(R.string.update_install))
                                }
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { main.dismissUpdate() }) {
                                Text(stringResource(R.string.update_later))
                            }
                        },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        (application as MarkQApplication).container.updateManager.retryPendingInstall(this)
    }
}
