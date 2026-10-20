package app.storagesaver

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Phase { NEEDS_PERMISSION, SCANNING, READY }

class MainVm(app: Application) : AndroidViewModel(app) {

    var phase by mutableStateOf(Phase.SCANNING)
    var result by mutableStateOf<ScanResult?>(null)

    /** true = hatane ke liye chuna gaya (dull), false = rakha ja raha hai (chamakta) */
    val marked = mutableStateMapOf<String, Boolean>()

    /** delete ke baad kitni jagah free hui (celebration animation ke liye) */
    var celebrate by mutableStateOf<Long?>(null)

    var pendingBatches: ArrayDeque<List<Uri>> = ArrayDeque()
    private var deletingBytes = 0L

    fun onPermissionResult(granted: Boolean) {
        if (granted) scan() else phase = Phase.NEEDS_PERMISSION
    }

    fun scan() {
        phase = Phase.SCANNING
        viewModelScope.launch {
            val t0 = System.currentTimeMillis()
            val app = getApplication<Application>()
            val r = withContext(Dispatchers.IO) {
                runCatching { Scanner.scan(app) }.getOrElse {
                    ScanResult(Scanner.storage(app), CategorySizes(0L, 0L, 0L), emptyList())
                }
            }
            val wait = 1400L - (System.currentTimeMillis() - t0)
            if (wait > 0L) delay(wait)
            marked.clear()
            // Screenshots aur duplicates pehle se chune hote hain; bade videos tu khud decide karega
            r.junk.forEach { g ->
                g.items.forEach { marked[it.key] = g.type != JunkType.BIG_VIDEO }
            }
            result = r
            phase = Phase.READY
        }
    }

    fun toggle(key: String) {
        marked[key] = marked[key] != true
    }

    fun setGroup(group: JunkGroup, value: Boolean) {
        group.items.forEach { marked[it.key] = value }
    }

    fun junkSelected(): List<MediaItem> =
        result?.junk?.flatMap { it.items }?.filter { marked[it.key] == true } ?: emptyList()

    fun beginDelete() {
        val sel = junkSelected()
        deletingBytes = sel.sumOf { it.size }
        pendingBatches = ArrayDeque(sel.map { it.uri }.chunked(1000))
    }

    fun onDeleteDone() {
        celebrate = deletingBytes
        scan()
    }

    fun onDeleteCancelled() {
        pendingBatches.clear()
    }
}
