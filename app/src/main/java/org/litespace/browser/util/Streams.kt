package org.litespace.browser.util

import java.io.InputStream
import java.io.ByteArrayOutputStream

/** Bounded read compatible with Android 8 (InputStream.readNBytes is newer). */
fun InputStream.readBounded(limit: Int): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while(output.size() < limit) {
        val count = read(buffer, 0, minOf(buffer.size, limit - output.size()))
        if(count == -1) break
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}
