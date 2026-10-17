package com.illyaua.winamp.player

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import java.io.File

object StorageAccess {
    /** True when the app may list and read audio files by path. */
    fun hasAccess(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 30) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }
}

/** Starting points for the file browser. */
object StorageRoots {
    class Shortcut(val label: String, val dir: File)

    fun primary(): File = Environment.getExternalStorageDirectory()

    fun shortcuts(context: Context): List<Shortcut> {
        val out = ArrayList<Shortcut>()
        val primary = primary()
        out += Shortcut("INT", primary)
        listOf("MUSIC" to Environment.DIRECTORY_MUSIC, "DL" to Environment.DIRECTORY_DOWNLOADS).forEach { (label, type) ->
            @Suppress("DEPRECATION")
            val dir = Environment.getExternalStoragePublicDirectory(type)
            if (dir.isDirectory) out += Shortcut(label, dir)
        }
        // Removable cards show up as extra app-specific dirs; the card root is the part before /Android/data.
        context.getExternalFilesDirs(null).forEach { appDir ->
            if (appDir == null) return@forEach
            val root = File(appDir.absolutePath.substringBefore("/Android/data"))
            if (root != primary && root.exists() && out.none { it.dir == root }) out += Shortcut("SD", root)
        }
        return out
    }

    /** Music folder if there is one, otherwise the top of internal storage. */
    fun startDirectory(): File {
        @Suppress("DEPRECATION")
        val music = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        return if (music.isDirectory) music else primary()
    }
}
