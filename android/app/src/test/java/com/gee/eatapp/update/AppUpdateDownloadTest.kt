package com.gee.eatapp.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class AppUpdateDownloadTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val payload = "verified APK fixture".toByteArray()
    private val release = AppRelease(
        versionName = "2.4.1",
        releaseUrl = "https://github.com/McGeeLee/shike/releases/tag/v2.4.1",
        releaseNotes = "",
        apkName = "shike-v2.4.1.apk",
        apkUrl = "https://github.com/McGeeLee/shike/releases/download/v2.4.1/shike-v2.4.1.apk",
        checksumUrl = "https://github.com/McGeeLee/shike/releases/download/v2.4.1/shike-v2.4.1.apk.sha256",
    )

    @Test
    fun cancelledBlockedDownloadCannotDeleteCompletedRetry() = runBlocking {
        val directory = temporaryFolder.newFolder("updates")
        val firstReadEntered = CountDownLatch(1)
        val resumeFirstRead = CountDownLatch(1)
        val apkRequests = AtomicInteger()
        val client = client {
            if (apkRequests.incrementAndGet() != 1) {
                ByteArrayInputStream(payload)
            } else {
                object : InputStream() {
                    override fun read(): Int = error("Expected a buffered read")

                    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                        firstReadEntered.countDown()
                        check(resumeFirstRead.await(10, TimeUnit.SECONDS))
                        payload.copyInto(buffer, offset)
                        return payload.size
                    }
                }
            }
        }
        val first = async { client.downloadRelease(directory, release) {} }
        try {
            withContext(Dispatchers.IO) {
                assertTrue(firstReadEntered.await(10, TimeUnit.SECONDS))
            }
            first.cancel()
            val retry = client.downloadRelease(directory, release) {}
            assertTrue(retry.exists())
            resumeFirstRead.countDown()
            first.join()

            assertTrue("The cancelled attempt must not delete its retry's APK", retry.exists())
            assertArrayEquals(payload, retry.readBytes())
            assertEquals(listOf(retry.name), directory.listFiles()!!.map { it.name })
        } finally {
            resumeFirstRead.countDown()
            first.cancel()
            first.join()
        }
    }

    @Test
    fun verificationFailureCleansItsFilesAndPreservesOtherDownloads() = runBlocking {
        val directory = temporaryFolder.newFolder("updates")
        val client = client { ByteArrayInputStream(payload) }
        val existing = client.downloadRelease(directory, release) {}
        val second = client.downloadRelease(directory, release) {}
        assertNotEquals(existing.absolutePath, second.absolutePath)

        val error = runCatching {
            client.downloadRelease(directory, release) { throw IOException("Invalid signing certificate") }
        }.exceptionOrNull()

        assertTrue(error is IOException)
        assertTrue(existing.exists())
        assertTrue(second.exists())
        assertFalse(directory.listFiles()!!.any { it.extension == "part" })
        assertEquals(2, directory.listFiles()!!.size)
    }

    private fun client(apkStream: () -> InputStream): AppUpdateClient {
        val checksum = MessageDigest.getInstance("SHA-256").digest(payload)
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        return AppUpdateClient { value ->
            object : HttpURLConnection(URL(value)) {
                override fun connect() = Unit
                override fun disconnect() = Unit
                override fun usingProxy(): Boolean = false
                override fun getResponseCode(): Int = HTTP_OK
                override fun getContentLengthLong(): Long = payload.size.toLong()
                override fun getInputStream(): InputStream = if (value.endsWith(".sha256")) {
                    ByteArrayInputStream(checksum.toByteArray())
                } else {
                    apkStream()
                }
            }
        }
    }
}
