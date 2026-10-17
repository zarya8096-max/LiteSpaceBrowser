package org.litespace.browser

import android.graphics.Bitmap
import android.util.Base64
import org.junit.Assert.*
import org.junit.Test
import org.litespace.browser.util.IconImages
import java.io.ByteArrayOutputStream

class IconDeviceTest {
    @Test fun compressedLargeIconsAreSampledBeforeDecode() {
        val source = Bitmap.createBitmap(2048, 2048, Bitmap.Config.ARGB_8888)
        val bytes = ByteArrayOutputStream().also { source.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        source.recycle()
        val icon = IconImages.decode(Base64.encodeToString(bytes, Base64.NO_WRAP))!!
        assertEquals(192, icon.width)
        assertEquals(192, icon.height)
        icon.recycle()
        assertNull(IconImages.decode("invalid base64"))
        assertNull(IconImages.decode("A".repeat(400_001)))
    }
}
