package com.vibecode.mobile.ui

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vibecode.mobile.data.*
import kotlinx.coroutines.launch

private data class SlashSuggestion(
    val command: String,
    val description: String,
    val kind: String = "COMMAND",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(repo: VibeRepository, id: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var session by remember { mutableStateOf<Session?>(null) }
    var text by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<List<Attachment>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var actionError by remember { mutableStateOf<String?>(null) }
    var controlsExpanded by remember { mutableStateOf(false) }

    suspend fun refresh() {
        session = runCatching { repo.session(id) }.getOrNull()
    }

    LaunchedEffect(id) {
        while (true) {
            refresh()
            kotlinx.coroutines.delay(1500)
        }
    }

    fun pasteClipboardAttachment() {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = cm.primaryClip ?: return
        val uri = clip.getItemAt(0).uri ?: return
        scope.launch {
            busy = true
            actionError = null
            runCatching { repo.upload(id, ctx.contentResolver, uri) }
                .onSuccess { pending = pending + it }
                .onFailure { actionError = it.message }
            busy = false
        }
    }

    fun sendQuickInput(value: String) {
        if (busy) return
        scope.launch {
            busy = true
            actionError = null
            runCatching { repo.send(id, value, emptyList()) }
                .onFailure { actionError = it.message }
            refresh()
            busy = false
        }
    }

    fun sendControl(vararg keys: String) {
        if (busy) return
        scope.launch {
            busy = true
            actionError = null
            runCatching { repo.sendControl(id, *keys) }
                .onFailure { actionError = it.message }
            refresh()
            busy = false
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        scope.launch {
            busy = true
            actionError = null
            for (uri in uris) {
                runCatching { repo.upload(id, ctx.contentResolver, uri) }
                    .onSuccess { pending = pending + it }
                    .onFailure { actionError = it.message }
            }
            busy = false
        }
    }

    val provider = session?.provider.orEmpty()
    val slashSuggestions = remember(text, provider) {
        slashSuggestionsFor(provider, text)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(session?.title?.ifBlank { "New session" } ?: "Session")
                        session?.let {
                            Text(
                                text = "${providerDisplayName(it.provider)} · ${it.projectName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onBack) { Icon(Icons.Default.ArrowBack, null) }
                },
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            runCatching { repo.stop(id) }
                                .onFailure { actionError = it.message }
                            refresh()
                        }
                    }) {
                        Icon(Icons.Default.Stop, null)
                    }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize(),
        ) {
            session?.let { s ->
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                providerDisplayName(s.provider).uppercase(),
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                            )
                        },
                    )
                    StatusBadge(s.status)
                }
            }

            LazyColumn(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 6.dp),
            ) {
                session?.messages?.let { msgs ->
                    items(msgs, key = { it.id }) { m ->
                        MessageBubble(m, session?.provider.orEmpty())
                    }
                }
                session?.lastOutput?.takeIf { it.isNotBlank() }?.let { out ->
                    item {
                        TerminalOutputCard(
                            provider = session?.provider.orEmpty(),
                            output = out,
                        )
                    }
                }
            }

            actionError?.let {
                Text(
                    text = it,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            OutlinedButton(
                onClick = { controlsExpanded = !controlsExpanded },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(if (controlsExpanded) "⌨ Ẩn phím phụ  ▲" else "⌨ Phím phụ  ▼")
            }

            if (controlsExpanded) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ControlButton("Esc", !busy) { sendControl("ESC") }
                    ControlButton("←", !busy) { sendControl("LEFT") }
                    ControlButton("↑", !busy) { sendControl("UP") }
                    ControlButton("↓", !busy) { sendControl("DOWN") }
                    ControlButton("→", !busy) { sendControl("RIGHT") }
                    ControlButton("Enter", !busy) { sendControl("ENTER") }
                    ControlButton("Tab", !busy) { sendControl("TAB") }
                    ControlButton("Space", !busy) { sendControl("SPACE") }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    TextButton(onClick = { sendQuickInput("y") }, enabled = !busy) { Text("y") }
                    TextButton(onClick = { sendQuickInput("n") }, enabled = !busy) { Text("n") }
                    listOf("1", "2", "3", "4", "5").forEach { option ->
                        TextButton(onClick = { sendQuickInput(option) }, enabled = !busy) { Text(option) }
                    }
                }
            }

            if (pending.isNotEmpty()) {
                Row(
                    Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    pending.take(8).forEach {
                        InputChip(
                            selected = true,
                            onClick = {},
                            label = { Text(it.originalName, maxLines = 1) },
                            trailingIcon = { Icon(Icons.Default.AttachFile, null) },
                        )
                    }
                }
            }

            if (slashSuggestions.isNotEmpty()) {
                SlashSuggestionPanel(
                    suggestions = slashSuggestions,
                    onSelect = { suggestion ->
                        text = suggestion.command + if (suggestion.command.endsWith(" ")) "" else " "
                    },
                )
            }

            Row(
                Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                IconButton(onClick = { picker.launch(arrayOf("*/*")) }) {
                    Icon(Icons.Default.AttachFile, null)
                }
                IconButton(onClick = { pasteClipboardAttachment() }) {
                    Icon(Icons.Default.ContentPaste, null)
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Nhập lệnh hoặc prompt…") },
                    supportingText = {
                        Text(
                            text = "Gõ / để mở command palette",
                            fontFamily = FontFamily.Monospace,
                        )
                    },
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                    maxLines = 5,
                )
                FilledIconButton(
                    onClick = {
                        scope.launch {
                            busy = true
                            actionError = null
                            runCatching { repo.send(id, text, pending) }
                                .onFailure { actionError = it.message }
                                .onSuccess {
                                    text = ""
                                    pending = emptyList()
                                }
                            refresh()
                            busy = false
                        }
                    },
                    enabled = !busy && (text.isNotBlank() || pending.isNotEmpty()),
                ) {
                    Icon(Icons.Default.Send, null)
                }
            }
        }
    }
}

@Composable
private fun ControlButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(label, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun MessageBubble(m: SessionMessage, provider: String) {
    val isUser = m.role == "USER"
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.86f),
            shape = RoundedCornerShape(
                topStart = 14.dp,
                topEnd = 14.dp,
                bottomStart = if (isUser) 14.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 14.dp,
            ),
            color = if (isUser) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            border = BorderStroke(
                1.dp,
                if (isUser) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.outlineVariant,
            ),
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Text(
                    text = if (isUser) "YOU" else providerDisplayName(provider).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                )
                if (m.text.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(m.text)
                }
                m.attachments.forEach {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "📎 ${it.originalName}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}

@Composable
private fun TerminalOutputCard(provider: String, output: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF05080D),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${providerDisplayName(provider).uppercase()} · LIVE TERMINAL",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "●",
                    color = MaterialTheme.colorScheme.tertiary,
                    fontFamily = FontFamily.Monospace,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            SelectionContainer {
                Text(
                    text = output,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

@Composable
private fun SlashSuggestionPanel(
    suggestions: List<SlashSuggestion>,
    onSelect: (SlashSuggestion) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF080D14),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
        tonalElevation = 4.dp,
    ) {
        Column(Modifier.padding(vertical = 4.dp)) {
            Text(
                text = "COMMAND PALETTE",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
            )
            suggestions.take(7).forEach { suggestion ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(suggestion) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = suggestion.command,
                        modifier = Modifier.widthIn(min = 82.dp),
                        color = MaterialTheme.colorScheme.tertiary,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = suggestion.description,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            text = suggestion.kind,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }
    }
}

private fun slashSuggestionsFor(provider: String, input: String): List<SlashSuggestion> {
    val trimmed = input.trimStart()
    if (!trimmed.startsWith("/")) return emptyList()
    val query = trimmed.substringBefore(' ').lowercase()
    val all = when (provider.lowercase()) {
        "codex" -> listOf(
            SlashSuggestion("/model", "Đổi model và reasoning effort"),
            SlashSuggestion("/permissions", "Chọn quyền chạy tool"),
            SlashSuggestion("/review", "Review thay đổi code hiện tại"),
            SlashSuggestion("/skills", "Xem và dùng skills", "SKILLS"),
            SlashSuggestion("/status", "Xem model, approvals và token usage"),
            SlashSuggestion("/compact", "Rút gọn context hiện tại"),
            SlashSuggestion("/new", "Bắt đầu chat mới"),
            SlashSuggestion("/mcp", "Xem MCP tools"),
            SlashSuggestion("/rename", "Đổi tên thread"),
        )
        "grok" -> listOf(
            SlashSuggestion("/help", "Xem commands và phím tắt"),
            SlashSuggestion("/new", "Bắt đầu session mới"),
            SlashSuggestion("/resume", "Mở lại session trước"),
            SlashSuggestion("/context", "Xem mức sử dụng context"),
            SlashSuggestion("/compact", "Rút gọn lịch sử hội thoại"),
            SlashSuggestion("/skills", "Mở danh sách skills", "SKILLS"),
            SlashSuggestion("/mcps", "Mở danh sách MCP"),
            SlashSuggestion("/rename", "Đổi tên session"),
            SlashSuggestion("/plan", "Chuyển sang Plan mode"),
            SlashSuggestion("/auto", "Chuyển sang Auto mode"),
        )
        else -> listOf(
            SlashSuggestion("/help", "Xem commands khả dụng"),
            SlashSuggestion("/model", "Đổi model cho session"),
            SlashSuggestion("/status", "Xem trạng thái session"),
            SlashSuggestion("/context", "Xem context đang dùng"),
            SlashSuggestion("/compact", "Rút gọn context"),
            SlashSuggestion("/skills", "Liệt kê skills khả dụng", "SKILLS"),
            SlashSuggestion("/permissions", "Quản lý quyền tool"),
            SlashSuggestion("/resume", "Chuyển sang conversation khác"),
            SlashSuggestion("/rename", "Đổi hoặc tự sinh tên session"),
        )
    }
    if (query == "/") return all
    return all.filter { it.command.lowercase().startsWith(query) }
}
