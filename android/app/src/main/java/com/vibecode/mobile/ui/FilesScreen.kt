package com.vibecode.mobile.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.vibecode.mobile.data.FileContentResponse
import com.vibecode.mobile.data.FileNode
import com.vibecode.mobile.data.SearchResult
import com.vibecode.mobile.data.VibeRepository
import kotlinx.coroutines.launch

@Composable
fun FilesScreen(repo: VibeRepository, enabled: Boolean) {
    val projects by repo.projects.collectAsState()
    val scope = rememberCoroutineScope()

    var projectId by remember(projects) {
        mutableStateOf(projects.firstOrNull()?.id.orEmpty())
    }
    var path by remember { mutableStateOf("") }
    var nodes by remember { mutableStateOf<List<FileNode>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var opened by remember { mutableStateOf<FileContentResponse?>(null) }
    var fileError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(projectId, path) {
        nodes = if (projectId.isBlank()) {
            emptyList()
        } else {
            runCatching { repo.files(projectId, path) }.getOrDefault(emptyList())
        }
    }

    fun openRemoteFile(filePath: String) {
        scope.launch {
            runCatching { repo.readFile(projectId, filePath) }
                .onSuccess {
                    opened = it
                    fileError = null
                }
                .onFailure {
                    fileError = it.message ?: "Không thể mở file"
                }
        }
    }

    val openedFile = opened
    if (openedFile != null) {
        FileEditor(
            snapshot = openedFile,
            enabled = enabled,
            onBack = { opened = null },
            onSave = { draft, expectedSha256 ->
                repo.writeFile(projectId, openedFile.path, draft, expectedSha256).sha256
            },
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
        ) {
            Text("Files & Search", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                projects.forEach { project ->
                    FilterChip(
                        selected = projectId == project.id,
                        onClick = {
                            projectId = project.id
                            path = ""
                            results = emptyList()
                            fileError = null
                        },
                        label = { Text(project.name) },
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Tìm nội dung trong project") },
                    singleLine = true,
                )
                IconButton(
                    enabled = enabled && projectId.isNotBlank() && query.isNotBlank(),
                    onClick = {
                        scope.launch {
                            results = runCatching { repo.search(projectId, query) }
                                .getOrDefault(emptyList())
                        }
                    },
                ) {
                    Icon(Icons.Default.Search, contentDescription = "Search")
                }
            }

            if (fileError != null) {
                Text(
                    text = fileError.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            if (results.isNotEmpty()) {
                Text(
                    text = "${results.size} kết quả",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.45f),
                ) {
                    items(results) { result ->
                        ListItem(
                            headlineContent = {
                                Text("${result.filePath}:${result.lineNumber}")
                            },
                            supportingContent = {
                                Text(result.preview, maxLines = 2)
                            },
                            modifier = Modifier.clickable {
                                openRemoteFile(result.filePath)
                            },
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = { path = path.substringBeforeLast('/', "") },
                    enabled = path.isNotBlank(),
                ) {
                    Icon(Icons.Default.ArrowUpward, contentDescription = "Up")
                }
                Text(if (path.isBlank()) "/" else "/$path")
            }

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(nodes, key = { it.path }) { node ->
                    ListItem(
                        leadingContent = {
                            Icon(
                                imageVector = if (node.isDir) Icons.Default.Folder else Icons.Default.Description,
                                contentDescription = null,
                            )
                        },
                        headlineContent = { Text(node.name) },
                        supportingContent = {
                            if (!node.isDir) {
                                Text("${node.size} bytes")
                            }
                        },
                        modifier = Modifier.clickable {
                            if (node.isDir) {
                                path = node.path
                            } else {
                                openRemoteFile(node.path)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun FileEditor(
    snapshot: FileContentResponse,
    enabled: Boolean,
    onBack: () -> Unit,
    onSave: suspend (String, String) -> String,
) {
    val scope = rememberCoroutineScope()
    var original by remember(snapshot.path, snapshot.content) { mutableStateOf(snapshot.content) }
    var draft by remember(snapshot.path, snapshot.content) { mutableStateOf(snapshot.content) }
    var revision by remember(snapshot.path, snapshot.sha256) { mutableStateOf(snapshot.sha256) }
    var saving by remember(snapshot.path) { mutableStateOf(false) }
    var error by remember(snapshot.path) { mutableStateOf<String?>(null) }
    val dirty = draft != original

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(onClick = onBack, enabled = !saving) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = snapshot.path,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Button(
                enabled = enabled && dirty && !saving,
                onClick = {
                    scope.launch {
                        saving = true
                        error = null
                        runCatching { onSave(draft, revision) }
                            .onSuccess { newRevision ->
                                original = draft
                                revision = newRevision
                            }
                            .onFailure {
                                error = it.message ?: "Không thể lưu file"
                            }
                        saving = false
                    }
                },
            ) {
                Text(if (saving) "Saving..." else "Save")
            }
        }

        Text(
            text = when {
                error != null -> error.orEmpty()
                saving -> "Đang lưu lên VPS..."
                dirty -> "Unsaved changes"
                else -> "Saved"
            },
            style = MaterialTheme.typography.labelMedium,
            color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 6.dp),
        )

        OutlinedTextField(
            value = draft,
            onValueChange = {
                draft = it
                error = null
            },
            enabled = enabled && !saving,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            label = { Text("Remote file") },
        )
    }
}
