package com.xyq.librender

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.xyq.librender.test.RenderTestActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith


/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun useAppContext() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.xyq.librender.test", appContext.packageName)
    }

    @Test
    fun useRenderTestActivity() {
        val scenario: ActivityScenario<RenderTestActivity> = ActivityScenario.launch(
            RenderTestActivity::class.java
        )
        Thread.sleep(10 * 1000)
        scenario.close()
    }
}