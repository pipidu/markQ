package com.markq

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.markq.ui.MainViewModel
import com.markq.ui.appViewModel
import com.markq.ui.editor.EditorScreen
import com.markq.ui.home.HomeScreen
import com.markq.ui.settings.SettingsScreen
import com.markq.ui.setup.SetupScreen
import com.markq.ui.theme.MarkQTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MarkQTheme {
                val main: MainViewModel = appViewModel()
                val settings by main.settings.collectAsStateWithLifecycle()
                val update by main.update.collectAsStateWithLifecycle()
                val installing by main.installing.collectAsStateWithLifecycle()
                val updateMessage by main.updateMessage.collectAsStateWithLifecycle()
                val snackbar = remember { SnackbarHostState() }
                val nav = rememberNavController()
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

                LaunchedEffect(updateMessage) {
                    val msg = updateMessage
                    if (msg != null) {
                        snackbar.showSnackbar(msg)
                        main.consumeUpdateMessage()
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
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
                                onSettings = { nav.navigate("settings") },
                            )
                        }
                        composable("settings") {
                            SettingsScreen(onBack = { nav.popBackStack() })
                        }
                        composable("editor") {
                            EditorScreen(onDone = { nav.popBackStack() })
                        }
                    }
                }

                val info = update
                if (info != null) {
                    AlertDialog(
                        onDismissRequest = { main.dismissUpdate() },
                        title = { Text("Update available") },
                        text = {
                            Text(
                                if (installing) "Downloading MarkQ ${info.version}…"
                                else "MarkQ ${info.version} is on GitHub Releases.",
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = { main.installUpdate(info) },
                                enabled = !installing,
                            ) { Text("Update") }
                        },
                        dismissButton = {
                            TextButton(onClick = { main.dismissUpdate() }) { Text("Later") }
                        },
                    )
                }
            }
        }
    }
}
