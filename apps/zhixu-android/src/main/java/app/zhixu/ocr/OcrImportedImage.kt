package app.zhixu.ocr

import android.net.Uri
import java.io.File

data class OcrImportedImage(
    val vaultUri: Uri,
    val vaultRelativePath: String,
    val localFile: File,
)

