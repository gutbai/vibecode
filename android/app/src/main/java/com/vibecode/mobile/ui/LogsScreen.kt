package com.vibecode.mobile.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.vibecode.mobile.data.AgentLogEntry
import com.vibecode.mobile.data.AppLog
import com.vibecode.mobile.data.VibeRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(repo: VibeRepository, enabled: Boolean) {
    val scope = rememberCoroutineScope()
    val appLines by AppLog.lines.collectAsState()
    var agentLines by remember { mutableStateOf<List<AgentLogEntry>>(emptyList()) }
    var agentError by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf(0) }

    suspend fun refreshAgent() {
        if (!enabled) return
        runCatching { repo.logs(400) }
            .onSuccess {
                agentLines = it
                agentError = null
            }
            .onFailure {
                agentError = it.message ?: "Không đọc được log từ Agent"
            }
    }

    LaunchedEffect(enabled, selected) {
        while (enabled && selected == 1) {
            refreshAgent()
            delay(3000)
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Logs") },
            actions = {
                IconButton(onClick = { if (selected == 1) scope.launch { refreshAgent() } }, enabled = enabled && selected == 1) {
                    Icon(Icons.Default.Refresh, "Refresh")
                }
                IconButton(onClick = { AppLog.clear() }, enabled = selected == 0) {
                    Icon(Icons.Default.DeleteSweep, "Clear app log")
                }
            },
        )
        TabRow(selectedTabIndex = selected) {
            Tab(selected = selected == 0, onClick = { selected = 0 }, text = { Text("APP") })
            Tab(selected = selected == 1, onClick = { selected = 1 }, text = { Text("AGENT") })
        }
        if (selected == 0) {
            if (appLines.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(16.dp)) { Text("Chưa có log.") }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(appLines.asReversed()) { line ->
                        Text(line, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        } else {
            agentError?.let {
                Text(
                    "Agent log: $it",
                    modifier = Modifier.padding(10.dp),
                    color = MaterialTheme.colorScheme.error,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(agentLines.asReversed()) { item ->
                    Text(
                        "${item.at} ${item.level} [${item.source}] ${item.message}",
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
