package co.tianheg.captionimg

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.exifinterface.media.ExifInterface
import co.tianheg.captionimg.ui.theme.CaptionImgTheme

class MainActivity : ComponentActivity() {

    private lateinit var exifHandler: ExifHandler
    private lateinit var fileManager: FileManager
    private lateinit var imagePickerManager: ImagePickerManager

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
                    
                    val fileName = fileManager.getFileName(uri)
                    val description = exifHandler.readImageDescription(uri) ?: ""
                    selectedImageUri = uri
                    currentImageItem = ImageItem(uri, fileName, description)
                    exifDataMap = exifHandler.readAllExifData(uri)
                    showExifEditor = true
                } else {
                    Toast.makeText(this, "Please select an image file", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private var selectedImageUri by mutableStateOf<Uri?>(null)
    private var currentImageItem by mutableStateOf<ImageItem?>(null)
    private var exifDataMap by mutableStateOf<Map<String, String>>(emptyMap())
    private var imageList by mutableStateOf<List<ImageItem>>(emptyList())
    private var showExifEditor by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        exifHandler = ExifHandler(this)
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
        if (showExifEditor && selectedImageUri != null) {
            ExifEditorScreen(
                uri = selectedImageUri,
                exifData = exifDataMap,
                onDescriptionChange = { /* Handle description change */ },
                onSave = { description ->
                    val success = exifHandler.updateImageDescription(selectedImageUri!!, description)
                    if (success) {
                        val updatedItem = currentImageItem?.copy(description = description)
                        if (updatedItem != null) {
                            imageList = imageList.map {
                                if (it.uri == selectedImageUri) updatedItem else it
                            }
                        }
                        showExifEditor = false
                        Toast.makeText(
                            this@MainActivity,
                            "Description updated successfully",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Toast.makeText(
                            this@MainActivity,
                            "Failed to update description",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                onDismiss = {
                    showExifEditor = false
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
                selectedImageUri = imageItem.uri
                currentImageItem = imageItem
                exifDataMap = exifHandler.readAllExifData(imageItem.uri)
                showExifEditor = true
            },
            onDeleteImage = { imageItem ->
                imageList = imageList.filter { it.uri != imageItem.uri }
                Toast.makeText(this, "Image removed from list", Toast.LENGTH_SHORT).show()
            }
        )
    }
}
