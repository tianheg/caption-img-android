# README for Caption Image - Android EXIF Editor App

## Overview
Caption Image is an Android application that allows users to read and update EXIF metadata (specifically image descriptions) in various image formats including:
- JPG/JPEG
- PNG
- WebP
- GIF
- BMP
- HEIC/HEIF

## Features

### Core Functionality
- **Read EXIF Data**: View all EXIF metadata from selected images
- **Edit Description**: Update the image description (ImageDescription tag) in EXIF data
- **Multiple Formats**: Support for JPEG, PNG, WebP, GIF, BMP, and HEIC formats
- **Batch Operations**: Add multiple images and manage them in a list
- **Real-time Preview**: View image thumbnails and current metadata

### UI Components
- **Image List Screen**: Browse and manage selected images
- **EXIF Editor Dialog**: View and edit image metadata
- **Image Cards**: Display image thumbnails with current descriptions

## Project Structure

```
app/
├── src/main/
│   ├── java/com/example/captionimg/
│   │   ├── MainActivity.kt           # Main activity and Compose UI state
│   │   ├── ExifHandler.kt            # EXIF read/write operations
│   │   ├── FileManager.kt            # File and URI management
│   │   ├── ImagePickerManager.kt     # Image selection logic
│   │   ├── ImageListScreen.kt        # List UI and data class
│   │   ├── ExifEditorScreen.kt       # Editor dialog UI
│   │   └── ui/theme/
│   │       └── Theme.kt              # Material 3 theming
│   ├── res/
│   │   ├── values/
│   │   │   ├── strings.xml
│   │   │   └── themes.xml
│   │   └── xml/
│   │       ├── backup_rules.xml
│   │       └── data_extraction_rules.xml
│   └── AndroidManifest.xml
├── build.gradle.kts
└── proguard-rules.pro

build.gradle.kts                    # Top-level build config
settings.gradle.kts                 # Project settings
gradle.properties                   # Gradle properties
```

## Dependencies

### Android Core
- `androidx.core:core-ktx` - Kotlin extensions
- `androidx.lifecycle:lifecycle-runtime-ktx` - Lifecycle management
- `androidx.activity:activity-ktx` - Activity extensions

### Jetpack Compose
- Compose UI, Material3, Icons
- Material Design 3 components

### Libraries
- `androidx.exifinterface:exifinterface` - EXIF data handling
- `io.coil-kt:coil-compose` - Image loading and display
- `kotlinx-coroutines-android` - Async operations

## Permissions Required

Add these to AndroidManifest.xml (already included):
```xml
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
```

## How to Use

### Build the App
```bash
./gradlew build
```

### Run the App
```bash
./gradlew installDebug
```

Or use Android Studio to run it on an emulator or physical device.

### Using the App

1. **Add Images**: Tap the '+' button in the top right to select images
2. **View Details**: See image thumbnails and current descriptions in the list
3. **Edit Metadata**: Tap the edit icon on any image card to open the EXIF editor
4. **Update Description**: Modify the image description and tap "Save"
5. **View All EXIF Data**: The editor displays all available EXIF tags
6. **Remove Images**: Tap the delete icon to remove images from the list

## Key Classes

### ExifHandler
Handles all EXIF read/write operations:
- `readImageDescription()` - Get image description
- `readAllExifData()` - Get all EXIF metadata
- `updateImageDescription()` - Update description tag
- `updateMultipleExifTags()` - Update multiple tags

### FileManager
Manages file operations:
- `getFileName()` - Extract filename from URI
- `isImageFile()` - Validate image files
- `getSupportedImageFormats()` - List supported MIME types

### ImageListScreen
Compose UI components:
- `ImageListScreen()` - Main list view
- `ImageItemCard()` - Individual image display
- `ImageItem` - Data class for image info

### ExifEditorScreen
Dialog for editing metadata:
- Displays current EXIF data
- Text field for description editing
- Save/Cancel buttons

## Technical Notes

### Supported Image Formats
The app supports reading and writing EXIF data for:
- JPEG/JPG - Full EXIF support
- PNG - Limited EXIF support (via Exif orientation)
- WebP - Basic EXIF support
- GIF - Limited support
- HEIC/HEIF - iOS format, limited support
- BMP - Basic support

### EXIF Tag Reference
Common EXIF tags available:
- `TAG_IMAGE_DESCRIPTION` - Image description
- `TAG_MAKE` - Camera manufacturer
- `TAG_MODEL` - Camera model
- `TAG_DATETIME` - Date/time taken
- `TAG_ARTIST` - Artist/photographer
- `TAG_GPS_LATITUDE` - GPS latitude
- `TAG_GPS_LONGITUDE` - GPS longitude

### Runtime Permissions
The app uses the scoped storage API (Android 10+) with content URIs. For Android 13+, it requests `READ_MEDIA_IMAGES` instead of `READ_EXTERNAL_STORAGE`.

## Future Enhancements

- Batch editing multiple images
- Export/import EXIF data
- Advanced EXIF tag editor
- Image information statistics
- Search and filter capabilities
- Metadata templates
- Undo/redo functionality
- Cloud backup support

## Troubleshooting

### Can't modify EXIF data
- Ensure the app has write permissions
- Some image files may be read-only
- Try copying the image to a writable location

### Image won't load
- Check if the image format is supported
- Verify file permissions
- Try a different image file

### Performance issues
- Don't load too many large images
- Clear cache in app settings
- Use images optimized for mobile

## License
This project is provided as-is for educational and development purposes.
