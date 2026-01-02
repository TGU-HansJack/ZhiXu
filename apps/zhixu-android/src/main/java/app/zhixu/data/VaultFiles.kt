package com.zhixu.android.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File

fun vaultRootToDocumentFile(context: Context, rootUri: Uri): DocumentFile? {
    val scheme = rootUri.scheme.orEmpty()
    if (scheme.equals("file", ignoreCase = true)) {
        val path = rootUri.path ?: return null
        return DocumentFile.fromFile(File(path))
    }
    return DocumentFile.fromTreeUri(context, rootUri)
}

fun appManagedVaultRootUri(context: Context): Uri {
    val base = context.getExternalFilesDir(null) ?: context.filesDir
    val dir = File(base, "ZhixuVault")
    if (!dir.exists()) {
        dir.mkdirs()
    }
    return Uri.fromFile(dir)
}

