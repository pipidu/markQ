package com.markq

import android.content.Context
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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

                if (update.readyToInstall) {
                    val version = update.info?.version.orEmpty()
                    AlertDialog(
                        onDismissRequest = { main.dismissUpdate() },
                        title = { Text(stringResource(R.string.update_available_title)) },
                        text = { Text(stringResource(R.string.update_available_body, version)) },
                        confirmButton = {
                            TextButton(onClick = { main.installUpdate(context) }) {
                                Text(stringResource(R.string.update_install))
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
}
