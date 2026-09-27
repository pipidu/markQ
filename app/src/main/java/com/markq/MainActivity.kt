package com.markq

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import com.markq.core.MarkQLink
import com.markq.ui.MainViewModel
import com.markq.ui.appViewModel
import com.markq.ui.editor.EditorScreen
import com.markq.ui.home.HomeScreen
import com.markq.ui.settings.SettingsScreen
import com.markq.ui.setup.SetupScreen
import com.markq.ui.templates.TemplateEditorScreen
import com.markq.ui.templates.TemplateListScreen
import com.markq.ui.theme.MarkQTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    private val incomingIntents = MutableStateFlow(0 to (null as Intent?))

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        incomingIntents.value = 1 to intent
        enableEdgeToEdge()
        setContent {
            val main: MainViewModel = appViewModel()
            val settings by main.settings.collectAsStateWithLifecycle()
            val incoming by incomingIntents.collectAsStateWithLifecycle()
            val openTemplateId by main.openTemplateId.collectAsStateWithLifecycle()
            val openTemplateMode by main.openTemplateMode.collectAsStateWithLifecycle()
            val linkError by main.linkError.collectAsStateWithLifecycle()
            MarkQTheme(
                barHex = settings.barColor,
                backgroundHex = settings.backgroundColor,
                fabHex = settings.fabColor,
            ) {
                val update by main.update.collectAsStateWithLifecycle()
                val snackbar = remember { SnackbarHostState() }
                val nav = rememberNavController()
                val context = LocalContext.current
                val start = if (settings.isConfigured) "home" else "setup"

                LaunchedEffect(incoming.first) {
                    main.handleIntent(incoming.second)
                }

                LaunchedEffect(settings.isConfigured, openTemplateId, openTemplateMode) {
                    if (!settings.isConfigured) {
                        val current = nav.currentDestination?.route
                        if (current != "setup") {
                            nav.navigate("setup") {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                        return@LaunchedEffect
                    }
                    val templateId = openTemplateId
                    if (templateId != null) {
                        nav.navigate("home") {
                            popUpTo(0) { inclusive = true }
                        }
                        val dest = when (openTemplateMode) {
                            MarkQLink.OpenMode.Camera -> "fromTemplate/$templateId?camera=1"
                            MarkQLink.OpenMode.Save -> "fromTemplate/$templateId?save=1"
                            MarkQLink.OpenMode.Edit -> "fromTemplate/$templateId"
                        }
                        nav.navigate(dest)
                        main.onOpenedTemplateLink()
                        return@LaunchedEffect
                    }
                    val current = nav.currentDestination?.route
                    if (current == "setup" || current == null) {
                        nav.navigate("home") {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                }

                LaunchedEffect(update.error) {
                    val msg = update.error ?: return@LaunchedEffect
                    snackbar.showSnackbar(msg)
                    main.consumeUpdateMessage()
                }
                LaunchedEffect(linkError) {
                    val msg = linkError ?: return@LaunchedEffect
                    snackbar.showSnackbar(msg)
                    main.consumeLinkError()
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.background,
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
                                onTemplates = { nav.navigate("templates") },
                                onOpen = { id -> nav.navigate("editor?entryId=$id") },
                                onSettings = { nav.navigate("settings") },
                            )
                        }
                        composable("settings") {
                            SettingsScreen(onBack = { nav.popBackStack() })
                        }
                        composable("templates") {
                            TemplateListScreen(
                                onBack = { nav.popBackStack() },
                                onCreate = { nav.navigate("template") },
                                onEdit = { id -> nav.navigate("template/$id") },
                                onUse = { id ->
                                    nav.navigate("fromTemplate/$id") {
                                        popUpTo("templates") { inclusive = true }
                                    }
                                },
                            )
                        }
                        composable("template") {
                            TemplateEditorScreen(onDone = { nav.popBackStack() })
                        }
                        composable(
                            route = "template/{templateId}",
                            arguments = listOf(navArgument("templateId") { type = NavType.StringType }),
                        ) { back ->
                            TemplateEditorScreen(
                                templateId = back.arguments?.getString("templateId"),
                                onDone = { nav.popBackStack() },
                            )
                        }
                        composable(
                            route = "fromTemplate/{templateId}?camera={camera}&save={save}",
                            arguments = listOf(
                                navArgument("templateId") { type = NavType.StringType },
                                navArgument("camera") {
                                    type = NavType.StringType
                                    defaultValue = "0"
                                },
                                navArgument("save") {
                                    type = NavType.StringType
                                    defaultValue = "0"
                                },
                            ),
                        ) { back ->
                            val id = back.arguments?.getString("templateId")
                            val openCamera = back.arguments?.getString("camera") == "1"
                            val openSave = !openCamera && back.arguments?.getString("save") == "1"
                            EditorScreen(
                                templateId = id,
                                openCamera = openCamera,
                                openSave = openSave,
                                onDone = { nav.popBackStack() },
                                onSavedFromLink = { entryId ->
                                    nav.navigate("editor?entryId=$entryId") {
                                        popUpTo("home")
                                    }
                                },
                            )
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingIntents.value = incomingIntents.value.first + 1 to intent
    }

    override fun onResume() {
        super.onResume()
        (application as MarkQApplication).container.updateManager.retryPendingInstall(this)
    }
}
