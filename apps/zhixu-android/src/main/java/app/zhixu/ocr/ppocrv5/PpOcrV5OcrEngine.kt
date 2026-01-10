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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.max

class PpOcrV5OcrEngine(
    context: Context,
    private val repository: VaultRepository,
    private val vaultRootUri: Uri,
    private val useFp16: Boolean = true,
    private val useGpu: Boolean = false,
) : OcrEngine {
    private val modelManager = PpOcrV5ModelManager(context, repository)
    private val native = PpOcrV5Ncnn()
    private val lock = Mutex()

    @Volatile private var loadedModelKey: String? = null
    @Volatile private var cachedModels: PpOcrV5ModelManager.ModelPaths? = null

    private fun buildModelKey(models: PpOcrV5ModelManager.ModelPaths): String {
        return buildString {
            append(models.version).append('|')
            append(models.detParam.absolutePath).append('|')
            append(models.detBin.absolutePath).append('|')
            append(models.recParam.absolutePath).append('|')
            append(models.recBin.absolutePath).append('|')
            append("fp16=").append(useFp16).append('|')
            append("gpu=").append(useGpu)
        }
    }

    override suspend fun recognize(image: File): OcrResult {
        return lock.withLock {
            val started = System.currentTimeMillis()
            Log.i("ZhixuOcr", "recognize start image=${image.absolutePath} size=${image.length()}")

            var models = cachedModels ?: modelManager.ensureMobileModels(vaultRootUri).also { cachedModels = it }
            val desiredKey = buildModelKey(models)
            var loaded = loadedModelKey == desiredKey

            if (!loaded) {
                Log.i(
                    "ZhixuOcr",
                    "loading models detParam=${models.detParam.length()} detBin=${models.detBin.length()} recParam=${models.recParam.length()} recBin=${models.recBin.length()} fp16=$useFp16 gpu=$useGpu",
                )
                loaded =
                    runCatching {
                        native.nativeLoadModel(
                            models.detParam.absolutePath,
                            models.detBin.absolutePath,
                            models.recParam.absolutePath,
                            models.recBin.absolutePath,
                            useFp16,
                            useGpu,
                        )
                    }.getOrDefault(false)
                if (!loaded) {
                    Log.w("ZhixuOcr", "nativeLoadModel failed; refreshing models")
                    models = modelManager.refreshMobileModels(vaultRootUri).also { cachedModels = it }
                    loaded =
                        runCatching {
                            native.nativeLoadModel(
                                models.detParam.absolutePath,
                                models.detBin.absolutePath,
                                models.recParam.absolutePath,
                                models.recBin.absolutePath,
                                useFp16,
                                useGpu,
                            )
                        }.getOrDefault(false)
                }
                if (loaded) {
                    loadedModelKey = buildModelKey(models)
                }
            }

            if (!loaded) {
                return@withLock OcrResult(engine = "ppocrv5ncnn", elapsedMs = System.currentTimeMillis() - started)
            }

            val bitmap =
                decodeBitmapForOcr(image, maxSidePx = 1600)
                    ?: return@withLock OcrResult(engine = "ppocrv5ncnn", elapsedMs = System.currentTimeMillis() - started, error = "BitmapFactory.decodeFile failed")
            Log.i("ZhixuOcr", "bitmap w=${bitmap.width} h=${bitmap.height} config=${bitmap.config}")

            val nativeAttempt =
                try {
                    runCatching { native.nativeRecognizeBitmap(bitmap) }
                } finally {
                    runCatching { bitmap.recycle() }
                }
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

            OcrResult(
                blocks = blocks,
                engine = "ppocrv5ncnn",
                elapsedMs = System.currentTimeMillis() - started,
                error = nativeError,
            )
        }
    }

    private fun decodeBitmapForOcr(
        image: File,
        maxSidePx: Int,
    ): Bitmap? {
        val path = image.absolutePath
        val bounds =
            BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
        BitmapFactory.decodeFile(path, bounds)
        val outW = bounds.outWidth
        val outH = bounds.outHeight
        if (outW <= 0 || outH <= 0) return null

        var sample = 1
        while (max(outW / sample, outH / sample) > maxSidePx) {
            sample *= 2
        }

        val opts =
            BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
        val decoded = BitmapFactory.decodeFile(path, opts) ?: return null
        if (decoded.config == Bitmap.Config.ARGB_8888) return decoded

        val converted = decoded.copy(Bitmap.Config.ARGB_8888, false)
        if (converted != null) {
            runCatching { decoded.recycle() }
            return converted
        }
        return decoded
    }

    suspend fun warmUp(): Boolean {
        return lock.withLock {
            val models = cachedModels ?: modelManager.ensureMobileModels(vaultRootUri).also { cachedModels = it }
            val desiredKey = buildModelKey(models)
            if (loadedModelKey == desiredKey) return@withLock true
            val loaded =
                runCatching {
                    native.nativeLoadModel(
                        models.detParam.absolutePath,
                        models.detBin.absolutePath,
                        models.recParam.absolutePath,
                        models.recBin.absolutePath,
                        useFp16,
                        useGpu,
                    )
                }.getOrDefault(false)
            if (loaded) loadedModelKey = desiredKey
            loaded
        }
    }

    fun release() {
        runCatching { native.nativeRelease() }
        loadedModelKey = null
    }
}
