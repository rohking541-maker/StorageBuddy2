package app.storagesaver

import android.net.Uri

enum class JunkType(val title: String, val emoji: String, val hint: String) {
    SCREENSHOT("Screenshots", "📸", "Purane screenshots, aksar ek baar dekh ke kaam khatam"),
    DUPLICATE("Duplicate photos", "👯", "Same photo ki extra copies (original wali safe rehti hai)"),
    BIG_VIDEO("Bade videos", "🎬", "100 MB se bade videos, dekh ke decide kar"),
}

data class MediaItem(
    val id: Long,
    val uri: Uri,
    val name: String,
    val size: Long,
    val isVideo: Boolean,
    val dateMillis: Long,
) {
    val key: String get() = (if (isVideo) "v" else "i") + id
}

data class StorageInfo(val total: Long, val free: Long) {
    val used: Long get() = (total - free).coerceAtLeast(0L)
}

data class CategorySizes(val photos: Long, val videos: Long, val audio: Long)

data class JunkGroup(val type: JunkType, val items: List<MediaItem>)

data class ScanResult(
    val storage: StorageInfo,
    val sizes: CategorySizes,
    val junk: List<JunkGroup>,
) {
    val junkBytes: Long get() = junk.sumOf { g -> g.items.sumOf { it.size } }
}
