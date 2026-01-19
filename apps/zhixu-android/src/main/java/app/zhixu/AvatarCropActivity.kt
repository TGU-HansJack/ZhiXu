package app.zhixu

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AppCompatActivity
import app.zhixu.data.UiPreferences
import app.zhixu.data.UiThemeMode
import com.canhub.cropper.CropImageView
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class AvatarCropActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val themeMode =
            runCatching {
                runBlocking { UiPreferences(applicationContext).themeMode.first() }
            }.getOrNull() ?: UiThemeMode.SYSTEM
        delegate.localNightMode =
            when (themeMode) {
                UiThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                UiThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                UiThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
            }

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_avatar_crop)

        val cropView = findViewById<CropImageView>(R.id.avatar_crop_view)
        val cancelButton = findViewById<MaterialButton>(R.id.avatar_crop_cancel)
        val confirmButton = findViewById<MaterialButton>(R.id.avatar_crop_confirm)
        val backdrop = findViewById<View>(R.id.avatar_crop_backdrop)

        fun finishCanceled() {
            setResult(RESULT_CANCELED)
            finish()
        }

        val inputUri =
            intent?.getStringExtra(EXTRA_INPUT_URI)
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let(Uri::parse)
                ?: run {
                    finishCanceled()
                    return
                }

        val metrics = resources.displayMetrics
        window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        cropView.setFixedAspectRatio(true)
        cropView.setAspectRatio(1, 1)
        cropView.cropShape = CropImageView.CropShape.OVAL

        cropView.setOnSetImageUriCompleteListener { _, _, error ->
            if (error != null) {
                Toast.makeText(this, getString(R.string.account_avatar_pick_failed), Toast.LENGTH_SHORT).show()
                finishCanceled()
            }
        }

        cropView.setOnCropImageCompleteListener { _, result ->
            confirmButton.isEnabled = true
            cancelButton.isEnabled = true

            if (!result.isSuccessful) {
                Toast.makeText(this, getString(R.string.account_avatar_pick_failed), Toast.LENGTH_SHORT).show()
                return@setOnCropImageCompleteListener
            }

            val out = result.uriContent
            if (out == null) {
                Toast.makeText(this, getString(R.string.account_avatar_pick_failed), Toast.LENGTH_SHORT).show()
                return@setOnCropImageCompleteListener
            }
            setResult(RESULT_OK, Intent().putExtra(EXTRA_CROPPED_URI, out.toString()))
            finish()
        }

        backdrop.setOnClickListener { finishCanceled() }
        cancelButton.setOnClickListener { finishCanceled() }
        confirmButton.setOnClickListener {
            confirmButton.isEnabled = false
            cancelButton.isEnabled = false

            runCatching {
                cropView.croppedImageAsync(
                    saveCompressFormat = Bitmap.CompressFormat.JPEG,
                    saveCompressQuality = 90,
                    reqWidth = 512,
                    reqHeight = 512,
                    options = CropImageView.RequestSizeOptions.RESIZE_EXACT,
                )
            }.onFailure {
                confirmButton.isEnabled = true
                cancelButton.isEnabled = true
                Toast.makeText(this, it.message.orEmpty().ifBlank { getString(R.string.common_failed) }, Toast.LENGTH_SHORT).show()
            }
        }

        runCatching { cropView.setImageUriAsync(inputUri) }.onFailure {
            Toast.makeText(this, getString(R.string.account_avatar_pick_failed), Toast.LENGTH_SHORT).show()
            finishCanceled()
        }
    }

    companion object {
        const val EXTRA_INPUT_URI = "inputUri"
        const val EXTRA_CROPPED_URI = "croppedUri"
    }
}
