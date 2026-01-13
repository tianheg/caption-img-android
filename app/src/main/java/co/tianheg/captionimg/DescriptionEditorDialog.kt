package co.tianheg.captionimg

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * XMP 描述编辑对话框
 */
@Composable
fun DescriptionEditorDialog(
    initialDescription: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var description by remember(initialDescription) {
        mutableStateOf(initialDescription.takeIf { it != "??" } ?: "")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑图片描述") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Text(
                    "输入图片描述（仅保存到 XMP，支持中文和所有 Unicode 字符）:",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("图片描述") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp),
                    maxLines = 10,
                    enabled = true,
                    placeholder = { Text("在此输入说明文字，支持中文...") },
                    supportingText = { Text("当前字数: ${description.length}") }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(description)
                },
                enabled = true
            ) {
                Icon(Icons.Filled.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = true) {
                Text("取消")
            }
        }
    )
}
