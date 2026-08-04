package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface HostMappingDao {
    @Query("SELECT * FROM host_mappings ORDER BY hostname ASC")
    fun getAllMappingsFlow(): Flow<List<HostMapping>>

    @Query("SELECT * FROM host_mappings WHERE isEnabled = 1")
    suspend fun getEnabledMappings(): List<HostMapping>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMapping(mapping: HostMapping)

    @Update
    suspend fun updateMapping(mapping: HostMapping)

    @Delete
    suspend fun deleteMapping(mapping: HostMapping)

    @Query("DELETE FROM host_mappings WHERE id = :id")
    suspend fun deleteById(id: Int)
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM browser_history ORDER BY timestamp DESC LIMIT 200")
    fun getHistoryFlow(): Flow<List<BrowserHistory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(entry: BrowserHistory)

    @Query("DELETE FROM browser_history WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM browser_history")
    suspend fun clearHistory()

    @Query("DELETE FROM browser_history WHERE url LIKE :pattern")
    suspend fun deleteHistoryMatching(pattern: String)
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY timestamp DESC")
    fun getBookmarksFlow(): Flow<List<Bookmark>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: Bookmark)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("SELECT COUNT(*) FROM bookmarks WHERE url = :url")
    suspend fun countBookmarkByUrl(url: String): Int

    @Query("DELETE FROM bookmarks WHERE url = :url")
    suspend fun deleteByUrl(url: String)
}

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY timestamp DESC")
    fun getAllDownloadsFlow(): Flow<List<DownloadItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownload(download: DownloadItem): Long

    @Update
    suspend fun updateDownload(download: DownloadItem)

    @Delete
    suspend fun deleteDownload(download: DownloadItem)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun getDownloadById(id: Int): DownloadItem?
}

