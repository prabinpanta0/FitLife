package com.example.fitlife.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.example.fitlife.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Service for uploading images to FreeImage.host
 * API Documentation: https://freeimage.host/page/api
 * 
 * SECURITY CONSIDERATIONS:
 * - The API key is embedded in the APK and can be extracted by determined users.
 * - The default key is FreeImage.host's PUBLIC demo key with rate limits.
 * - For production apps, consider:
 *   1. Proxying uploads through your own backend server
 *   2. Using Firebase Remote Config to fetch keys at runtime
 *   3. Implementing your own image hosting with proper authentication
 *   4. Using a dedicated key with domain/app restrictions if available
 * - Current implementation is suitable for development and limited production use.
 * 
 * MEMORY CONSIDERATIONS:
 * - Images are downscaled and compressed before upload to prevent OutOfMemoryError
 * - Maximum raw file size is enforced before processing
 * - Compressed output size is verified before base64 encoding
 */
object ImageUploadService {
    
    private const val TAG = "ImageUploadService"
    private const val API_URL = "https://freeimage.host/api/1/upload"
    
    // NOTE: This key is the public demo key from FreeImage.host documentation.
    // It has rate limits and is intended for development/testing.
    // For production, register your own key or implement backend proxying.
    private val API_KEY = BuildConfig.FREEIMAGE_API_KEY
    
    // Image size limits
    private const val MAX_RAW_FILE_SIZE_BYTES = 15 * 1024 * 1024L // 15 MB max raw file size
    private const val MAX_COMPRESSED_SIZE_BYTES = 5 * 1024 * 1024 // 5 MB max after compression
    private const val TARGET_MAX_DIMENSION = 1920 // Max width or height after downscaling
    private const val INITIAL_JPEG_QUALITY = 85
    private const val MIN_JPEG_QUALITY = 50
    
    /**
     * Upload an image from a URI to FreeImage.host
     * 
     * The image is automatically downscaled and compressed to prevent OutOfMemoryError
     * and reduce upload time/bandwidth.
     * 
     * @param context Android context for content resolver
     * @param imageUri URI of the image to upload
     * @return URL of the uploaded image, or failure with error details
     */
    suspend fun uploadImage(context: Context, imageUri: Uri): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Step 1: Check raw file size before loading into memory
            val fileSize = getFileSize(context, imageUri)
            when {
                fileSize < 0 -> {
                    // Fail-fast: Cannot safely proceed without knowing file size
                    // This prevents potential OOM from unexpectedly large files
                    Log.e(TAG, "Could not determine file size for URI: $imageUri")
                    return@withContext Result.failure(
                        Exception("Could not determine image file size. Please try a different image or check file permissions.")
                    )
                }
                fileSize > MAX_RAW_FILE_SIZE_BYTES -> {
                    Log.w(TAG, "Image file too large: $fileSize bytes (max: $MAX_RAW_FILE_SIZE_BYTES)")
                    return@withContext Result.failure(
                        Exception("Image file is too large (${fileSize / 1024 / 1024}MB). Maximum allowed is ${MAX_RAW_FILE_SIZE_BYTES / 1024 / 1024}MB.")
                    )
                }
                else -> {
                    Log.d(TAG, "Raw file size: $fileSize bytes")
                }
            }
            
            // Step 2: Decode image dimensions without loading full bitmap
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(imageUri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream, null, options)
            } ?: return@withContext Result.failure(Exception("Could not read image dimensions"))
            
            val originalWidth = options.outWidth
            val originalHeight = options.outHeight
            if (originalWidth <= 0 || originalHeight <= 0) {
                return@withContext Result.failure(Exception("Invalid image dimensions"))
            }
            Log.d(TAG, "Original dimensions: ${originalWidth}x${originalHeight}")
            
            // Step 3: Calculate sample size for downscaling
            val sampleSize = calculateInSampleSize(originalWidth, originalHeight, TARGET_MAX_DIMENSION)
            Log.d(TAG, "Using inSampleSize: $sampleSize")
            
            // Step 4: Decode bitmap with downscaling
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565 // Use less memory (2 bytes/pixel vs 4)
            }
            val bitmap = context.contentResolver.openInputStream(imageUri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream, null, decodeOptions)
            } ?: return@withContext Result.failure(Exception("Could not decode image"))
            
            Log.d(TAG, "Decoded bitmap: ${bitmap.width}x${bitmap.height}")
            
            // Step 5: Compress to JPEG with quality reduction if needed
            val compressedBytes = compressWithSizeLimit(bitmap, MAX_COMPRESSED_SIZE_BYTES)
            bitmap.recycle() // Free bitmap memory immediately
            
            if (compressedBytes == null) {
                return@withContext Result.failure(
                    Exception("Could not compress image to acceptable size. Please use a smaller image.")
                )
            }
            Log.d(TAG, "Compressed size: ${compressedBytes.size} bytes")
            
            // Step 6: Base64 encode the compressed image
            val base64Image = Base64.encodeToString(compressedBytes, Base64.NO_WRAP)
            
            // Step 7: Upload to server
            uploadToServer(base64Image)
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "OutOfMemoryError while processing image", e)
            Result.failure(Exception("Image is too large to process. Please use a smaller image."))
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading image", e)
            Result.failure(e)
        }
    }
    
    /**
     * Gets the file size from ContentResolver metadata.
     * Returns -1 if size cannot be determined.
     */
    private fun getFileSize(context: Context, uri: Uri): Long {
        return try {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                afd.length.takeIf { it >= 0 } ?: -1L
            } ?: -1L
        } catch (e: Exception) {
            Log.w(TAG, "Could not determine file size for $uri", e)
            -1L
        }
    }
    
    /**
     * Calculates the optimal inSampleSize for BitmapFactory to downsample an image.
     * Uses power of 2 values as recommended by Android documentation.
     */
    private fun calculateInSampleSize(width: Int, height: Int, maxDimension: Int): Int {
        var inSampleSize = 1
        val largerDimension = maxOf(width, height)
        
        if (largerDimension > maxDimension) {
            val halfLarger = largerDimension / 2
            while (halfLarger / inSampleSize >= maxDimension) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
    
    /**
     * Compresses a bitmap to JPEG, reducing quality iteratively until within size limit.
     * Returns null if unable to achieve target size even at minimum quality.
     */
    private fun compressWithSizeLimit(bitmap: Bitmap, maxSizeBytes: Int): ByteArray? {
        var quality = INITIAL_JPEG_QUALITY
        
        while (quality >= MIN_JPEG_QUALITY) {
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
            val bytes = outputStream.toByteArray()
            
            if (bytes.size <= maxSizeBytes) {
                Log.d(TAG, "Compressed at quality $quality: ${bytes.size} bytes")
                return bytes
            }
            
            Log.d(TAG, "Quality $quality produced ${bytes.size} bytes, reducing quality...")
            quality -= 10
        }
        
        // Final attempt at minimum quality
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, MIN_JPEG_QUALITY, outputStream)
        val bytes = outputStream.toByteArray()
        
        return if (bytes.size <= maxSizeBytes) {
            Log.d(TAG, "Final compress at quality $MIN_JPEG_QUALITY: ${bytes.size} bytes")
            bytes
        } else {
            Log.w(TAG, "Could not compress below $maxSizeBytes bytes (got ${bytes.size})")
            null
        }
    }
    
    /**
     * Uploads the base64-encoded image to FreeImage.host
     * 
     * Streams POST data directly to avoid creating a large in-memory string.
     * The base64 image is URL-encoded in chunks to minimize memory duplication.
     */
    private fun uploadToServer(base64Image: String): Result<String> {
        val url = URL(API_URL)
        val connection = url.openConnection() as HttpURLConnection
        
        try {
            connection.apply {
                requestMethod = "POST"
                doOutput = true
                doInput = true
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                // Use chunked streaming to avoid buffering entire request in memory
                setChunkedStreamingMode(0) // Use default chunk size
                connectTimeout = 30000
                readTimeout = 30000
            }
            
            // Stream POST data directly to connection output stream
            // This avoids creating one large string with the entire encoded payload
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                // Write fixed parameters first
                writer.write("key=")
                writer.write(java.net.URLEncoder.encode(API_KEY, "UTF-8"))
                writer.write("&action=upload&format=json&source=")
                
                // URL-encode and stream the base64 image in chunks to avoid
                // creating a second large string (the URL-encoded version)
                val chunkSize = 8192 // 8KB chunks
                var offset = 0
                while (offset < base64Image.length) {
                    val end = minOf(offset + chunkSize, base64Image.length)
                    val chunk = base64Image.substring(offset, end)
                    writer.write(java.net.URLEncoder.encode(chunk, "UTF-8"))
                    offset = end
                }
                writer.flush()
            }
            
            // Read response
            val responseCode = connection.responseCode
            
            return if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                    reader.readText()
                }
                
                val jsonResponse = JSONObject(response)
                
                if (jsonResponse.has("status_code") && jsonResponse.getInt("status_code") == 200) {
                    val imageData = jsonResponse.getJSONObject("image")
                    val imageUrl = imageData.getString("url")
                    Log.i(TAG, "Upload successful: $imageUrl")
                    Result.success(imageUrl)
                } else {
                    val errorMessage = jsonResponse.optString("error", "Unknown error")
                    Log.e(TAG, "Upload failed: $errorMessage")
                    Result.failure(Exception("Upload failed: $errorMessage"))
                }
            } else {
                val errorResponse = connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { reader ->
                    reader.readText()
                } ?: "Unknown error"
                Log.e(TAG, "HTTP $responseCode: $errorResponse")
                Result.failure(Exception("HTTP $responseCode: $errorResponse"))
            }
        } finally {
            connection.disconnect()
        }
    }
    
    /**
     * Check if the API key is configured
     */
    fun isConfigured(): Boolean {
        return API_KEY.isNotBlank()
    }
}
