package com.vibecode.mobile.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
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
                    text = "Claude Code · Codex · Grok",
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
            onCreate = { provider, project, prompt ->
                scope.launch {
                    runCatching { repo.create(provider, project, "", prompt) }
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
                    text = session.title.ifBlank { "New session" },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(8.dp))
                StatusBadge(session.status)
            }
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = providerDisplayName(session.provider),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    text = "•",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = session.projectName,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = session.machineName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    onCreate: (String, String, String) -> Unit,
) {
    var provider by remember { mutableStateOf("claude") }
    var project by remember { mutableStateOf(projects.firstOrNull()?.id.orEmpty()) }
    var prompt by remember { mutableStateOf("") }

    LaunchedEffect(projects) {
        if (project.isBlank() && projects.isNotEmpty()) {
            project = projects.first().id
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Tạo session")
                Text(
                    text = "Chọn AI CLI và workspace",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionLabel("AI CLI")
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ProviderChip("claude", "Claude", provider) { provider = it }
                        ProviderChip("codex", "Codex", provider) { provider = it }
                        ProviderChip("grok", "Grok", provider) { provider = it }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                SectionLabel("WORKSPACE")
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
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
                    }
                }

                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Prompt đầu tiên") },
                    placeholder = { Text("Có thể để trống và nhập sau") },
                    minLines = 3,
                    maxLines = 7,
                )
                Text(
                    text = "Tiêu đề sẽ tự tạo từ prompt đầu tiên, không cần đặt thủ công.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(provider, project, prompt) },
                enabled = project.isNotBlank(),
            ) {
                Text("Mở session")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy")
            }
        },
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ProviderChip(
    id: String,
    label: String,
    selectedProvider: String,
    onSelected: (String) -> Unit,
) {
    FilterChip(
        selected = selectedProvider == id,
        onClick = { onSelected(id) },
        label = {
            Text(
                text = label,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
            )
        },
    )
}

fun providerDisplayName(provider: String): String = when (provider.lowercase()) {
    "claude" -> "Claude"
    "codex" -> "Codex"
    "grok" -> "Grok"
    else -> provider.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}
