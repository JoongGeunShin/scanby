package com.example.scanby.core.media

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.example.scanby.core.vision.DocQuadDetector
import com.example.scanby.core.vision.warpToFlatDocument
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale

fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
    } else {
        @Suppress("DEPRECATION")
        MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
    }
}
fun processPickedImage(context: Context, picked: Bitmap, detector: DocQuadDetector?) {
    val corners = detector?.detectCorners(picked)?.corners
    val output = if (corners != null) {
        warpToFlatDocument(picked, corners) ?: picked
    } else {
        picked
    }

    val fileName = "scanby_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.KOREA)
        .format(System.currentTimeMillis()) + ".jpg"
    val savedToGallery = saveToGallery(context, output, fileName)
    if (!savedToGallery) {
        File(context.filesDir, fileName).let { photoFile ->
            FileOutputStream(photoFile).use { out ->
                output.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }
        }
    }
    if (output !== picked) picked.recycle()
    output.recycle()

    val message = if (savedToGallery) "갤러리에 저장됨: $fileName" else "저장됨: $fileName"
    ContextCompat.getMainExecutor(context).execute {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}
fun saveToGallery(context: Context, bitmap: Bitmap, fileName: String): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false

    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/scanby")
    }
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false

    return try {
        val stream = resolver.openOutputStream(uri) ?: return false
        stream.use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out) }
        true
    } catch (e: Exception) {
        false
    }
}
