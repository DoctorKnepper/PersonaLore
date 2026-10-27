package com.doctorknepper.personalore.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.doctorknepper.personalore.data.MediaCache
import com.doctorknepper.personalore.data.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Shows a photo from the encrypted media folder, decrypting it in the background. */
@Composable
fun EncryptedImage(name: String, modifier: Modifier, contentScale: ContentScale = ContentScale.Crop, maxSidePx: Int = 512) {
    val bmp by produceState<Bitmap?>(null, name, maxSidePx, Store.unlocked) {
        value = if (Store.unlocked) withContext(Dispatchers.IO) { MediaCache.bitmap(name, maxSidePx) } else null
    }
    val b = bmp
    if (b != null) Image(b.asImageBitmap(), contentDescription = null, contentScale = contentScale, modifier = modifier)
    else Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh))
}
