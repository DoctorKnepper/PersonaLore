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
        // Full-bleed layers, so the masks used by different launchers can be checked.
        for (name in listOf("ic_launcher_background", "ic_launcher_foreground", "ic_launcher_monochrome")) {
            val id = ctx.resources.getIdentifier(name, "drawable", ctx.packageName)
            val layer = Bitmap.createBitmap(432, 432, Bitmap.Config.ARGB_8888)
            ContextCompat.getDrawable(ctx, id)!!.apply { setBounds(0, 0, 432, 432); draw(Canvas(layer)) }
            File("build/screens/$name.png").outputStream().use { layer.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
