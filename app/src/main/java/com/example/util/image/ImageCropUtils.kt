package com.example.util.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.math.max
import kotlin.math.min

object ImageCropUtils {

    /**
     * Carrega um Bitmap a partir de URI (content/file) ou string Base64 (Data URL),
     * corrigindo a orientação EXIF e limitando a resolução máxima para economizar memória.
     */
    suspend fun loadBitmap(
        context: Context,
        source: String,
        maxDimension: Int = 1920
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            if (source.isBlank()) return@withContext null

            // Caso 1: Imagem codificada em Base64 (data:image/...)
            if (source.startsWith("data:image/")) {
                val commaIdx = source.indexOf(',')
                if (commaIdx == -1) return@withContext null
                val base64Data = source.substring(commaIdx + 1)
                val bytes = Base64.decode(base64Data, Base64.DEFAULT)

                // Lê dimensões
                val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOptions)
                val (outW, outH) = boundsOptions.outWidth to boundsOptions.outHeight
                if (outW <= 0 || outH <= 0) return@withContext null

                var sample = 1
                while (outW / sample > maxDimension || outH / sample > maxDimension) {
                    sample *= 2
                }

                val decodeOptions = BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                return@withContext BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
            }

            // Caso 2: URI (content:// ou file://)
            val uri = Uri.parse(source)

            // Lê dimensões primeiro
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            openStream(context, uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOptions)
            }
            val (outW, outH) = boundsOptions.outWidth to boundsOptions.outHeight
            if (outW <= 0 || outH <= 0) return@withContext null

            var sample = 1
            while (outW / sample > maxDimension || outH / sample > maxDimension) {
                sample *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            val bitmap = openStream(context, uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return@withContext null

            // Corrige orientação EXIF
            var exifOrientation = ExifInterface.ORIENTATION_NORMAL
            try {
                openStream(context, uri)?.use { stream ->
                    val exif = ExifInterface(stream)
                    exifOrientation = exif.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                }
            } catch (_: Throwable) { }

            val matrix = Matrix()
            when (exifOrientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            }

            if (matrix.isIdentity) {
                bitmap
            } else {
                val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                if (rotated != bitmap) {
                    bitmap.recycle()
                }
                rotated
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun openStream(context: Context, uri: Uri): InputStream? {
        return try {
            context.contentResolver.openInputStream(uri)
        } catch (e: Exception) {
            if (uri.path != null) {
                val file = File(uri.path!!)
                if (file.exists()) file.inputStream() else null
            } else null
        }
    }

    /**
     * Recorta o bitmap precisamente de acordo com a área do viewport da tela,
     * levando em conta a rotação do usuário, zoom e posição de pan.
     */
    suspend fun cropBitmap(
        context: Context,
        sourceBitmap: Bitmap,
        extraRotationDegrees: Float,
        viewportWidthPx: Float,
        viewportHeightPx: Float,
        displayedImageWidthPx: Float,
        displayedImageHeightPx: Float,
        panOffsetX: Float,
        panOffsetY: Float,
        prefix: String = "cropped"
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            // 1. Aplica rotação do usuário (se houver)
            val workingBitmap = if (extraRotationDegrees % 360f != 0f) {
                val rotMatrix = Matrix().apply { postRotate(extraRotationDegrees) }
                Bitmap.createBitmap(sourceBitmap, 0, 0, sourceBitmap.width, sourceBitmap.height, rotMatrix, true)
            } else {
                sourceBitmap
            }

            val bw = workingBitmap.width.toFloat()
            val bh = workingBitmap.height.toFloat()

            // 2. Calcula posição da imagem relativa ao viewport
            // O centro da imagem está em (viewportWidthPx/2 + panOffsetX, viewportHeightPx/2 + panOffsetY)
            val imgLeft = (viewportWidthPx - displayedImageWidthPx) / 2f + panOffsetX
            val imgTop = (viewportHeightPx - displayedImageHeightPx) / 2f + panOffsetY

            // Posição do viewport em relação à imagem renderizada na tela
            val viewLeftInImage = (0f - imgLeft).coerceAtLeast(0f)
            val viewTopInImage = (0f - imgTop).coerceAtLeast(0f)

            // Razão de proporção da tela para o bitmap original
            val scaleX = bw / displayedImageWidthPx
            val scaleY = bh / displayedImageHeightPx

            // Converte as coordenadas para o bitmap original
            val cropX = (viewLeftInImage * scaleX).toInt().coerceIn(0, workingBitmap.width - 1)
            val cropY = (viewTopInImage * scaleY).toInt().coerceIn(0, workingBitmap.height - 1)

            val cropW = (viewportWidthPx * scaleX).toInt().coerceIn(1, workingBitmap.width - cropX)
            val cropH = (viewportHeightPx * scaleY).toInt().coerceIn(1, workingBitmap.height - cropY)

            val cropped = Bitmap.createBitmap(workingBitmap, cropX, cropY, cropW, cropH)

            if (workingBitmap != sourceBitmap && workingBitmap != cropped) {
                workingBitmap.recycle()
            }

            // 3. Salva em cache local do app
            val cacheDir = File(context.cacheDir, "cropped_images").apply { if (!exists()) mkdirs() }
            val outputFile = File(cacheDir, "${prefix}_${System.currentTimeMillis()}.jpg")
            FileOutputStream(outputFile).use { out ->
                cropped.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }

            cropped.recycle()
            Uri.fromFile(outputFile)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
