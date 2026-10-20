package app.storagesaver

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels

class MainActivity : ComponentActivity() {

    private val vm: MainVm by viewModels()
    private lateinit var permLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var deleteLauncher: ActivityResultLauncher<IntentSenderRequest>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )

        permLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) {
            vm.onPermissionResult(hasMediaPermission(this))
        }

        deleteLauncher = registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) { res ->
            if (res.resultCode == RESULT_OK) nextBatch() else vm.onDeleteCancelled()
        }

        setContent {
            StorageTheme {
                App(
                    vm = vm,
                    onAllow = { permLauncher.launch(requiredPermissions()) },
                    onAppSettings = { openAppSettings() },
                    onOpenStorage = { openStorageSettings() },
                    onDelete = { startDelete() },
                )
            }
        }

        if (savedInstanceState == null) {
            if (hasMediaPermission(this)) vm.scan() else vm.phase = Phase.NEEDS_PERMISSION
        }
    }

    override fun onResume() {
        super.onResume()
        if (vm.phase == Phase.NEEDS_PERMISSION && hasMediaPermission(this)) vm.scan()
    }

    private fun startDelete() {
        if (vm.junkSelected().isEmpty()) return
        vm.beginDelete()
        nextBatch()
    }

    private fun nextBatch() {
        val batch = vm.pendingBatches.removeFirstOrNull()
        if (batch == null) {
            vm.onDeleteDone()
            return
        }
        try {
            // Android khud ek confirmation dialog dikhata hai, bina user ki haan ke kuch delete nahi hota
            val pi = MediaStore.createDeleteRequest(contentResolver, batch)
            deleteLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
        } catch (e: Exception) {
            vm.onDeleteCancelled()
        }
    }

    private fun openStorageSettings() {
        try {
            startActivity(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS))
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (_: Exception) {
            }
        }
    }

    private fun openAppSettings() {
        try {
            startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", packageName, null),
                )
            )
        } catch (_: Exception) {
        }
    }
}
