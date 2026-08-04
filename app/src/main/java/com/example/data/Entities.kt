package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "host_mappings")
data class HostMapping(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val hostname: String,
    val ipAddress: String,
    val isEnabled: Boolean = true
)

@Entity(tableName = "browser_history")
data class BrowserHistory(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val url: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "bookmarks")
data class Bookmark(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val url: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "downloads")
data class DownloadItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val fileName: String,
    val fileUrl: String,
    val filePath: String,
    val mimeType: String,
    val fileSize: Long,
    val downloadStatus: String, // "DOWNLOADING", "COMPLETED", "FAILED"
    val timestamp: Long = System.currentTimeMillis()
)

