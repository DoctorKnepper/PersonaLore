package com.doctorknepper.personalore

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Renders the launcher icon to app/build/screens/icon.png for review. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class IconRenderTest {
    @Test
    fun renderIcon() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val d = ContextCompat.getDrawable(ctx, R.mipmap.ic_launcher)!!
        val bmp = Bitmap.createBitmap(432, 432, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, 432, 432)
        d.draw(Canvas(bmp))
        File("build/screens").mkdirs()
        File("build/screens/icon.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
