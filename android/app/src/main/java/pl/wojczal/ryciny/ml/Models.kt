package pl.wojczal.ryciny.ml

import android.content.Context
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/** Maps a model straight out of the APK (stored uncompressed, see `noCompress` in build.gradle.kts). */
fun Context.mapAsset(path: String): MappedByteBuffer =
    assets.openFd(path).use { fd ->
        FileInputStream(fd.fileDescriptor).channel.use {
            it.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
        }
    }
