package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class Repository(private val database: AppDatabase) {
    private val hostMappingDao = database.hostMappingDao()
    private val historyDao = database.historyDao()
    private val bookmarkDao = database.bookmarkDao()
    private val downloadDao = database.downloadDao()

    val allMappings: Flow<List<HostMapping>> = hostMappingDao.getAllMappingsFlow()
    val history: Flow<List<BrowserHistory>> = historyDao.getHistoryFlow()
    val bookmarks: Flow<List<Bookmark>> = bookmarkDao.getBookmarksFlow()
    val downloads: Flow<List<DownloadItem>> = downloadDao.getAllDownloadsFlow()

    suspend fun insertDownload(download: DownloadItem): Long {
        return downloadDao.insertDownload(download)
    }

    suspend fun updateDownload(download: DownloadItem) {
        downloadDao.updateDownload(download)
    }

    suspend fun deleteDownload(download: DownloadItem) {
        downloadDao.deleteDownload(download)
    }

    suspend fun deleteDownloadById(id: Int) {
        downloadDao.deleteById(id)
    }

    suspend fun getDownloadById(id: Int): DownloadItem? {
        return downloadDao.getDownloadById(id)
    }

    suspend fun getEnabledMappings(): List<HostMapping> {
        return hostMappingDao.getEnabledMappings()
    }

    suspend fun insertMapping(mapping: HostMapping) {
        hostMappingDao.insertMapping(mapping)
    }

    suspend fun deleteMapping(mapping: HostMapping) {
        hostMappingDao.deleteMapping(mapping)
    }

    suspend fun deleteMappingById(id: Int) {
        hostMappingDao.deleteById(id)
    }

    suspend fun updateMapping(mapping: HostMapping) {
        hostMappingDao.updateMapping(mapping)
    }

    suspend fun addHistory(entry: BrowserHistory) {
        historyDao.insertHistory(entry)
    }

    suspend fun clearHistory() {
        historyDao.clearHistory()
    }

    suspend fun deleteHistoryMatching(pattern: String) {
        historyDao.deleteHistoryMatching(pattern)
    }

    suspend fun addBookmark(bookmark: Bookmark) {
        if (bookmarkDao.countBookmarkByUrl(bookmark.url) == 0) {
            bookmarkDao.insertBookmark(bookmark)
        }
    }

    suspend fun removeBookmarkByUrl(url: String) {
        bookmarkDao.deleteByUrl(url)
    }

    suspend fun deleteBookmarkById(id: Int) {
        bookmarkDao.deleteById(id)
    }

    suspend fun isBookmarked(url: String): Boolean {
        return bookmarkDao.countBookmarkByUrl(url) > 0
    }

    // Seed default eOffice hosts if none exist
    suspend fun seedDefaultsIfEmpty() {
        val currentMappings = allMappings.first()
        if (currentMappings.isEmpty()) {
            hostMappingDao.insertMapping(
                HostMapping(
                    hostname = "eoffsigner.eoffice.gov.in",
                    ipAddress = "127.0.0.1",
                    isEnabled = true
                )
            )
            hostMappingDao.insertMapping(
                HostMapping(
                    hostname = "districts.upeoffice.gov.in",
                    ipAddress = "192.168.39.110",
                    isEnabled = true
                )
            )
            // Seed a few useful bookmarks
            bookmarkDao.insertBookmark(
                Bookmark(
                    title = "UP eOffice (Districts)",
                    url = "https://districts.upeoffice.gov.in"
                )
            )
            bookmarkDao.insertBookmark(
                Bookmark(
                    title = "eOffice Signer Utility",
                    url = "https://eoffsigner.eoffice.gov.in"
                )
            )
            bookmarkDao.insertBookmark(
                Bookmark(
                    title = "Google Search",
                    url = "https://www.google.com"
                )
            )
        }
    }
}
