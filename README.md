# CaptionImg (Android) — XMP Description Editor

## Overview
CaptionImg is an Android application that reads and updates an image's description using **JPEG XMP (APP1)**.

The app is intentionally **XMP-only** for description storage.

## Features

### Core Functionality
- **Read Description**: Reads the description from JPEG XMP
- **Edit Description**: Writes the description back to JPEG XMP (UTF-8)
- **Unicode Friendly**: Works well for Chinese and all Unicode text
- **Batch Operations**: Add multiple images and manage them in a list
- **Real-time Preview**: View image thumbnails and current metadata

### UI Components
- **Image List Screen**: Browse and manage selected images
- **Description Editor Dialog**: View and edit the XMP description
- **Image Cards**: Display image thumbnails with current descriptions

## Project Structure

```
app/
├── src/main/
│   ├── java/co/tianheg/captionimg/
│   │   ├── MainActivity.kt             # Main activity and Compose UI state
│   │   ├── XmpHandler.kt               # XMP description read/write operations
│   │   ├── JpegXmp.kt                  # Minimal JPEG APP1 XMP reader/writer
│   │   ├── DescriptionEditorDialog.kt  # Editor dialog UI
│   │   ├── FileManager.kt              # File and URI management
│   │   ├── ImagePickerManager.kt       # Image selection logic
│   │   ├── ImageListScreen.kt          # List UI and data class
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
3. **Edit Description**: Tap the edit icon on any image card
4. **Update Description**: Modify the image description and tap "Save"
5. **Remove Images**: Tap the delete icon to remove images from the list

## Key Classes

### XmpHandler
Handles reading/writing the description via JPEG XMP:
- `readDescription()` - Read description from XMP
- `updateDescription()` - Write description to XMP

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

### DescriptionEditorDialog
Dialog for editing the XMP description.

## Technical Notes

### Supported Formats
The current implementation targets **JPEG/JPG** because XMP is written into JPEG APP1 segments.

### Runtime Permissions
The app uses the scoped storage API (Android 10+) with content URIs. For Android 13+, it requests `READ_MEDIA_IMAGES` instead of `READ_EXTERNAL_STORAGE`.

## Future Enhancements

- Batch editing multiple images
- Export/import XMP description
- Image information statistics
- Search and filter capabilities
- Metadata templates
- Undo/redo functionality
- Cloud backup support

## Troubleshooting

### Can't modify description
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
