package org.litespace.browser

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.ResultReceiver
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class LegacySandboxDeviceTest {
    @Test fun realSeparateProcessesKeepDifferentCookiesForSameOrigin() {
        assumeTrue(Build.VERSION.SDK_INT >= 28)
        val app = ApplicationProvider.getApplicationContext<LiteSpaceApplication>()
        fun probe(component: Class<*>, value: String? = null): String {
            val result = AtomicReference(""); val latch = CountDownLatch(1)
            val receiver = object : ResultReceiver(null) {
                override fun onReceiveResult(code: Int, data: Bundle?) { result.set(data?.getString("cookie").orEmpty()); latch.countDown() }
            }
            // The anonymous test subclass is absent from a remote app process's
            // classloader. Parcel the framework base type while retaining its Binder.
            val parcel = android.os.Parcel.obtain()
            val remoteReceiver = try { receiver.writeToParcel(parcel, 0); parcel.setDataPosition(0); ResultReceiver.CREATOR.createFromParcel(parcel) } finally { parcel.recycle() }
            app.startActivity(Intent(app, component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("reply", remoteReceiver).apply { if(value != null) putExtra("set", value) })
            val completed = latch.await(45, TimeUnit.SECONDS)
            if(!completed) {
                val descriptor = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("logcat -d -t 1500 -v brief")
                val logs = android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
                fail("Remote cookie probe must complete: " + logs.lineSequence().filter { it.contains("AndroidRuntime") || it.contains("LiteSpaceSandbox") || it.contains("background", true) }.joinToString("\n").takeLast(10000))
            }
            return result.get()
        }
        ActivityScenario.launch(MainActivity::class.java).use {
            val red = "red-${UUID.randomUUID()}"; val green = "green-${UUID.randomUUID()}"
            assertTrue(probe(RedProbeActivity::class.java, red).contains(red))
            assertTrue(probe(GreenProbeActivity::class.java, green).contains(green))
            val repeated = probe(RedProbeActivity::class.java)
            assertTrue(repeated.contains(red)); assertFalse(repeated.contains(green))
        }
    }
}
