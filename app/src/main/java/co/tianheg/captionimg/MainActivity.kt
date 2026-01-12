package co.tianheg.captionimg

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import co.tianheg.captionimg.ui.theme.CaptionImgTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var xmpHandler: XmpHandler
    private lateinit var fileManager: FileManager
    private lateinit var imagePickerManager: ImagePickerManager

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Logger.init(this)
        }
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

        setContent {
            CaptionImgTheme {
                MainScreen()
            }
        }
    }

    @Composable
    fun MainScreen() {
        val scope = rememberCoroutineScope()
        
        if (showDescriptionEditor && selectedImageUri != null) {
            DescriptionEditorDialog(
                initialDescription = currentImageItem?.description ?: "",
                onSave = { description ->
                    scope.launch {
                        val resultUri = xmpHandler.updateDescription(selectedImageUri!!, description)
                        if (resultUri != null) {
                            val finalUri = resultUri
                            val updatedItem = currentImageItem?.copy(description = description, uri = finalUri)

                            if (updatedItem != null) {
                                imageList = imageList.map {
                                    if (it.uri == selectedImageUri) updatedItem else it
                                }
                            }

                            selectedImageUri = finalUri
                            currentImageItem = updatedItem
                            showDescriptionEditor = false
                            Toast.makeText(this@MainActivity, "Description updated successfully", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@MainActivity, "Failed to update description", Toast.LENGTH_SHORT).show()
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
