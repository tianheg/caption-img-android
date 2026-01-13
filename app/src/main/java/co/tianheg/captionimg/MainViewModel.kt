package co.tianheg.captionimg

import android.app.Application
import android.content.IntentSender
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed class Screen {
    data object Images : Screen()
    data object Albums : Screen()
}

data class MainUiState(
    val hasReadImagesPermission: Boolean = false,
    val currentScreen: Screen = Screen.Images,
    val imageList: List<ImageItem> = emptyList(),
    val albumList: List<AlbumItem> = emptyList(),
    val selectedAlbumBucketIds: Set<String> = emptySet(),
    val showDescriptionEditor: Boolean = false,
    val selectedImageUri: Uri? = null,
    val currentImageItem: ImageItem? = null,
    val descriptionLoadingKeys: Set<String> = emptySet()
)

sealed class UiEvent {
    data class Toast(val message: String) : UiEvent()
    data class RequestWriteAccess(val uri: Uri, val description: String) : UiEvent()
    data class RequestRecoverableWrite(val intentSender: IntentSender, val uri: Uri, val description: String) : UiEvent()
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val context = app.applicationContext

    private val xmpHandler = XmpHandler(context)
    private val fileManager = FileManager(context)
    private val mediaStoreRepository = MediaStoreRepository(context)

    private val descriptionCache = HashMap<String, String>(256)

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    init {
        val selected = AlbumSelectionStore.loadSelectedBucketIds(context)
        _uiState.update { it.copy(selectedAlbumBucketIds = selected) }
    }

    fun onReadImagesPermissionResult(granted: Boolean) {
        _uiState.update { it.copy(hasReadImagesPermission = granted) }
        if (granted) {
            val selected = _uiState.value.selectedAlbumBucketIds
            if (selected.isNotEmpty() && _uiState.value.imageList.isEmpty()) {
                viewModelScope.launch { loadImagesFromSelectedAlbums(selected) }
            }
        }
    }

    fun onPickAlbumsClicked() {
        if (!_uiState.value.hasReadImagesPermission) {
            _events.tryEmit(UiEvent.Toast("需要读取相册权限才能列出相册/图片"))
            return
        }
        _uiState.update { it.copy(currentScreen = Screen.Albums) }
        viewModelScope.launch {
            val albums = loadAlbumsInternal()
            _uiState.update { it.copy(albumList = albums) }
        }
    }

    fun onBackFromAlbums() {
        _uiState.update { it.copy(currentScreen = Screen.Images) }
    }

    fun onAlbumsConfirmed(selected: Set<String>) {
        AlbumSelectionStore.saveSelectedBucketIds(context, selected)
        _uiState.update {
            it.copy(
                selectedAlbumBucketIds = selected,
                currentScreen = Screen.Images
            )
        }
        if (_uiState.value.hasReadImagesPermission) {
            viewModelScope.launch { loadImagesFromSelectedAlbums(selected) }
        }
    }

    private suspend fun loadAlbumsInternal(): List<AlbumItem> {
        return try {
            mediaStoreRepository.getAlbums()
        } catch (e: Exception) {
            Logger.log("读取相册失败", e)
            _events.tryEmit(UiEvent.Toast("读取相册失败"))
            emptyList()
        }
    }

    private suspend fun loadImagesFromSelectedAlbums(bucketIds: Set<String>) {
        if (!_uiState.value.hasReadImagesPermission) return
        if (bucketIds.isEmpty()) {
            _uiState.update { it.copy(imageList = emptyList()) }
            return
        }

        val combined = ArrayList<ImageItem>(512)
        for (bucketId in bucketIds) {
            try {
                combined.addAll(mediaStoreRepository.getImagesForBucket(bucketId))
            } catch (e: Exception) {
                Logger.log("读取相册图片失败: $bucketId", e)
            }
        }

        val merged = combined.map { item ->
            val cached = descriptionCache[item.uri.toString()]
            if (cached.isNullOrEmpty()) item else item.copy(description = cached)
        }

        _uiState.update { it.copy(imageList = merged) }
    }

    fun onAddImageClicked() {
        // Handled by Activity launcher
    }

    fun onSingleImagePicked(uri: Uri) {
        viewModelScope.launch {
            if (!fileManager.isImageFile(uri)) {
                _events.emit(UiEvent.Toast("Please select an image file"))
                return@launch
            }
            val fileName = fileManager.getFileName(uri)
            val description = xmpHandler.readDescription(uri) ?: ""
            val item = ImageItem(uri, fileName, description)

            _uiState.update {
                it.copy(
                    selectedImageUri = uri,
                    currentImageItem = item,
                    showDescriptionEditor = true
                )
            }
        }
    }

    fun ensureDescriptionLoaded(item: ImageItem) {
        val key = item.uri.toString()
        if (item.description.isNotEmpty()) return

        val cached = descriptionCache[key]
        if (!cached.isNullOrEmpty()) {
            _uiState.update { state ->
                state.copy(imageList = state.imageList.map { if (it.uri.toString() == key) it.copy(description = cached) else it })
            }
            return
        }

        val loading = _uiState.value.descriptionLoadingKeys
        if (loading.contains(key)) return

        _uiState.update { it.copy(descriptionLoadingKeys = it.descriptionLoadingKeys + key) }

        viewModelScope.launch {
            val desc = try {
                xmpHandler.readDescription(item.uri)
            } catch (e: Exception) {
                Logger.log("读取 XMP 描述失败: ${item.uri}", e)
                null
            }

            if (!desc.isNullOrEmpty()) {
                descriptionCache[key] = desc
                _uiState.update { state ->
                    state.copy(imageList = state.imageList.map { if (it.uri.toString() == key) it.copy(description = desc) else it })
                }
            }

            _uiState.update { it.copy(descriptionLoadingKeys = it.descriptionLoadingKeys - key) }
        }
    }

    fun onEditImage(imageItem: ImageItem) {
        viewModelScope.launch {
            val latest = xmpHandler.readDescription(imageItem.uri) ?: imageItem.description
            _uiState.update {
                it.copy(
                    selectedImageUri = imageItem.uri,
                    currentImageItem = imageItem.copy(description = latest),
                    showDescriptionEditor = true
                )
            }
        }
    }

    fun onDeleteImage(imageItem: ImageItem) {
        _uiState.update { it.copy(imageList = it.imageList.filter { item -> item.uri != imageItem.uri }) }
        _events.tryEmit(UiEvent.Toast("Image removed from list"))
    }

    fun onDismissEditor() {
        _uiState.update { it.copy(showDescriptionEditor = false, selectedImageUri = null) }
    }

    fun onSaveDescription(description: String) {
        val uri = _uiState.value.selectedImageUri ?: return

        viewModelScope.launch {
            try {
                val resultUri = xmpHandler.updateDescription(uri, description)
                if (resultUri != null) {
                    val finalUri = resultUri
                    descriptionCache[finalUri.toString()] = description

                    _uiState.update { state ->
                        val updatedItem = state.currentImageItem?.copy(description = description, uri = finalUri)
                        state.copy(
                            imageList = state.imageList.map { if (it.uri == uri) (updatedItem ?: it.copy(description = description)) else it },
                            selectedImageUri = finalUri,
                            currentImageItem = updatedItem,
                            showDescriptionEditor = false
                        )
                    }

                    _events.emit(UiEvent.Toast("Description updated successfully"))
                } else {
                    // MediaStore provider might require explicit write-request flow.
                    _events.emit(UiEvent.RequestWriteAccess(uri, description))
                }
            } catch (e: android.app.RecoverableSecurityException) {
                _events.emit(
                    UiEvent.RequestRecoverableWrite(
                        e.userAction.actionIntent.intentSender,
                        uri,
                        description
                    )
                )
            } catch (e: SecurityException) {
                _events.emit(UiEvent.RequestWriteAccess(uri, description))
            }
        }
    }

    fun onWriteAccessGranted(uri: Uri, description: String) {
        viewModelScope.launch {
            val updated = xmpHandler.updateDescription(uri, description)
            if (updated != null) {
                descriptionCache[uri.toString()] = description
                _uiState.update { state ->
                    state.copy(imageList = state.imageList.map { if (it.uri == uri) it.copy(description = description) else it })
                }
                _events.emit(UiEvent.Toast("Description updated successfully"))
            } else {
                _events.emit(UiEvent.Toast("Failed to update description"))
            }
            _uiState.update { it.copy(showDescriptionEditor = false) }
        }
    }
}
