package com.bepinex.android.bridge

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.bepinex.android.BepInExLog

/** Decode plugin FAB icons from C# (PNG with alpha). */
object IconDecoder {

    private const val MAX_PIXELS = 256 * 256
    private const val MAX_BYTES = 512 * 1024

    /**
     * @param data base64 payload, optionally prefixed with data:image/png;base64,
     * @param mime ignored except logging; always decode as bitmap
     * @return ARGB_8888 Bitmap or null
     */
    fun decode(data: String, mime: String = "image/png"): Bitmap? {
        return try {
            decodeInternal(data, mime)
        } catch (t: Throwable) {
            BepInExLog.w("IconDecoder: failed to decode $mime icon: ${t.message}")
            null
        }
    }

    private fun decodeInternal(data: String, mime: String): Bitmap? {
        val payload = stripDataUri(data).trim()
        if (payload.isEmpty()) {
            BepInExLog.w("IconDecoder: empty $mime icon payload")
            return null
        }

        val bytes = decodeBase64(payload)
        if (bytes == null || bytes.isEmpty()) {
            BepInExLog.w("IconDecoder: invalid base64 for $mime icon")
            return null
        }
        if (bytes.size > MAX_BYTES) {
            BepInExLog.w("IconDecoder: $mime icon exceeds 512KB (${bytes.size} bytes)")
            return null
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val width = bounds.outWidth
        val height = bounds.outHeight
        if (width <= 0 || height <= 0) {
            BepInExLog.w("IconDecoder: could not decode $mime icon bounds")
            return null
        }
        if (width.toLong() * height.toLong() > MAX_PIXELS) {
            BepInExLog.w("IconDecoder: $mime icon too large (${width}x${height})")
            return null
        }

        val opts = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            // Straight PNG alpha; BitmapFactory premultiplies once for Canvas.
            inPremultiplied = true
        }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        if (decoded == null) {
            BepInExLog.w("IconDecoder: BitmapFactory returned null for $mime icon")
            return null
        }

        val bitmap = if (decoded.config == Bitmap.Config.ARGB_8888) {
            decoded
        } else {
            val converted = decoded.copy(Bitmap.Config.ARGB_8888, false)
            decoded.recycle()
            converted
        }
        if (bitmap == null) {
            BepInExLog.w("IconDecoder: failed to convert $mime icon to ARGB_8888")
            return null
        }
        bitmap.setHasAlpha(true)
        return bitmap
    }

    /** Strips a data URI prefix when `base64,` is present; otherwise returns [data] unchanged. */
    private fun stripDataUri(data: String): String {
        val marker = data.indexOf("base64", ignoreCase = true)
        if (marker < 0) return data
        val comma = data.indexOf(',', startIndex = marker)
        if (comma < 0) return data
        return data.substring(comma + 1)
    }

    private fun decodeBase64(payload: String): ByteArray? {
        decodeBase64(payload, Base64.DEFAULT)?.let { return it }
        return decodeBase64(payload, Base64.URL_SAFE)
    }

    private fun decodeBase64(payload: String, flags: Int): ByteArray? {
        return try {
            val decoded = Base64.decode(payload, flags)
            if (decoded != null && decoded.isNotEmpty()) decoded else null
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
