package com.markq.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.markq.MarkQApplication

@Composable
inline fun <reified VM : ViewModel> appViewModel(): VM {
    val app = LocalContext.current.applicationContext as MarkQApplication
    return viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val repo = app.container.repository
                val updates = app.container.updateManager
                val created: ViewModel = when (modelClass) {
                    com.markq.ui.home.HomeViewModel::class.java -> com.markq.ui.home.HomeViewModel(repo)
                    com.markq.ui.setup.SetupViewModel::class.java -> com.markq.ui.setup.SetupViewModel(repo, app)
                    com.markq.ui.settings.SettingsViewModel::class.java ->
                        com.markq.ui.settings.SettingsViewModel(repo, updates, app)
                    com.markq.ui.editor.EditorViewModel::class.java ->
                        com.markq.ui.editor.EditorViewModel(repo, app.container.places)
                    com.markq.ui.templates.TemplateListViewModel::class.java ->
                        com.markq.ui.templates.TemplateListViewModel(repo)
                    com.markq.ui.templates.TemplateEditorViewModel::class.java ->
                        com.markq.ui.templates.TemplateEditorViewModel(repo)
                    com.markq.ui.MainViewModel::class.java -> com.markq.ui.MainViewModel(repo, updates)
                    else -> error("Unknown ViewModel ${modelClass.simpleName}")
                }
                return created as T
            }
        },
    )
}
