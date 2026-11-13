package com.gammagamma.lax.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ManualCredentialsDialog(
    reason: String,
    onSubmit: (url: String, token: String, cookie: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var url by remember { mutableStateOf("https://") }
    var token by remember { mutableStateOf("") }
    var cookie by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sign in") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(reason)
                Text(
                    "Paste a workspace URL, xoxc- token, and d cookie (xoxd-). " +
                        "Tokens stay in memory / your keyring and are never logged.",
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Workspace URL") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("Token (xoxc-…)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = cookie,
                    onValueChange = { cookie = it },
                    label = { Text("Cookie d (xoxd-…)") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(url.trim(), token.trim(), cookie.trim()) },
                enabled = token.startsWith("xoxc-") && cookie.startsWith("xoxd-") && url.contains("slack.com"),
            ) {
                Text("Connect")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
