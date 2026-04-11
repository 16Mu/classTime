package com.wind.ggbond.classtime.util

import android.content.Context
import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File

/**
 * MediaFileManager 单元测试
 * 
 * 测试文件复制、删除和清理功能
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MediaFileManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var mediaFileManager: MediaFileManager
    private lateinit var filesDir: File

    @Before
    fun setup() {
        mockkObject(AppLogger)
        every { AppLogger.d(any<String>(), any<String>()) } returns Unit
        every { AppLogger.e(any<String>(), any<String>()) } returns Unit
        every { AppLogger.w(any<String>(), any<String>()) } returns Unit
        every { AppLogger.i(any<String>(), any<String>()) } returns Unit
        filesDir = tempFolder.newFolder("app_files")
        context = mockk<Context>(relaxed = true)
        every { context.filesDir } returns filesDir
        mediaFileManager = MediaFileManager(context)
    }

    @Test
    fun `test copyMediaToPrivateStorage creates file with correct extension`() = runTest {
        // 准备测试数据
        val testContent = "test image content".toByteArray()
        val sourceUri = Uri.parse("content://test/image.jpg")
        
        // Mock ContentResolver
        val contentResolver = mockk<android.content.ContentResolver>(relaxed = true)
        every { context.contentResolver } returns contentResolver
        every { contentResolver.openInputStream(sourceUri) } returns ByteArrayInputStream(testContent)
        
        // 执行复制
        val localUri = mediaFileManager.copyMediaToPrivateStorage(sourceUri, "jpg")
        
        // 验证结果
        assertNotNull("Local URI should not be null", localUri)
        assertTrue("File should exist", File(localUri!!.path!!).exists())
        assertTrue("File should have .jpg extension", localUri.path!!.endsWith(".jpg"))
        
        // 验证文件内容
        val copiedContent = File(localUri.path!!).readBytes()
        assertArrayEquals("File content should match", testContent, copiedContent)
    }

    @Test
    fun `test deleteBackgroundFile removes file successfully`() = runTest {
        // 创建测试文件
        val backgroundsDir = File(filesDir, "backgrounds")
        backgroundsDir.mkdirs()
        val testFile = File(backgroundsDir, "test.jpg")
        testFile.writeText("test content")
        
        assertTrue("Test file should exist before deletion", testFile.exists())
        
        // 删除文件
        val fileUri = Uri.fromFile(testFile)
        val deleted = mediaFileManager.deleteBackgroundFile(fileUri)
        
        // 验证结果
        assertTrue("Delete should return true", deleted)
        assertFalse("File should not exist after deletion", testFile.exists())
    }

    @Test
    fun `test clearAllBackgrounds removes all files`() = runTest {
        // 创建多个测试文件
        val backgroundsDir = File(filesDir, "backgrounds")
        backgroundsDir.mkdirs()
        
        val file1 = File(backgroundsDir, "test1.jpg")
        val file2 = File(backgroundsDir, "test2.gif")
        val file3 = File(backgroundsDir, "test3.mp4")
        
        file1.writeText("content1")
        file2.writeText("content2")
        file3.writeText("content3")
        
        assertTrue("All files should exist", file1.exists() && file2.exists() && file3.exists())
        
        // 清理所有文件
        val deletedCount = mediaFileManager.clearAllBackgrounds()
        
        // 验证结果
        assertEquals("Should delete 3 files", 3, deletedCount)
        assertFalse("File 1 should not exist", file1.exists())
        assertFalse("File 2 should not exist", file2.exists())
        assertFalse("File 3 should not exist", file3.exists())
    }

    @Test
    fun `test getBackgroundFileSize returns correct size`() = runTest {
        val backgroundsDir = File(filesDir, "backgrounds")
        backgroundsDir.mkdirs()
        val testFile = File(backgroundsDir, "test.jpg")
        val testContent = "test content with some length"
        testFile.writeText(testContent)
        
        val fileUri = Uri.fromFile(testFile)
        val size = mediaFileManager.getBackgroundFileSize(fileUri)
        
        assertEquals("File size should match", testContent.length.toLong(), size)
    }

    @Test
    fun `test getTotalBackgroundsSize calculates total correctly`() = runTest {
        val backgroundsDir = File(filesDir, "backgrounds")
        backgroundsDir.mkdirs()
        
        val file1 = File(backgroundsDir, "test1.jpg")
        val file2 = File(backgroundsDir, "test2.gif")
        
        val content1 = "content1"
        val content2 = "content2 with more text"
        
        file1.writeText(content1)
        file2.writeText(content2)
        
        val totalSize = mediaFileManager.getTotalBackgroundsSize()
        
        val expectedSize = content1.length.toLong() + content2.length.toLong()
        assertEquals("Total size should match sum of all files", expectedSize, totalSize)
    }

    @Test
    fun `test fileExists returns true for existing file`() = runTest {
        val backgroundsDir = File(filesDir, "backgrounds")
        backgroundsDir.mkdirs()
        val testFile = File(backgroundsDir, "test.jpg")
        testFile.writeText("test")
        
        val fileUri = Uri.fromFile(testFile)
        val exists = mediaFileManager.fileExists(fileUri)
        
        assertTrue("File should exist", exists)
    }

    @Test
    fun `test fileExists returns false for non-existing file`() = runTest {
        val backgroundsDir = File(filesDir, "backgrounds")
        val nonExistingFile = File(backgroundsDir, "nonexisting.jpg")
        val fileUri = Uri.fromFile(nonExistingFile)
        
        val exists = mediaFileManager.fileExists(fileUri)
        
        assertFalse("File should not exist", exists)
    }
}
