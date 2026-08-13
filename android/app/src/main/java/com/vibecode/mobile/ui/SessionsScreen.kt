package com.vibecode.mobile.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vibecode.mobile.data.Project
import com.vibecode.mobile.data.Session
import com.vibecode.mobile.data.VibeRepository
import kotlinx.coroutines.launch

@Composable
fun SessionsScreen(
    repo: VibeRepository,
    enabled: Boolean,
    onOpen: (String) -> Unit,
) {
    val sessions by repo.sessions.collectAsState()
    val projects by repo.projects.collectAsState()
    val scope = rememberCoroutineScope()

    var filter by remember { mutableStateOf("ALL") }
    var create by remember { mutableStateOf(false) }

    val shown = remember(sessions, filter) {
        if (filter == "ALL") sessions else sessions.filter { it.status == filter }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "VibeCode",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
                Text(
                    text = "Claude Code & Codex sessions",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(12.dp))
            FilledIconButton(
                onClick = { create = true },
                enabled = enabled,
            ) {
                Icon(Icons.Default.Add, contentDescription = "Tạo session")
            }
        }

        Spacer(Modifier.height(18.dp))

        // 1080px wide phones are commonly ~360dp at xxhdpi. A 2x2 grid is safer
        // than forcing four status chips into one horizontal row.
        StatusFilterGrid(
            selected = filter,
            all = sessions.size,
            running = sessions.count { it.status == "RUNNING" },
            waiting = sessions.count { it.status == "WAITING_INPUT" },
            done = sessions.count { it.status == "DONE" },
            onSelected = { filter = it },
        )

        Spacer(Modifier.height(14.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(shown, key = { it.id }) { session ->
                SessionCard(session) { onOpen(session.id) }
            }
        }
    }

    if (create) {
        CreateSessionDialog(
            projects = projects,
            onDismiss = { create = false },
            onCreate = { provider, project, title, prompt ->
                scope.launch {
                    runCatching { repo.create(provider, project, title, prompt) }
                    repo.refresh()
                    create = false
                }
            },
        )
    }
}

@Composable
private fun StatusFilterGrid(
    selected: String,
    all: Int,
    running: Int,
    waiting: Int,
    done: Int,
    onSelected: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatusFilterChip(
                modifier = Modifier.weight(1f),
                selected = selected == "ALL",
                label = "Tất cả",
                count = all,
                onClick = { onSelected("ALL") },
            )
            StatusFilterChip(
                modifier = Modifier.weight(1f),
                selected = selected == "RUNNING",
                label = "Đang chạy",
                count = running,
                onClick = { onSelected("RUNNING") },
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatusFilterChip(
                modifier = Modifier.weight(1f),
                selected = selected == "WAITING_INPUT",
                label = "Cần input",
                count = waiting,
                onClick = { onSelected("WAITING_INPUT") },
            )
            StatusFilterChip(
                modifier = Modifier.weight(1f),
                selected = selected == "DONE",
                label = "Done",
                count = done,
                onClick = { onSelected("DONE") },
            )
        }
    }
}

@Composable
private fun StatusFilterChip(
    modifier: Modifier = Modifier,
    selected: Boolean,
    label: String,
    count: Int,
    onClick: () -> Unit,
) {
    FilterChip(
        modifier = modifier,
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = "$label $count",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}

@Composable
private fun SessionCard(
    session: Session,
    onClick: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = session.title.ifBlank { "Untitled session" },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(8.dp))
                StatusBadge(session.status)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "${session.provider} · ${session.projectName}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = session.machineName,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun StatusBadge(status: String) {
    val color = when (status) {
        "WAITING_INPUT" -> MaterialTheme.colorScheme.tertiary
        "ERROR" -> MaterialTheme.colorScheme.error
        "DONE" -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.primary
    }
    AssistChip(
        onClick = {},
        label = {
            Text(
                text = status.replace('_', ' '),
                color = color,
                maxLines = 1,
            )
        },
    )
}

@Composable
private fun CreateSessionDialog(
    projects: List<Project>,
    onDismiss: () -> Unit,
    onCreate: (String, String, String, String) -> Unit,
) {
    var provider by remember { mutableStateOf("claude") }
    var project by remember { mutableStateOf(projects.firstOrNull()?.id.orEmpty()) }
    var title by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tạo session") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = provider == "claude",
                        onClick = { provider = "claude" },
                        label = { Text("Claude") },
                    )
                    FilterChip(
                        selected = provider == "codex",
                        onClick = { provider = "codex" },
                        label = { Text("Codex") },
                    )
                }
                projects.forEach { item ->
                    FilterChip(
                        selected = project == item.id,
                        onClick = { project = item.id },
                        label = {
                            Text(
                                text = item.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    )
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Tiêu đề") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Prompt ban đầu") },
                    minLines = 3,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(provider, project, title, prompt) },
                enabled = project.isNotBlank(),
            ) {
                Text("Start")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy")
            }
        },
    )
}
