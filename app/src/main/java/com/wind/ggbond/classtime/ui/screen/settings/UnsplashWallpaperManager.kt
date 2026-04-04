package com.wind.ggbond.classtime.ui.screen.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import coil3.ImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.toBitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UnsplashWallpaperManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val imageLoader by lazy { ImageLoader(context) }

    companion object {
        private const val TAG = "UnsplashManager"
        private const val UNSPLASH_API_BASE = "https://api.unsplash.com"
        private const val ACCESS_KEY = "demo"
        
        data class UnsplashPhoto(
            val id: String,
            val urls: PhotoUrls,
            val user: User?,
            val description: String?
        )
        
        data class PhotoUrls(
            val raw: String,
            val full: String,
            val regular: String,
            val small: String,
            val thumb: String
        )
        
        data class User(
            val name: String,
            val username: String
        )
    }

    suspend fun searchUnsplashPhotos(query: String, count: Int = 10): List<UnsplashPhoto> = withContext(Dispatchers.IO) {
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "$UNSPLASH_API_BASE/search/photos?query=$encodedQuery&per_page=$count&orientation=landscape"
            
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Client-ID $ACCESS_KEY")
                .build()
            
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: return@withContext emptyList()
            
            val json = JSONObject(responseBody)
            val resultsArray = json.optJSONArray("results") ?: return@withContext emptyList()
            
            val photos = mutableListOf<UnsplashPhoto>()
            for (i in 0 until resultsArray.length()) {
                val photoObj = resultsArray.getJSONObject(i)
                val urlsObj = photoObj.getJSONObject("urls")
                
                photos.add(UnsplashPhoto(
                    id = photoObj.getString("id"),
                    urls = PhotoUrls(
                        raw = urlsObj.getString("raw"),
                        full = urlsObj.getString("full"),
                        regular = urlsObj.getString("regular"),
                        small = urlsObj.optString("small", ""),
                        thumb = urlsObj.optString("thumb", "")
                    ),
                    user = if (photoObj.has("user")) {
                        val userObj = photoObj.getJSONObject("user")
                        User(
                            name = userObj.optString("name", ""),
                            username = userObj.optString("username", "")
                        )
                    } else null,
                    description = photoObj.optString("description", null)
                ))
            }
            
            Log.d(TAG, "搜索到 ${photos.size} 张壁纸: $query")
            photos
        } catch (e: Exception) {
            Log.e(TAG, "搜索Unsplash照片失败: $query", e)
            emptyList()
        }
    }

    suspend fun getRandomUnsplashWallpapers(count: Int = 5): List<UnsplashPhoto> = withContext(Dispatchers.IO) {
        try {
            val url = "$UNSPLASH_API_BASE/photos/random?count=$count&orientation=landscape&featured=true"
            
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Client-ID $ACCESS_KEY")
                .build()
            
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: return@withContext emptyList()
            
            val jsonArray = if (responseBody.startsWith("[")) {
                org.json.JSONArray(responseBody)
            } else {
                org.json.JSONArray().put(org.json.JSONObject(responseBody))
            }
            
            val photos = mutableListOf<UnsplashPhoto>()
            for (i in 0 until jsonArray.length()) {
                val photoObj = jsonArray.getJSONObject(i)
                val urlsObj = photoObj.getJSONObject("urls")
                
                photos.add(UnsplashPhoto(
                    id = photoObj.getString("id"),
                    urls = PhotoUrls(
                        raw = urlsObj.getString("raw"),
                        full = urlsObj.getString("full"),
                        regular = urlsObj.getString("regular"),
                        small = urlsObj.optString("small", ""),
                        thumb = urlsObj.optString("thumb", "")
                    ),
                    user = if (photoObj.has("user")) {
                        val userObj = photoObj.getJSONObject("user")
                        User(
                            name = userObj.optString("name", ""),
                            username = userObj.optString("username", "")
                        )
                    } else null,
                    description = photoObj.optString("description", null)
                ))
            }
            
            Log.d(TAG, "获取到 ${photos.size} 张随机壁纸")
            photos
        } catch (e: Exception) {
            Log.e(TAG, "获取随机壁纸失败", e)
            emptyList()
        }
    }

    suspend fun getFeaturedWallpapers(count: Int = 10): List<UnsplashPhoto> {
        val queries = listOf("nature", "abstract", "minimal", "gradient", "texture")
        val allPhotos = mutableListOf<UnsplashPhoto>()
        
        queries.forEachIndexed { index, query ->
            val perPage = count / queries.size + if (index < count % queries.size) 1 else 0
            val photos = searchUnsplashPhotos(query, perPage)
            allPhotos.addAll(photos)
        }
        
        return allPhotos.shuffled().take(count)
    }

    suspend fun downloadPhoto(photo: UnsplashPhoto, quality: String = "regular"): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val imageUrl = when (quality) {
                "full" -> photo.urls.full
                "small" -> photo.urls.small
                "thumb" -> photo.urls.thumb
                else -> photo.urls.regular
            }
            
            val request = ImageRequest.Builder(context)
                .data(imageUrl)
                .memoryCachePolicy(CachePolicy.DISABLED)
                .diskCachePolicy(CachePolicy.DISABLED)
                .build()
            
            val result = imageLoader.execute(request)
            result.image?.toBitmap()
        } catch (e: Exception) {
            Log.e(TAG, "下载照片失败: ${photo.id}", e)
            null
        }
    }

    suspend fun saveUnsplashAsBackground(photo: Any, name: String? = null): Boolean = withContext(Dispatchers.IO) {
        try {
            val unsplashPhoto = photo as? UnsplashPhoto ?: return@withContext false
            
            val bitmap = downloadPhoto(unsplashPhoto) ?: return@withContext false
            
            val fileName = name ?: "unsplash_${unsplashPhoto.id}_${System.currentTimeMillis()}.jpg"
            val file = File(context.cacheDir, fileName)
            
            FileOutputStream(file).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)
                fos.flush()
            }
            
            Log.d(TAG, "保存Unsplash壁纸成功: ${file.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "保存Unsplash壁纸失败", e)
            false
        }
    }
}
