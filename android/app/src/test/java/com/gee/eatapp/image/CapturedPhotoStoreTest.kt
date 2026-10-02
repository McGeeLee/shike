package com.gee.eatapp.image

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CapturedPhotoStoreTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun deletesOnlyOwnedCaptureNames() {
        val store = CapturedPhotoStore(temporary.root)
        val capture = store.create().apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val unrelated = File(capture.parentFile, "user-photo.jpg").apply { writeText("keep") }
        val outside = temporary.newFile("outside.jpg")
        store.delete("../outside.jpg")
        store.delete(unrelated.name)
        store.delete(capture.name)
        assertFalse(capture.exists())
        assertTrue(unrelated.exists())
        assertTrue(outside.exists())
    }

    @Test fun staleCleanupPreservesPendingAndRecentCaptures() {
        val store = CapturedPhotoStore(temporary.root)
        val now = System.currentTimeMillis()
        val abandoned = store.create().apply { setLastModified(now - 2 * 86_400_000L) }
        val pending = store.create().apply { setLastModified(now - 2 * 86_400_000L) }
        val recent = store.create()
        store.cleanStale(pending.name, now)
        assertFalse(abandoned.exists())
        assertTrue(pending.exists())
        assertTrue(recent.exists())
    }
}
