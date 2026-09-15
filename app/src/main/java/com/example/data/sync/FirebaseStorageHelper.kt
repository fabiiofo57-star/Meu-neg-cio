package com.example.data.sync

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

class FirebaseStorageHelper(private val context: Context) {

    private val isFirebaseAvailable: Boolean
        get() = try {
            FirebaseStorage.getInstance()
            true
        } catch (e: Exception) {
            false
        }

    /**
     * Persiste a imagem localmente, gera uma versão portátil Base64 para sincronização entre aparelhos
     * e tenta upload para o Firebase Storage se configurado.
     */
    suspend fun saveImageLocallyAndSync(
        uri: Uri,
        fileName: String,
        userId: String
    ): String = withContext(Dispatchers.IO) {
        try {
            // 1. Carrega e comprime a imagem de forma otimizada para avatar/logo
            val inputStream = context.contentResolver.openInputStream(uri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (originalBitmap == null) {
                return@withContext uri.toString()
            }

            // Redimensiona proporcionalmente (1080px para capas panorâmicas e 480px para logos/perfil)
            val isCover = fileName.startsWith("cover_")
            val maxDim = if (isCover) 1080 else 480
            val width = originalBitmap.width
            val height = originalBitmap.height
            val scale = if (width > maxDim || height > maxDim) {
                val ratio = width.toFloat() / height.toFloat()
                if (ratio > 1) {
                    val targetW = maxDim
                    val targetH = (maxDim / ratio).toInt().coerceAtLeast(1)
                    Bitmap.createScaledBitmap(originalBitmap, targetW, targetH, true)
                } else {
                    val targetH = maxDim
                    val targetW = (maxDim * ratio).toInt().coerceAtLeast(1)
                    Bitmap.createScaledBitmap(originalBitmap, targetW, targetH, true)
                }
            } else {
                originalBitmap
            }

            val baos = ByteArrayOutputStream()
            scale.compress(Bitmap.CompressFormat.JPEG, 80, baos)
            val imageBytes = baos.toByteArray()

            // 2. Salva no armazenamento local deste aparelho
            val localDir = File(context.filesDir, "business_logos").apply { if (!exists()) mkdirs() }
            val localFile = File(localDir, "$fileName.jpg")
            FileOutputStream(localFile).use { it.write(imageBytes) }

            // Gera Data URL Base64 para portabilidade instantânea entre aparelhos
            val base64DataUrl = "data:image/jpeg;base64," + Base64.encodeToString(imageBytes, Base64.NO_WRAP)

            // 3. Se o Firebase Storage estiver ativo, tenta upload na nuvem
            if (isFirebaseAvailable) {
                try {
                    val storageRef = FirebaseStorage.getInstance().reference
                    val imageRef = storageRef.child("businesses/$userId/$fileName.jpg")
                    imageRef.putBytes(imageBytes).await()
                    val downloadUrl = imageRef.downloadUrl.await().toString()
                    return@withContext downloadUrl
                } catch (e: Exception) {
                    Log.w("FirebaseStorageHelper", "Firebase Storage não respondeu, usando Base64 portátil: ${e.message}")
                }
            }

            // Retorna o Base64 que é persistido no Firestore e interpretado por qualquer aparelho
            return@withContext base64DataUrl
        } catch (e: Exception) {
            Log.e("FirebaseStorageHelper", "Erro ao processar imagem: ${e.message}", e)
            return@withContext uri.toString()
        }
    }

    /**
     * Quando o aparelho B baixa dados da nuvem com Data URL Base64, grava no arquivo local
     * para cache de altíssima performance.
     */
    fun decodeAndCacheIfBase64(uriString: String?, fileName: String): String? {
        if (uriString.isNullOrBlank()) return null
        if (!uriString.startsWith("data:image/")) return uriString

        return try {
            val commaIdx = uriString.indexOf(',')
            if (commaIdx == -1) return uriString
            val base64Part = uriString.substring(commaIdx + 1)
            val bytes = Base64.decode(base64Part, Base64.DEFAULT)
            val localDir = File(context.filesDir, "business_logos").apply { if (!exists()) mkdirs() }
            val localFile = File(localDir, "$fileName.jpg")
            FileOutputStream(localFile).use { it.write(bytes) }
            Uri.fromFile(localFile).toString()
        } catch (e: Exception) {
            Log.e("FirebaseStorageHelper", "Erro ao gravar cache de Base64", e)
            uriString
        }
    }
}
