package app.storagesaver

import android.Manifest
import android.app.usage.StorageStatsManager
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.provider.MediaStore
import androidx.core.content.ContextCompat

fun requiredPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= 33) {
        arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_AUDIO,
        )
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

fun hasMediaPermission(ctx: Context): Boolean {
    fun ok(p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
    return if (Build.VERSION.SDK_INT >= 33) {
        ok(Manifest.permission.READ_MEDIA_IMAGES) && ok(Manifest.permission.READ_MEDIA_VIDEO)
    } else {
        ok(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
}

object Scanner {
    private const val BIG_VIDEO_BYTES = 100L * 1024L * 1024L

    private class Row(
        val item: MediaItem,
        val w: Int,
        val h: Int,
        val taken: Long,
        val isScreenshot: Boolean,
    )

    fun storage(ctx: Context): StorageInfo {
        return try {
            val ssm = ctx.getSystemService(StorageStatsManager::class.java)
            StorageInfo(
                ssm.getTotalBytes(StorageManager.UUID_DEFAULT),
                ssm.getFreeBytes(StorageManager.UUID_DEFAULT),
            )
        } catch (e: Exception) {
            val stat = StatFs(Environment.getDataDirectory().path)
            StorageInfo(stat.totalBytes, stat.availableBytes)
        }
    }

    fun scan(ctx: Context): ScanResult {
        val cr = ctx.contentResolver
        val images = runCatching { queryImages(cr) }.getOrDefault(emptyList())
        val videos = runCatching { queryVideos(cr) }.getOrDefault(emptyList())
        val audio = runCatching { audioBytes(cr) }.getOrDefault(0L)

        val screenshots = images.filter { it.isScreenshot }
            .map { it.item }
            .sortedByDescending { it.size }

        val dupes = ArrayList<MediaItem>()
        images.filter { !it.isScreenshot && it.taken > 0L && it.item.size > 0L }
            .groupBy { "${it.item.size}_${it.taken}_${it.w}x${it.h}" }
            .values
            .filter { it.size > 1 }
            .forEach { group ->
                // sabse purani (chhoti id) original maani jaati hai, baaki copies
                group.sortedBy { it.item.id }.drop(1).forEach { dupes.add(it.item) }
            }
        dupes.sortByDescending { it.size }

        val big = videos.filter { it.size >= BIG_VIDEO_BYTES }.sortedByDescending { it.size }

        val groups = listOf(
            JunkGroup(JunkType.SCREENSHOT, screenshots),
            JunkGroup(JunkType.DUPLICATE, dupes),
            JunkGroup(JunkType.BIG_VIDEO, big),
        ).filter { it.items.isNotEmpty() }

        val photoBytes = images.sumOf { it.item.size }
        val videoBytes = videos.sumOf { it.size }
        return ScanResult(storage(ctx), CategorySizes(photoBytes, videoBytes, audio), groups)
    }

    private fun queryImages(cr: ContentResolver): List<Row> {
        val out = ArrayList<Row>()
        val base = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val proj = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.RELATIVE_PATH,
        )
        cr.query(base, proj, null, null, null)?.use { c ->
            val iId = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val iName = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val iSize = c.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val iTaken = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val iAdded = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val iW = c.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val iH = c.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            val iPath = c.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)
            while (c.moveToNext()) {
                val id = c.getLong(iId)
                val name = c.getString(iName) ?: "photo"
                val path = c.getString(iPath) ?: ""
                val taken = c.getLong(iTaken)
                val added = c.getLong(iAdded) * 1000L
                val isShot = path.contains("screenshot", ignoreCase = true) ||
                    name.startsWith("Screenshot", ignoreCase = true)
                val item = MediaItem(
                    id = id,
                    uri = ContentUris.withAppendedId(base, id),
                    name = name,
                    size = c.getLong(iSize),
                    isVideo = false,
                    dateMillis = if (taken > 0L) taken else added,
                )
                out.add(Row(item, c.getInt(iW), c.getInt(iH), taken, isShot))
            }
        }
        return out
    }

    private fun queryVideos(cr: ContentResolver): List<MediaItem> {
        val out = ArrayList<MediaItem>()
        val base = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val proj = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_TAKEN,
            MediaStore.Video.Media.DATE_ADDED,
        )
        cr.query(base, proj, null, null, null)?.use { c ->
            val iId = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val iName = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val iSize = c.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val iTaken = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_TAKEN)
            val iAdded = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            while (c.moveToNext()) {
                val id = c.getLong(iId)
                val taken = c.getLong(iTaken)
                val added = c.getLong(iAdded) * 1000L
                out.add(
                    MediaItem(
                        id = id,
                        uri = ContentUris.withAppendedId(base, id),
                        name = c.getString(iName) ?: "video",
                        size = c.getLong(iSize),
                        isVideo = true,
                        dateMillis = if (taken > 0L) taken else added,
                    )
                )
            }
        }
        return out
    }

    private fun audioBytes(cr: ContentResolver): Long {
        var total = 0L
        cr.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Audio.Media.SIZE),
            null,
            null,
            null,
        )?.use { c ->
            val i = c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            while (c.moveToNext()) total += c.getLong(i)
        }
        return total
    }
}
