package app.zhixu.ocr

import android.graphics.Bitmap
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MlKitOcrEngine : OcrEngine {
    override suspend fun recognize(bitmap: Bitmap): OcrResult =
        withContext(Dispatchers.Default) {
            val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
            try {
                val image = InputImage.fromBitmap(bitmap, 0)
                val text = Tasks.await(recognizer.process(image))
                toOcrResult(text)
            } finally {
                runCatching { recognizer.close() }
            }
        }
}

private fun toOcrResult(text: Text): OcrResult {
    val blocks =
        buildList {
            for (block in text.textBlocks) {
                for (line in block.lines) {
                    val box = line.boundingBox ?: continue
                    add(
                        OcrBlock(
                            text = line.text,
                            left = box.left,
                            top = box.top,
                            right = box.right,
                            bottom = box.bottom,
                            confidence = -1f,
                        ),
                    )
                }
            }
        }
    return OcrResult(blocks = blocks, fullText = text.text.orEmpty())
}

