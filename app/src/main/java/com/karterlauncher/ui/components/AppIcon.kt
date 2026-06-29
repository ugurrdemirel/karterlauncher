package com.karterlauncher.ui.components

import android.content.Context
import androidx.collection.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val ICON_SIZE_PX = 208
private val IconCornerRadius = 18.dp

private val iconCache = LruCache<String, ImageBitmap>(128)

@Composable
fun AppIcon(
    packageName: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf(iconCache[packageName]) }
    LaunchedEffect(packageName) {
        val cached = iconCache[packageName]
        if (cached != null) {
            bitmap = cached
        } else {
            val loaded = loadAppIcon(context.applicationContext, packageName)
            if (loaded != null) {
                iconCache.put(packageName, loaded)
                bitmap = loaded
            } else {
                bitmap = null
            }
        }
    }

    val resolved = bitmap
    if (resolved != null) {
        Image(
            bitmap = resolved,
            contentDescription = null,
            modifier = modifier.clip(RoundedCornerShape(IconCornerRadius)),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(IconCornerRadius))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Apps,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(44.dp),
            )
        }
    }
}

private suspend fun loadAppIcon(context: Context, packageName: String): ImageBitmap? =
    withContext(Dispatchers.IO) {
        runCatching {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            drawable.toBitmap(ICON_SIZE_PX, ICON_SIZE_PX).asImageBitmap()
        }.getOrNull()
    }
