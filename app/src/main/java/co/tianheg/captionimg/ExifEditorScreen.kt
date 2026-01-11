package co.tianheg.captionimg

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.exifinterface.media.ExifInterface

@Composable
fun ExifEditorScreen(
    uri: Uri?,
    exifData: Map<String, String>,
    onDescriptionChange: (String) -> Unit,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var description by remember { mutableStateOf(exifData[ExifInterface.TAG_IMAGE_DESCRIPTION] ?: "") }
    var isSaving by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Image Description") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Text("Current EXIF Data:", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(8.dp))

                exifData.forEach { (tag, value) ->
                    Text(
                        "$tag: $value",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                TextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Image Description") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp),
                    maxLines = 5,
                    enabled = !isSaving
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    isSaving = true
                    onSave(description)
                },
                enabled = !isSaving
            ) {
                Icon(Icons.Filled.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text("Cancel")
            }
        }
    )
}
