package org.litespace.browser.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.InputStream

/** Decode imported icons at a bounded resolution, including very large PNG/JPEGs. */
object IconImages {
    fun decode(encoded: String): Bitmap? = runCatching {
        require(encoded.isNotEmpty() && encoded.length <= 400_000)
        val bytes = Base64.decode(encoded, Base64.DEFAULT)
        read { bytes.inputStream() }
    }.getOrNull()

    fun read(open: () -> InputStream): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open().use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Invalid image" }
        var sample = 1
        while(maxOf(bounds.outWidth, bounds.outHeight) / sample > 384) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val original = open().use { BitmapFactory.decodeStream(it, null, options) } ?: error("Cannot decode image")
        val scaled = Bitmap.createScaledBitmap(original, 192, 192, true)
        if(scaled !== original) original.recycle()
        return scaled
    }
}
