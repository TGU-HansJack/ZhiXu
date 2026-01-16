package app.zhixu.data

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
    return DocumentFile.fromTreeUri(context, rootUri) ?: DocumentFile.fromSingleUri(context, rootUri)
}

fun appManagedVaultRootUri(context: Context): Uri {
    val candidates = listOfNotNull(context.getExternalFilesDir(null), context.filesDir)
    for (base in candidates) {
        val dir = File(base, "ZhixuVault")
        if (dir.exists()) {
            if (dir.isDirectory) return Uri.fromFile(dir)
            continue
        }
        if (dir.mkdirs() || (dir.exists() && dir.isDirectory)) {
            return Uri.fromFile(dir)
        }
    }
    val fallback = File(context.filesDir, "ZhixuVault")
    fallback.mkdirs()
    return Uri.fromFile(fallback)
}
