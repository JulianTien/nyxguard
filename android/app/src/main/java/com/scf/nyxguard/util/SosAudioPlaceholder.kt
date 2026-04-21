package com.scf.nyxguard.util

import android.content.Context
import java.io.File

object SosAudioPlaceholder {

    fun create(context: Context, prefix: String): String? {
        return runCatching {
            val directory = File(context.filesDir, "sos-audio").apply { mkdirs() }
            val file = File(directory, "${prefix}_${System.currentTimeMillis()}.m4a")
            if (!file.exists()) {
                file.writeBytes(ByteArray(0))
            }
            file.absolutePath
        }.getOrNull()
    }
}
