package com.gonzaroman.acornix.device

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaExportTest {
    @Test
    fun picksTheTypeFromTheExtension() {
        assertEquals("image/jpeg", MediaExport.mimeFor("photo.JPG"))
        assertEquals("video/webm", MediaExport.mimeFor("stop-motion.webm"))
        assertEquals("application/pdf", MediaExport.mimeFor("invoice.pdf"))
        assertEquals("application/octet-stream", MediaExport.mimeFor("notes"))
    }

    @Test
    fun photosAndVideosGoToTheGallery() {
        assertEquals(MediaExport.PICTURES, MediaExport.folderFor("image/png"))
        assertEquals(MediaExport.MOVIES, MediaExport.folderFor("video/mp4"))
        assertEquals(MediaExport.MUSIC, MediaExport.folderFor("audio/wav"))
        assertEquals(MediaExport.DOWNLOADS, MediaExport.folderFor("image/svg+xml"))
        assertEquals(MediaExport.DOWNLOADS, MediaExport.folderFor("text/csv"))
    }
}
