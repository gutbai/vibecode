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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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

private data class TerminalChoice(
    val number: Int,
    val label: String,
)

private val terminalChoiceRegex = Regex("""^\s*(?:[❯›>•○●]\s*)?(\d{1,2})[.)]\s+(.+?)\s*$""")

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
    var remoteSlash by remember { mutableStateOf<List<SlashItem>>(emptyList()) }
    var terminalInputOption by remember { mutableStateOf<Int?>(null) }

    suspend fun refresh() {
        session = runCatching { repo.session(id) }.getOrNull()
    }

    LaunchedEffect(id) {
        while (true) {
            refresh()
            kotlinx.coroutines.delay(1200)
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

    fun sendQuickInput(value: String, afterSend: (() -> Unit)? = null) {
        if (busy) return
        scope.launch {
            busy = true
            actionError = null
            runCatching { repo.send(id, value, emptyList()) }
                .onSuccess { afterSend?.invoke() }
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
    val projectId = session?.projectId.orEmpty()

    LaunchedEffect(provider, projectId) {
        remoteSlash = if (provider.isNotBlank() && projectId.isNotBlank()) {
            runCatching { repo.slash(projectId, provider) }.getOrDefault(emptyList())
        } else emptyList()
    }

    val slashSuggestions = remember(text, provider, remoteSlash) {
        slashSuggestionsFor(provider, text, remoteSlash)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(session?.title?.ifBlank { "New session" } ?: "Session", maxLines = 1)
                        session?.let {
                            Text(
                                text = "${providerDisplayName(it.provider)} · ${it.projectName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                            )
                        }
                    }
                },
                navigationIcon = { IconButton(onBack) { Icon(Icons.Default.ArrowBack, null) } },
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            runCatching { repo.stop(id) }.onFailure { actionError = it.message }
                            refresh()
                        }
                    }) { Icon(Icons.Default.Stop, null) }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            session?.let { s ->
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = providerDisplayName(s.provider).uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                    )
                    StatusBadge(s.status)
                }
            }

            LazyColumn(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                session?.messages?.let { msgs ->
                    items(msgs, key = { it.id }) { m -> MessageBubble(m, provider) }
                }
                session?.lastOutput?.takeIf { it.isNotBlank() }?.let { out ->
                    item {
                        TerminalOutputCard(
                            provider = provider,
                            output = out,
                            enabled = !busy,
                            inlineInputOption = terminalInputOption,
                            onChoice = { choice ->
                                if (choice.isFreeTextChoice()) {
                                    sendQuickInput(choice.number.toString()) {
                                        terminalInputOption = choice.number
                                    }
                                } else {
                                    terminalInputOption = null
                                    sendQuickInput(choice.number.toString())
                                }
                            },
                            onInlineSubmit = { value ->
                                if (value.isNotBlank()) {
                                    sendQuickInput(value) { terminalInputOption = null }
                                }
                            },
                            onInlineCancel = { terminalInputOption = null },
                        )
                    }
                }
            }

            actionError?.let {
                Text(
                    text = it,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (controlsExpanded) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    ControlButton("Esc", !busy) { sendControl("ESC") }
                    ControlButton("←", !busy) { sendControl("LEFT") }
                    ControlButton("↑", !busy) { sendControl("UP") }
                    ControlButton("↓", !busy) { sendControl("DOWN") }
                    ControlButton("→", !busy) { sendControl("RIGHT") }
                    ControlButton("Enter", !busy) { sendControl("ENTER") }
                    ControlButton("Tab", !busy) { sendControl("TAB") }
                }
            }

            if (pending.isNotEmpty()) {
                Row(
                    Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    pending.take(8).forEach { attachment ->
                        InputChip(
                            selected = true,
                            onClick = {},
                            label = { Text(attachment.originalName, maxLines = 1) },
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

            TerminalComposer(
                value = text,
                onValueChange = { text = it },
                busy = busy,
                controlsExpanded = controlsExpanded,
                hasAttachments = pending.isNotEmpty(),
                onToggleControls = { controlsExpanded = !controlsExpanded },
                onAttach = { picker.launch(arrayOf("*/*")) },
                onPaste = { pasteClipboardAttachment() },
                onSend = {
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
            )
        }
    }
}

@Composable
private fun TerminalComposer(
    value: String,
    onValueChange: (String) -> Unit,
    busy: Boolean,
    controlsExpanded: Boolean,
    hasAttachments: Boolean,
    onToggleControls: () -> Unit,
    onAttach: () -> Unit,
    onPaste: () -> Unit,
    onSend: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        color = Color(0xFF05080D),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "❯",
                    color = MaterialTheme.colorScheme.tertiary,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 16.dp),
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Nhập prompt hoặc /command…") },
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                    minLines = 1,
                    maxLines = 4,
                    trailingIcon = {
                        IconButton(
                            onClick = onSend,
                            enabled = !busy && (value.isNotBlank() || hasAttachments),
                        ) { Icon(Icons.Default.ArrowUpward, "Send") }
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onAttach, enabled = !busy, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Icon(Icons.Default.AttachFile, null)
                    Spacer(Modifier.width(3.dp))
                    Text("Attach", fontFamily = FontFamily.Monospace)
                }
                TextButton(onClick = onPaste, enabled = !busy, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Icon(Icons.Default.ContentPaste, null)
                    Spacer(Modifier.width(3.dp))
                    Text("Paste", fontFamily = FontFamily.Monospace)
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onToggleControls, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Icon(Icons.Default.Keyboard, null)
                    Spacer(Modifier.width(3.dp))
                    Text(if (controlsExpanded) "Ẩn phím" else "Phím phụ", fontFamily = FontFamily.Monospace)
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
        contentPadding = PaddingValues(horizontal = 11.dp, vertical = 7.dp),
    ) { Text(label, fontFamily = FontFamily.Monospace) }
}

@Composable
private fun MessageBubble(m: SessionMessage, provider: String) {
    val isUser = m.role == "USER"
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = if (isUser) Color(0xFF071827) else Color(0xFF0B0F15),
        border = BorderStroke(
            1.dp,
            if (isUser) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                text = if (isUser) "YOU  ❯" else "${providerDisplayName(provider).uppercase()}  ❯",
                style = MaterialTheme.typography.labelSmall,
                color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.widthIn(min = 62.dp),
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                if (m.text.isNotBlank()) Text(m.text, fontFamily = FontFamily.Monospace)
                m.attachments.forEach {
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
private fun TerminalOutputCard(
    provider: String,
    output: String,
    enabled: Boolean,
    inlineInputOption: Int?,
    onChoice: (TerminalChoice) -> Unit,
    onInlineSubmit: (String) -> Unit,
    onInlineCancel: () -> Unit,
) {
    var inlineText by remember(inlineInputOption) { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val lines = remember(output) { output.lines().takeLast(90) }

    LaunchedEffect(inlineInputOption) {
        if (inlineInputOption != null) {
            kotlinx.coroutines.delay(120)
            runCatching { focusRequester.requestFocus() }
            keyboard?.show()
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF030609),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp),
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
                Text("●", color = MaterialTheme.colorScheme.tertiary)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
            Column(Modifier.padding(vertical = 7.dp)) {
                lines.forEach { line ->
                    val choice = parseTerminalChoice(line)
                    if (choice != null) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp, vertical = 1.dp)
                                .clickable(enabled = enabled) { onChoice(choice) },
                            color = if (choice.number == inlineInputOption) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.32f)
                            } else Color.Transparent,
                            shape = RoundedCornerShape(6.dp),
                        ) {
                            Row(
                                Modifier.padding(horizontal = 7.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Text(
                                    text = "${choice.number}.",
                                    color = MaterialTheme.colorScheme.tertiary,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(28.dp),
                                )
                                Text(
                                    text = choice.label,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = if (choice.isFreeTextChoice()) "✎" else "↵",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                        }
                    } else {
                        SelectionContainer {
                            Text(
                                text = if (line.isEmpty()) " " else line,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 1.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                }
            }

            if (inlineInputOption != null) {
                HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        "❯",
                        color = MaterialTheme.colorScheme.tertiary,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                    )
                    OutlinedTextField(
                        value = inlineText,
                        onValueChange = { inlineText = it },
                        modifier = Modifier.weight(1f).focusRequester(focusRequester),
                        placeholder = { Text("Nhập câu trả lời cho option $inlineInputOption…") },
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                        singleLine = false,
                        maxLines = 3,
                    )
                    IconButton(
                        onClick = { onInlineSubmit(inlineText) },
                        enabled = enabled && inlineText.isNotBlank(),
                    ) { Icon(Icons.Default.ArrowUpward, "Submit") }
                    IconButton(onClick = onInlineCancel) { Icon(Icons.Default.Close, "Cancel") }
                }
            }
        }
    }
}

private fun parseTerminalChoice(line: String): TerminalChoice? {
    val match = terminalChoiceRegex.matchEntire(line) ?: return null
    val number = match.groupValues[1].toIntOrNull() ?: return null
    val label = match.groupValues[2].trim()
    if (label.isBlank()) return null
    return TerminalChoice(number, label)
}

private fun TerminalChoice.isFreeTextChoice(): Boolean {
    val s = label.lowercase()
    return listOf(
        "type something",
        "something else",
        "other",
        "custom",
        "enter your own",
        "write your own",
        "nhập nội dung",
        "nhập câu trả lời",
        "khác",
    ).any { s.contains(it) }
}

@Composable
private fun SlashSuggestionPanel(
    suggestions: List<SlashSuggestion>,
    onSelect: (SlashSuggestion) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp),
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF080D14),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
    ) {
        Column(Modifier.padding(vertical = 3.dp)) {
            Text(
                text = "COMMAND PALETTE",
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
            )
            suggestions.take(8).forEach { suggestion ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onSelect(suggestion) }.padding(horizontal = 10.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = suggestion.command,
                        modifier = Modifier.widthIn(min = 78.dp),
                        color = MaterialTheme.colorScheme.tertiary,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Column(Modifier.weight(1f)) {
                        Text(suggestion.description, style = MaterialTheme.typography.bodySmall)
                        Text(
                            suggestion.kind,
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

private fun slashSuggestionsFor(
    provider: String,
    input: String,
    discovered: List<SlashItem>,
): List<SlashSuggestion> {
    val trimmed = input.trimStart()
    if (!trimmed.startsWith("/")) return emptyList()
    val query = trimmed.substringBefore(' ').lowercase()
    val fallback = when (provider.lowercase()) {
        "codex" -> listOf(
            SlashSuggestion("/model", "Đổi model và reasoning effort"),
            SlashSuggestion("/permissions", "Chọn quyền chạy tool"),
            SlashSuggestion("/review", "Review thay đổi code hiện tại"),
            SlashSuggestion("/skills", "Xem và dùng skills", "SKILLS"),
            SlashSuggestion("/status", "Xem model, approvals và token usage"),
            SlashSuggestion("/compact", "Rút gọn context hiện tại"),
            SlashSuggestion("/new", "Bắt đầu chat mới"),
            SlashSuggestion("/mcp", "Xem MCP tools"),
        )
        "grok" -> listOf(
            SlashSuggestion("/help", "Xem commands và phím tắt"),
            SlashSuggestion("/new", "Bắt đầu session mới"),
            SlashSuggestion("/resume", "Mở lại session trước"),
            SlashSuggestion("/context", "Xem mức sử dụng context"),
            SlashSuggestion("/compact", "Rút gọn lịch sử hội thoại"),
            SlashSuggestion("/skills", "Mở danh sách skills", "SKILLS"),
            SlashSuggestion("/mcps", "Mở danh sách MCP"),
            SlashSuggestion("/plan", "Chuyển sang Plan mode"),
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
        )
    }
    val all = if (discovered.isNotEmpty()) {
        discovered.map { SlashSuggestion(it.command, it.description, it.kind) }
    } else fallback
    if (query == "/") return all
    val needle = query.removePrefix("/")
    return all.filter { suggestion ->
        suggestion.command.lowercase().removePrefix("/").removePrefix("$").startsWith(needle)
    }
}
