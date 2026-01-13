package co.tianheg.captionimg

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.IntentSenderRequest
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.tianheg.captionimg.ui.theme.CaptionImgTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private var pendingWriteUri: Uri? = null
    private var pendingWriteDescription: String? = null

    private fun isMediaStoreUri(uri: Uri): Boolean {
        return uri.scheme == "content" && uri.authority == "media"
    }

    private fun requestWriteAccess(uri: Uri, description: String) {
        if (Build.VERSION.SDK_INT >= 30) {
            pendingWriteUri = uri
            pendingWriteDescription = description
            val pi = MediaStore.createWriteRequest(contentResolver, listOf(uri))
            val request = IntentSenderRequest.Builder(pi.intentSender).build()
            requestWriteImagesLauncher.launch(request)
        } else {
            Toast.makeText(this, "Android 10 及以下无法自动请求写入授权，请改用手动添加的单张图片方式", Toast.LENGTH_SHORT).show()
        }
    }

    private val requestReadImagesPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        viewModel.onReadImagesPermissionResult(isGranted)
        if (!isGranted) {
            Toast.makeText(this, "需要读取相册权限才能列出相册/图片", Toast.LENGTH_SHORT).show()
        }
    }

    private val requestWriteImagesLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = pendingWriteUri
            val description = pendingWriteDescription
            if (uri != null && description != null) {
                viewModel.onWriteAccessGranted(uri, description)
            }
        }
        pendingWriteUri = null
        pendingWriteDescription = null
    }

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data ?: return@registerForActivityResult
            val uri = data.data

            if (uri != null) {
                // Single image picked
                // Take persistent permissions (best-effort).
                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                } catch (_: Exception) {
                    // Some providers don't support persistable permissions.
                }
                viewModel.onSingleImagePicked(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Logger.init(this)

        setContent {
            CaptionImgTheme {
                MainScreen()
            }
        }

        // Sync current permission state for auto-loading previously selected albums.
        val permission = requiredReadImagesPermission()
        val granted = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        viewModel.onReadImagesPermissionResult(granted)

        lifecycleScope.launch {
            viewModel.events.collect { event ->
                when (event) {
                    is UiEvent.Toast -> Toast.makeText(this@MainActivity, event.message, Toast.LENGTH_SHORT).show()

                    is UiEvent.RequestRecoverableWrite -> {
                        pendingWriteUri = event.uri
                        pendingWriteDescription = event.description
                        val request = IntentSenderRequest.Builder(event.intentSender).build()
                        requestWriteImagesLauncher.launch(request)
                    }

                    is UiEvent.RequestWriteAccess -> {
                        if (Build.VERSION.SDK_INT >= 30 && isMediaStoreUri(event.uri)) {
                            pendingWriteUri = event.uri
                            pendingWriteDescription = event.description
                            val pi = MediaStore.createWriteRequest(contentResolver, listOf(event.uri))
                            val request = IntentSenderRequest.Builder(pi.intentSender).build()
                            requestWriteImagesLauncher.launch(request)
                        } else {
                            Toast.makeText(this@MainActivity, "Failed to update description", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    private fun requiredReadImagesPermission(): String {
        return if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }

    private fun ensureReadImagesPermission(): Boolean {
        val permission = requiredReadImagesPermission()
        val granted = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            requestReadImagesPermissionLauncher.launch(permission)
        }
        viewModel.onReadImagesPermissionResult(granted)
        return granted
    }

    @Composable
    fun MainScreen() {
        val state by viewModel.uiState.collectAsStateWithLifecycle()

        if (state.currentScreen is Screen.Albums) {
            AlbumPickerScreen(
                albums = state.albumList,
                initialSelectedBucketIds = state.selectedAlbumBucketIds,
                onBack = { viewModel.onBackFromAlbums() },
                onConfirm = { selected ->
                    viewModel.onAlbumsConfirmed(selected)
                }
            )
            return
        }

        if (state.showDescriptionEditor && state.selectedImageUri != null) {
            DescriptionEditorDialog(
                initialDescription = state.currentImageItem?.description ?: "",
                onSave = { description -> viewModel.onSaveDescription(description) },
                onDismiss = { viewModel.onDismissEditor() }
            )
        }

        ImageListScreen(
            images = state.imageList,
            onAddImage = {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "image/*"
                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                }
                imagePickerLauncher.launch(intent)
            },
            onPickAlbums = {
                if (!ensureReadImagesPermission()) return@ImageListScreen
                viewModel.onPickAlbumsClicked()
            },
            onEnsureDescription = { item -> viewModel.ensureDescriptionLoaded(item) },
            isDescriptionLoading = { uri -> state.descriptionLoadingKeys.contains(uri.toString()) },
            onEditImage = { imageItem -> viewModel.onEditImage(imageItem) },
            onDeleteImage = { imageItem -> viewModel.onDeleteImage(imageItem) }
        )
    }
}
