package app.zhixu.ocr.ppocrv5

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import app.zhixu.data.VaultRepository
import app.zhixu.ocr.OcrBlock
import app.zhixu.ocr.OcrBox
import app.zhixu.ocr.OcrEngine
import app.zhixu.ocr.OcrPoint
import app.zhixu.ocr.OcrResult
import java.io.File

class PpOcrV5OcrEngine(
    context: Context,
    private val repository: VaultRepository,
    private val vaultRootUri: Uri,
) : OcrEngine {
    private val modelManager = PpOcrV5ModelManager(context, repository)
    private val native = PpOcrV5Ncnn()

    override suspend fun recognize(image: File): OcrResult {
        val started = System.currentTimeMillis()
        Log.i("ZhixuOcr", "recognize start image=${image.absolutePath} size=${image.length()}")
        var models = modelManager.ensureMobileModels(vaultRootUri)
        Log.i(
            "ZhixuOcr",
            "models detParam=${models.detParam.length()} detBin=${models.detBin.length()} recParam=${models.recParam.length()} recBin=${models.recBin.length()}",
        )
        var loaded =
            runCatching {
                native.nativeLoadModel(
                    models.detParam.absolutePath,
                    models.detBin.absolutePath,
                    models.recParam.absolutePath,
                    models.recBin.absolutePath,
                    true,
                    false,
                )
            }.getOrDefault(false)
        if (!loaded) {
            Log.w("ZhixuOcr", "nativeLoadModel failed; refreshing models")
            models = modelManager.refreshMobileModels(vaultRootUri)
            loaded =
                runCatching {
                    native.nativeLoadModel(
                        models.detParam.absolutePath,
                        models.detBin.absolutePath,
                        models.recParam.absolutePath,
                        models.recBin.absolutePath,
                        true,
                        false,
                    )
                }.getOrDefault(false)
        }
        if (!loaded) return OcrResult(engine = "ppocrv5ncnn", elapsedMs = System.currentTimeMillis() - started)

        val bitmap =
            BitmapFactory.decodeFile(image.absolutePath)?.let { decoded ->
                if (decoded.config == Bitmap.Config.ARGB_8888) decoded else decoded.copy(Bitmap.Config.ARGB_8888, false)
            }
                ?: return OcrResult(engine = "ppocrv5ncnn", elapsedMs = System.currentTimeMillis() - started, error = "BitmapFactory.decodeFile failed")
        Log.i("ZhixuOcr", "bitmap w=${bitmap.width} h=${bitmap.height} config=${bitmap.config}")

        val nativeAttempt = runCatching { native.nativeRecognizeBitmap(bitmap) }
        val nativeBlocks = nativeAttempt.getOrNull()
        val nativeError = nativeAttempt.exceptionOrNull()?.message ?: nativeAttempt.exceptionOrNull()?.javaClass?.simpleName
        Log.i("ZhixuOcr", "native blocks=${nativeBlocks?.size ?: -1}")
        val blocksUnsorted =
            nativeBlocks?.mapNotNull { b ->
                val pts = b.points
                if (pts == null || pts.size < 8) return@mapNotNull null
                OcrBlock(
                    text = b.text.orEmpty(),
                    confidence = b.score,
                    box =
                        OcrBox(
                            points =
                                listOf(
                                    OcrPoint(pts[0], pts[1]),
                                    OcrPoint(pts[2], pts[3]),
                                    OcrPoint(pts[4], pts[5]),
                                    OcrPoint(pts[6], pts[7]),
                                ),
                        ),
                )
            }.orEmpty()
        val blocks =
            blocksUnsorted.sortedWith(
                compareBy<OcrBlock>(
                    { it.box?.points?.minOfOrNull { p -> p.y } ?: Float.MAX_VALUE },
                    { it.box?.points?.minOfOrNull { p -> p.x } ?: Float.MAX_VALUE },
                ),
            )

        return OcrResult(
            blocks = blocks,
            engine = "ppocrv5ncnn",
            elapsedMs = System.currentTimeMillis() - started,
            error = nativeError,
        )
    }

    fun release() {
        runCatching { native.nativeRelease() }
    }
}
