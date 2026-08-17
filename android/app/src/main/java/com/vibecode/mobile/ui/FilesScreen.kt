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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.vibecode.mobile.data.ApiException
import com.vibecode.mobile.data.FileContentResponse
import com.vibecode.mobile.data.FileNode
import com.vibecode.mobile.data.FileRevision
import com.vibecode.mobile.data.FileRevisionContent
import com.vibecode.mobile.data.FileWriteResponse
import com.vibecode.mobile.data.SearchResult
import com.vibecode.mobile.data.VibeRepository
import kotlinx.coroutines.launch
import kotlin.math.abs

private data class SearchOpenTarget(
    val query: String,
    val lineNumber: Int,
    val matchCase: Boolean,
)

@Composable
fun FilesScreen(repo: VibeRepository, enabled: Boolean) {
    val projects by repo.projects.collectAsState()
    val scope = rememberCoroutineScope()

    var projectId by remember(projects) { mutableStateOf(projects.firstOrNull()?.id.orEmpty()) }
    var path by remember { mutableStateOf("") }
    var nodes by remember { mutableStateOf<List<FileNode>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var matchCase by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var opened by remember { mutableStateOf<FileContentResponse?>(null) }
    var openTarget by remember { mutableStateOf<SearchOpenTarget?>(null) }
    var fileError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(projectId, path) {
        nodes = if (projectId.isBlank()) emptyList()
        else runCatching { repo.files(projectId, path) }.getOrDefault(emptyList())
    }

    fun openRemoteFile(filePath: String, target: SearchOpenTarget? = null) {
        scope.launch {
            runCatching { repo.readFile(projectId, filePath) }
                .onSuccess {
                    openTarget = target
                    opened = it
                    fileError = null
                }
                .onFailure { fileError = it.message ?: "Không thể mở file" }
        }
    }

    fun runProjectSearch() {
        val needle = query
        if (!enabled || projectId.isBlank() || needle.isBlank()) return
        scope.launch {
            searching = true
            fileError = null
            runCatching { repo.search(projectId, needle) }
                .onSuccess { results = it }
                .onFailure {
                    results = emptyList()
                    fileError = it.message ?: "Không thể tìm kiếm"
                }
            searching = false
        }
    }

    val openedFile = opened
    if (openedFile != null) {
        FileEditor(
            snapshot = openedFile,
            initialSearch = openTarget,
            enabled = enabled,
            onBack = {
                opened = null
                openTarget = null
            },
            onSave = { draft, expectedSha256 ->
                repo.writeFile(projectId, openedFile.path, draft, expectedSha256).sha256
            },
            onReload = { repo.readFile(projectId, openedFile.path) },
            onHistory = { repo.fileHistory(projectId, openedFile.path) },
            onReadRevision = { revisionId -> repo.readFileRevision(projectId, openedFile.path, revisionId) },
            onRestore = { revisionId, expectedSha256 ->
                repo.restoreFileRevision(projectId, openedFile.path, revisionId, expectedSha256)
            },
        )
        return
    }

    val visibleResults = remember(results, query, matchCase) {
        if (!matchCase || query.isBlank()) results
        else results.filter { literalMatchRanges(it.preview, query, matchCase = true).isNotEmpty() }
    }
    val totalMatches = remember(visibleResults, query, matchCase) {
        visibleResults.sumOf { literalMatchRanges(it.preview, query, matchCase).size }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Files & Search", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
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
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Tìm literal trong project") },
                singleLine = true,
            )
            FilterChip(
                selected = matchCase,
                onClick = { matchCase = !matchCase },
                label = { Text("Aa") },
            )
            IconButton(
                enabled = enabled && projectId.isNotBlank() && query.isNotBlank() && !searching,
                onClick = { runProjectSearch() },
            ) {
                Icon(Icons.Default.Search, contentDescription = "Search")
            }
        }

        Text(
            text = if (matchCase) "Literal · Aa: phân biệt hoa/thường" else "Literal · không phân biệt hoa/thường",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )

        if (fileError != null) {
            Text(
                text = fileError.orEmpty(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        if (results.isNotEmpty() || searching) {
            Text(
                text = if (searching) "Đang tìm..." else "$totalMatches match · ${visibleResults.size} dòng",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            if (!searching && visibleResults.isEmpty()) {
                Text(
                    text = "Không có kết quả đúng chế độ Aa hiện tại.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().weight(0.45f)) {
                    items(visibleResults) { result ->
                        ListItem(
                            headlineContent = { Text("${result.filePath}:${result.lineNumber}") },
                            supportingContent = {
                                HighlightedPreview(
                                    text = result.preview,
                                    query = query,
                                    matchCase = matchCase,
                                )
                            },
                            modifier = Modifier.clickable {
                                openRemoteFile(
                                    result.filePath,
                                    SearchOpenTarget(query, result.lineNumber, matchCase),
                                )
                            },
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { path = path.substringBeforeLast('/', "") }, enabled = path.isNotBlank()) {
                Icon(Icons.Default.ArrowUpward, contentDescription = "Up")
            }
            Text(if (path.isBlank()) "/" else "/$path")
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(nodes, key = { it.path }) { node ->
                ListItem(
                    leadingContent = {
                        Icon(if (node.isDir) Icons.Default.Folder else Icons.Default.Description, contentDescription = null)
                    },
                    headlineContent = { Text(node.name) },
                    supportingContent = { if (!node.isDir) Text("${node.size} bytes") },
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

@Composable
private fun HighlightedPreview(text: String, query: String, matchCase: Boolean) {
    val ranges = remember(text, query, matchCase) { literalMatchRanges(text, query, matchCase) }
    val background = MaterialTheme.colorScheme.secondaryContainer
    val foreground = MaterialTheme.colorScheme.onSecondaryContainer
    val annotated = remember(text, ranges, background, foreground) {
        buildAnnotatedString {
            var cursor = 0
            ranges.forEach { range ->
                if (range.first > cursor) append(text.substring(cursor, range.first))
                withStyle(SpanStyle(background = background, color = foreground)) {
                    append(text.substring(range.first, range.last + 1))
                }
                cursor = range.last + 1
            }
            if (cursor < text.length) append(text.substring(cursor))
        }
    }
    Text(annotated, maxLines = 2)
}

@Composable
private fun FileEditor(
    snapshot: FileContentResponse,
    initialSearch: SearchOpenTarget?,
    enabled: Boolean,
    onBack: () -> Unit,
    onSave: suspend (String, String) -> String,
    onReload: suspend () -> FileContentResponse,
    onHistory: suspend () -> List<FileRevision>,
    onReadRevision: suspend (String) -> FileRevisionContent,
    onRestore: suspend (String, String) -> FileWriteResponse,
) {
    val scope = rememberCoroutineScope()
    val editorFocus = remember { FocusRequester() }

    var original by remember(snapshot.path, snapshot.content) { mutableStateOf(snapshot.content) }
    var editorValue by remember(snapshot.path, snapshot.content) { mutableStateOf(TextFieldValue(snapshot.content)) }
    var revision by remember(snapshot.path, snapshot.sha256) { mutableStateOf(snapshot.sha256) }
    var busy by remember(snapshot.path) { mutableStateOf(false) }
    var error by remember(snapshot.path) { mutableStateOf<String?>(null) }
    var notice by remember(snapshot.path) { mutableStateOf<String?>(null) }
    var mode by remember(snapshot.path) { mutableStateOf("edit") }
    var history by remember(snapshot.path) { mutableStateOf<List<FileRevision>>(emptyList()) }
    var historyPreview by remember(snapshot.path) { mutableStateOf<FileRevisionContent?>(null) }
    var conflictLatest by remember(snapshot.path) { mutableStateOf<FileContentResponse?>(null) }
    var diffBase by remember(snapshot.path) { mutableStateOf(snapshot.content) }

    var findQuery by remember(snapshot.path, initialSearch?.query) { mutableStateOf(initialSearch?.query.orEmpty()) }
    var findMatchCase by remember(snapshot.path, initialSearch?.matchCase) { mutableStateOf(initialSearch?.matchCase ?: false) }
    var currentMatch by remember(snapshot.path) { mutableIntStateOf(0) }

    val draft = editorValue.text
    val dirty = draft != original
    val matches = remember(draft, findQuery, findMatchCase) {
        literalMatchRanges(draft, findQuery, findMatchCase)
    }

    fun selectMatch(index: Int) {
        if (matches.isEmpty()) return
        val normalized = ((index % matches.size) + matches.size) % matches.size
        val range = matches[normalized]
        currentMatch = normalized
        editorValue = editorValue.copy(selection = TextRange(range.first, range.last + 1))
    }

    LaunchedEffect(snapshot.path, initialSearch?.query, initialSearch?.lineNumber, initialSearch?.matchCase) {
        val target = initialSearch ?: return@LaunchedEffect
        val found = literalMatchRanges(editorValue.text, target.query, target.matchCase)
        if (found.isNotEmpty()) {
            val targetOffset = offsetForLine(editorValue.text, target.lineNumber)
            val nearest = found.indices.minByOrNull { abs(found[it].first - targetOffset) } ?: 0
            currentMatch = nearest
            val range = found[nearest]
            editorValue = editorValue.copy(selection = TextRange(range.first, range.last + 1))
            runCatching { editorFocus.requestFocus() }
        }
    }

    LaunchedEffect(findQuery, findMatchCase) {
        currentMatch = 0
        if (matches.isNotEmpty()) {
            val range = matches.first()
            editorValue = editorValue.copy(selection = TextRange(range.first, range.last + 1))
        }
    }

    LaunchedEffect(matches.size) {
        if (matches.isEmpty()) currentMatch = 0
        else if (currentMatch >= matches.size) currentMatch = matches.lastIndex
    }

    fun loadHistory() {
        scope.launch {
            busy = true
            error = null
            runCatching { onHistory() }
                .onSuccess {
                    history = it
                    mode = "history"
                }
                .onFailure { error = it.message ?: "Không thể tải history" }
            busy = false
        }
    }

    fun saveDraft() {
        scope.launch {
            busy = true
            error = null
            notice = null
            runCatching { onSave(editorValue.text, revision) }
                .onSuccess { newRevision ->
                    original = editorValue.text
                    revision = newRevision
                    diffBase = editorValue.text
                    conflictLatest = null
                    notice = "Đã lưu lên VPS"
                }
                .onFailure { failure ->
                    if (failure is ApiException && failure.statusCode == 409) {
                        runCatching { onReload() }
                            .onSuccess { latest ->
                                conflictLatest = latest
                                diffBase = latest.content
                                mode = "diff"
                                error = "File đã thay đổi trên VPS sau khi bạn mở. Không ghi đè. Hãy xem diff rồi merge."
                            }
                            .onFailure { error = it.message ?: "File đã thay đổi và không tải được bản mới nhất" }
                    } else {
                        error = failure.message ?: "Không thể lưu file"
                    }
                }
            busy = false
        }
    }

    val currentLine = if (matches.isNotEmpty()) lineForOffset(draft, matches[currentMatch.coerceIn(0, matches.lastIndex)].first) else 0

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            IconButton(onClick = onBack, enabled = !busy) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Text(snapshot.path, style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { mode = "edit" }, enabled = !busy) { Text("Edit") }
            TextButton(onClick = { diffBase = conflictLatest?.content ?: original; mode = "diff" }, enabled = !busy) { Text("Diff") }
            TextButton(onClick = { loadHistory() }, enabled = !busy) { Text("History") }
            Button(onClick = { saveDraft() }, enabled = enabled && dirty && !busy) {
                Text(if (busy) "Working..." else "Save")
            }
        }

        if (mode == "edit") {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                OutlinedTextField(
                    value = findQuery,
                    onValueChange = { findQuery = it },
                    modifier = Modifier.width(190.dp),
                    singleLine = true,
                    placeholder = { Text("Find in file") },
                )
                FilterChip(
                    selected = findMatchCase,
                    onClick = { findMatchCase = !findMatchCase },
                    label = { Text("Aa") },
                )
                Text(
                    text = if (matches.isEmpty()) "0/0" else "${currentMatch + 1}/${matches.size} · L$currentLine",
                    style = MaterialTheme.typography.labelMedium,
                )
                IconButton(enabled = matches.isNotEmpty(), onClick = { selectMatch(currentMatch - 1) }) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Previous match")
                }
                IconButton(enabled = matches.isNotEmpty(), onClick = { selectMatch(currentMatch + 1) }) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Next match")
                }
                IconButton(enabled = findQuery.isNotEmpty(), onClick = { findQuery = "" }) {
                    Icon(Icons.Default.Close, contentDescription = "Clear search")
                }
            }
        }

        val latest = conflictLatest
        if (latest != null) {
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Conflict: VPS có bản mới hơn", style = MaterialTheme.typography.titleSmall)
                    Text("Bản bạn đang gõ vẫn được giữ nguyên. App chưa ghi gì lên file.")
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        TextButton(onClick = { diffBase = latest.content; mode = "diff" }) { Text("Diff với VPS") }
                        TextButton(onClick = {
                            original = latest.content
                            editorValue = TextFieldValue(latest.content)
                            revision = latest.sha256
                            diffBase = latest.content
                            conflictLatest = null
                            error = null
                            notice = "Đã dùng bản VPS; thay đổi local đã bỏ"
                            mode = "edit"
                        }) { Text("Giữ bản VPS") }
                        TextButton(onClick = {
                            original = latest.content
                            revision = latest.sha256
                            diffBase = latest.content
                            conflictLatest = null
                            error = null
                            notice = "Đã dùng bản VPS làm base và giữ nguyên nội dung bạn đang gõ. Hãy merge rồi Save."
                            mode = "diff"
                        }) { Text("Merge thủ công") }
                    }
                }
            }
        }

        if (error != null) {
            Text(error.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
        } else if (notice != null) {
            Text(notice.orEmpty(), style = MaterialTheme.typography.labelMedium)
        } else {
            Text(if (dirty) "Unsaved changes" else "Saved", style = MaterialTheme.typography.labelMedium)
        }

        Spacer(Modifier.height(6.dp))

        when (mode) {
            "diff" -> {
                val diff = buildUnifiedDiff(diffBase, editorValue.text)
                Card(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    LazyColumn(modifier = Modifier.padding(10.dp)) {
                        item {
                            Text(
                                text = diff,
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }

            "history" -> {
                Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    if (history.isEmpty()) {
                        Text("Chưa có revision do VibeCode lưu.")
                    } else {
                        val preview = historyPreview
                        if (preview != null) {
                            Card(modifier = Modifier.fillMaxWidth().weight(0.55f)) {
                                LazyColumn(modifier = Modifier.padding(10.dp)) {
                                    item {
                                        Text(
                                            buildUnifiedDiff(preview.content, original),
                                            fontFamily = FontFamily.Monospace,
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                TextButton(onClick = { historyPreview = null }) { Text("Đóng diff") }
                                Button(
                                    enabled = enabled && !busy && preview.revision != null,
                                    onClick = {
                                        val revisionId = preview.revision?.id ?: return@Button
                                        scope.launch {
                                            busy = true
                                            error = null
                                            runCatching { onRestore(revisionId, revision) }
                                                .onSuccess { restored ->
                                                    original = restored.content
                                                    editorValue = TextFieldValue(restored.content)
                                                    revision = restored.sha256
                                                    diffBase = restored.content
                                                    historyPreview = null
                                                    conflictLatest = null
                                                    notice = "Đã restore revision; bản trước restore cũng đã được lưu vào history"
                                                    history = runCatching { onHistory() }.getOrDefault(history)
                                                    mode = "edit"
                                                }
                                                .onFailure { failure ->
                                                    if (failure is ApiException && failure.statusCode == 409) {
                                                        error = "Không restore vì file vừa thay đổi trên VPS. Hãy mở lại file trước."
                                                    } else {
                                                        error = failure.message ?: "Không thể restore revision"
                                                    }
                                                }
                                            busy = false
                                        }
                                    },
                                ) { Text("Restore / Undo") }
                            }
                        }

                        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                            items(history, key = { it.id }) { item ->
                                ListItem(
                                    headlineContent = { Text(item.createdAt.ifBlank { item.id }) },
                                    supportingContent = { Text("${item.size} bytes · ${item.sha256.take(12)}") },
                                    modifier = Modifier.clickable {
                                        scope.launch {
                                            busy = true
                                            error = null
                                            runCatching { onReadRevision(item.id) }
                                                .onSuccess { historyPreview = it }
                                                .onFailure { error = it.message ?: "Không thể đọc revision" }
                                            busy = false
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }

            else -> {
                OutlinedTextField(
                    value = editorValue,
                    onValueChange = {
                        editorValue = it
                        error = null
                        notice = null
                    },
                    enabled = enabled && !busy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .focusRequester(editorFocus),
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    label = { Text("Remote file") },
                )
            }
        }
    }
}

private fun literalMatchRanges(text: String, query: String, matchCase: Boolean): List<IntRange> {
    if (query.isEmpty() || text.isEmpty()) return emptyList()
    val out = ArrayList<IntRange>()
    var start = 0
    while (start <= text.length - query.length) {
        val index = text.indexOf(query, startIndex = start, ignoreCase = !matchCase)
        if (index < 0) break
        out += index until (index + query.length)
        start = index + query.length.coerceAtLeast(1)
    }
    return out
}

private fun offsetForLine(text: String, lineNumber: Int): Int {
    if (lineNumber <= 1) return 0
    var line = 1
    for (index in text.indices) {
        if (text[index] == '\n') {
            line++
            if (line == lineNumber) return index + 1
        }
    }
    return text.length
}

private fun lineForOffset(text: String, offset: Int): Int {
    if (offset <= 0) return 1
    var line = 1
    val end = offset.coerceAtMost(text.length)
    for (index in 0 until end) {
        if (text[index] == '\n') line++
    }
    return line
}

private fun buildUnifiedDiff(base: String, changed: String, maxLines: Int = 300): String {
    if (base == changed) return "Không có thay đổi."
    val aAll = base.lines()
    val bAll = changed.lines()
    val a = aAll.take(maxLines)
    val b = bAll.take(maxLines)
    val rows = a.size + 1
    val cols = b.size + 1
    val dp = Array(rows) { IntArray(cols) }
    for (i in a.indices.reversed()) {
        for (j in b.indices.reversed()) {
            dp[i][j] = if (a[i] == b[j]) dp[i + 1][j + 1] + 1 else maxOf(dp[i + 1][j], dp[i][j + 1])
        }
    }
    val out = StringBuilder("--- base\n+++ local/current\n")
    var i = 0
    var j = 0
    while (i < a.size || j < b.size) {
        when {
            i < a.size && j < b.size && a[i] == b[j] -> {
                out.append("  ").append(a[i]).append('\n')
                i++
                j++
            }
            j < b.size && (i == a.size || dp[i][j + 1] >= dp[i + 1][j]) -> {
                out.append("+ ").append(b[j]).append('\n')
                j++
            }
            i < a.size -> {
                out.append("- ").append(a[i]).append('\n')
                i++
            }
        }
    }
    if (aAll.size > maxLines || bAll.size > maxLines) {
        out.append("\n... diff chỉ hiển thị ").append(maxLines).append(" dòng đầu để tránh lag trên điện thoại ...")
    }
    return out.toString()
}
