package com.example.npuclassifier

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {
    private val vm: SortViewModel by viewModels()
    private var hasAll by mutableStateOf(false)

    private val pickFiles = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) vm.loadUris(uris)
    }
    private val pickFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.loadFolder(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Repo.releaseRunning(this)
        setContent {
            AppTheme {
                App(
                    vm = vm,
                    hasAll = hasAll,
                    onPermission = ::openPermission,
                    onPickFiles = { pickFiles.launch(arrayOf("image/*", "video/*")) },
                    onPickFolder = { pickFolder.launch(null) }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hasAll = Environment.isExternalStorageManager()
    }

    private fun openPermission() {
        try {
            startActivity(
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName"))
            )
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
        }
    }
}
