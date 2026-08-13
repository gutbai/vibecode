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
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
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
import androidx.compose.ui.unit.dp
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
    var opened by remember { mutableStateOf<Pair<String, String>?>(null) }

    LaunchedEffect(projectId, path) {
        nodes = if (projectId.isBlank()) {
            emptyList()
        } else {
            runCatching { repo.files(projectId, path) }.getOrDefault(emptyList())
        }
    }

    val openedFile = opened
    if (openedFile != null) {
        FileViewer(
            path = openedFile.first,
            content = openedFile.second,
            onBack = { opened = null },
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
                                scope.launch {
                                    val content = runCatching {
                                        repo.readFile(projectId, result.filePath)
                                    }.getOrDefault("")
                                    opened = result.filePath to content
                                }
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
                                scope.launch {
                                    val content = runCatching {
                                        repo.readFile(projectId, node.path)
                                    }.getOrDefault("")
                                    opened = node.path to content
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun FileViewer(
    path: String,
    content: String,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Text(path, style = MaterialTheme.typography.titleMedium)
        }
        Card(modifier = Modifier.fillMaxSize()) {
            LazyColumn(modifier = Modifier.padding(12.dp)) {
                item {
                    Text(content, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
