package co.tianheg.captionimg

import android.Manifest
import android.app.Activity
import android.app.RecoverableSecurityException
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import co.tianheg.captionimg.ui.theme.CaptionImgTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var xmpHandler: XmpHandler
    private lateinit var fileManager: FileManager
    private lateinit var imagePickerManager: ImagePickerManager
    private lateinit var mediaStoreRepository: MediaStoreRepository

    private sealed class Screen {
        data object Images : Screen()
        data object Albums : Screen()
    }

    private var currentScreen by mutableStateOf<Screen>(Screen.Images)

    private var pendingWriteUri: Uri? by mutableStateOf(null)
    private var pendingWriteDescription: String? by mutableStateOf(null)

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

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Logger.init(this)
        }
    }

    private val requestReadImagesPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
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
                lifecycleScope.launch {
                    val updated = xmpHandler.updateDescription(uri, description)
                    if (updated != null) {
                        imageList = imageList.map { if (it.uri == uri) it.copy(description = description) else it }
                        Toast.makeText(this@MainActivity, "Description updated successfully", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@MainActivity, "Failed to update description", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        pendingWriteUri = null
        pendingWriteDescription = null
        showDescriptionEditor = false
    }

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data ?: return@registerForActivityResult
            val uri = data.data

            if (uri != null) {
                // Single image picked
                if (fileManager.isImageFile(uri)) {
                    // Take persistent permissions
                    try {
                        contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    
                    lifecycleScope.launch {
                        val fileName = fileManager.getFileName(uri)
                        val description = xmpHandler.readDescription(uri) ?: ""
                        selectedImageUri = uri
                        currentImageItem = ImageItem(uri, fileName, description)
                        showDescriptionEditor = true
                    }
                } else {
                    Toast.makeText(this, "Please select an image file", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private var selectedImageUri by mutableStateOf<Uri?>(null)
    private var currentImageItem by mutableStateOf<ImageItem?>(null)
    private var imageList by mutableStateOf<List<ImageItem>>(emptyList())
    private var showDescriptionEditor by mutableStateOf(false)

    private var albumList by mutableStateOf<List<AlbumItem>>(emptyList())
    private var selectedAlbumBucketIds by mutableStateOf<Set<String>>(emptySet())

    // Lazy XMP description cache + loading flags (keyed by uri string to keep it stable).
    private val descriptionCache = mutableStateMapOf<String, String>()
    private val descriptionLoading = mutableStateMapOf<String, Boolean>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 请求存储权限用于日志记录
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            Logger.init(this)
        }

        xmpHandler = XmpHandler(this)
        fileManager = FileManager(this)
        imagePickerManager = ImagePickerManager(this) {}
        mediaStoreRepository = MediaStoreRepository(this)

        selectedAlbumBucketIds = AlbumSelectionStore.loadSelectedBucketIds(this)

        setContent {
            CaptionImgTheme {
                MainScreen()
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
        return granted
    }

    private fun loadAlbums() {
        if (!ensureReadImagesPermission()) return
        lifecycleScope.launch {
            try {
                albumList = mediaStoreRepository.getAlbums()
            } catch (e: Exception) {
                Logger.log("读取相册失败", e)
                Toast.makeText(this@MainActivity, "读取相册失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadImagesFromSelectedAlbums() {
        if (!ensureReadImagesPermission()) return
        lifecycleScope.launch {
            val buckets = selectedAlbumBucketIds
            if (buckets.isEmpty()) {
                imageList = emptyList()
                return@launch
            }

            val combined = ArrayList<ImageItem>(512)
            for (bucketId in buckets) {
                try {
                    combined.addAll(mediaStoreRepository.getImagesForBucket(bucketId))
                } catch (e: Exception) {
                    Logger.log("读取相册图片失败: $bucketId", e)
                }
            }

            // Lazy: fill from cache if available; otherwise keep empty and load when visible.
            imageList = combined.map { item ->
                val cached = descriptionCache[item.uri.toString()]
                if (cached.isNullOrEmpty()) item else item.copy(description = cached)
            }
        }
    }

    private fun ensureDescriptionLoaded(item: ImageItem) {
        val key = item.uri.toString()
        if (item.description.isNotEmpty()) return
        val cached = descriptionCache[key]
        if (!cached.isNullOrEmpty()) {
            imageList = imageList.map { if (it.uri.toString() == key) it.copy(description = cached) else it }
            return
        }
        if (descriptionLoading[key] == true) return

        descriptionLoading[key] = true
        lifecycleScope.launch {
            val desc = try {
                xmpHandler.readDescription(item.uri)
            } catch (e: Exception) {
                Logger.log("读取 XMP 描述失败: ${item.uri}", e)
                null
            }

            if (!desc.isNullOrEmpty()) {
                descriptionCache[key] = desc
                imageList = imageList.map { if (it.uri.toString() == key) it.copy(description = desc) else it }
            }
            descriptionLoading.remove(key)
        }
    }

    @Composable
    fun MainScreen() {
        val scope = rememberCoroutineScope()

        LaunchedEffect(Unit) {
            if (selectedAlbumBucketIds.isNotEmpty()) {
                loadImagesFromSelectedAlbums()
            }
        }

        if (currentScreen is Screen.Albums) {
            AlbumPickerScreen(
                albums = albumList,
                initialSelectedBucketIds = selectedAlbumBucketIds,
                onBack = { currentScreen = Screen.Images },
                onConfirm = { selected ->
                    selectedAlbumBucketIds = selected
                    AlbumSelectionStore.saveSelectedBucketIds(this@MainActivity, selected)
                    currentScreen = Screen.Images
                    loadImagesFromSelectedAlbums()
                }
            )
            return
        }
        
        if (showDescriptionEditor && selectedImageUri != null) {
            DescriptionEditorDialog(
                initialDescription = currentImageItem?.description ?: "",
                onSave = { description ->
                    scope.launch {
                        val uri = selectedImageUri!!
                        try {
                            val resultUri = xmpHandler.updateDescription(uri, description)
                            if (resultUri != null) {
                                val finalUri = resultUri
                                val updatedItem = currentImageItem?.copy(description = description, uri = finalUri)

                                if (updatedItem != null) {
                                    imageList = imageList.map {
                                        if (it.uri == uri) updatedItem else it
                                    }
                                }

                                selectedImageUri = finalUri
                                currentImageItem = updatedItem
                                showDescriptionEditor = false
                                Toast.makeText(this@MainActivity, "Description updated successfully", Toast.LENGTH_SHORT).show()
                            } else {
                                // Some MediaStore providers may fail without throwing; request write access and retry.
                                if (isMediaStoreUri(uri)) {
                                    requestWriteAccess(uri, description)
                                } else {
                                    Toast.makeText(this@MainActivity, "Failed to update description", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } catch (e: RecoverableSecurityException) {
                            pendingWriteUri = uri
                            pendingWriteDescription = description
                            val request = IntentSenderRequest.Builder(e.userAction.actionIntent.intentSender).build()
                            requestWriteImagesLauncher.launch(request)
                        } catch (e: SecurityException) {
                            if (Build.VERSION.SDK_INT >= 30) {
                                requestWriteAccess(uri, description)
                            } else {
                                Logger.log("写入需要权限", e)
                                Toast.makeText(this@MainActivity, "写入需要系统授权", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                onDismiss = {
                    showDescriptionEditor = false
                    selectedImageUri = null
                }
            )
        }

        ImageListScreen(
            images = imageList,
            onAddImage = {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "image/*"
                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                }
                imagePickerLauncher.launch(intent)
            },
            onPickAlbums = {
                loadAlbums()
                currentScreen = Screen.Albums
            },
            onEnsureDescription = { item -> ensureDescriptionLoaded(item) },
            isDescriptionLoading = { uri -> descriptionLoading[uri.toString()] == true },
            onEditImage = { imageItem ->
                scope.launch {
                    selectedImageUri = imageItem.uri
                    // Refresh from XMP in case it changed externally.
                    val latest = xmpHandler.readDescription(imageItem.uri) ?: imageItem.description
                    currentImageItem = imageItem.copy(description = latest)
                    showDescriptionEditor = true
                }
            },
            onDeleteImage = { imageItem ->
                imageList = imageList.filter { it.uri != imageItem.uri }
                Toast.makeText(this@MainActivity, "Image removed from list", Toast.LENGTH_SHORT).show()
            }
        )
    }
}
