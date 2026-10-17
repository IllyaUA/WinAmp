package com.illyaua.winamp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.illyaua.winamp.player.PlayerEngine
import com.illyaua.winamp.ui.WinampApp

class MainActivity : ComponentActivity() {

    private val engine by lazy { PlayerEngine.get(this) }

    private val pickFiles =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            uris.forEach(::keepAccess)
            engine.addUris(uris)
        }

    private val pickFolder =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                keepAccess(uri)
                engine.addFolder(uri)
            }
        }

    private val askPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            if (results[Manifest.permission.RECORD_AUDIO] == true) engine.startVisualizer()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissions()
        if (savedInstanceState == null) handleViewIntent(intent)

        setContent {
            WinampApp(
                engine = engine,
                onAddFiles = { pickFiles.launch(AUDIO_TYPES) },
                onAddFolder = { pickFolder.launch(null) },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleViewIntent(intent)
    }

    /** "Open with" from a file manager: add the file and start playing it. */
    private fun handleViewIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        intent.data?.let { engine.addUris(listOf(it), playFirst = true) }
    }

    private fun requestPermissions() {
        if (granted(Manifest.permission.RECORD_AUDIO)) engine.startVisualizer()
        val missing = buildList {
            if (!granted(Manifest.permission.RECORD_AUDIO)) add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= 33 && !granted(Manifest.permission.POST_NOTIFICATIONS)) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (missing.isNotEmpty()) askPermissions.launch(missing.toTypedArray())
    }

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    /** Lets the playlist reopen this file after the app restarts. */
    private fun keepAccess(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
            // some providers do not offer persistable grants; the file still plays for this session
        }
    }

    private companion object {
        val AUDIO_TYPES = arrayOf(
            "audio/*",
            "application/x-midi",
            "application/ogg",
        )
    }
}
