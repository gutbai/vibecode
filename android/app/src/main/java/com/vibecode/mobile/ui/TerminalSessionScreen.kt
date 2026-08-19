package com.vibecode.mobile.ui

import android.annotation.SuppressLint
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color as AndroidColor
import android.view.inputmethod.InputMethodManager
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.vibecode.mobile.data.Session
import com.vibecode.mobile.data.VibeRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable
fun SessionWorkspaceScreen(repo: VibeRepository, id: String, onBack: () -> Unit) {
    var chatMode by remember(id) { mutableStateOf(false) }
    if (chatMode) {
        SessionDetailScreen(repo, id, onBack = { chatMode = false })
    } else {
        RealTerminalSessionScreen(
            repo = repo,
            id = id,
            onBack = onBack,
            onOpenChat = { chatMode = true },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RealTerminalSessionScreen(
    repo: VibeRepository,
    id: String,
    onBack: () -> Unit,
    onOpenChat: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var session by remember(id) { mutableStateOf<Session?>(null) }
    var terminalView by remember(id) { mutableStateOf<WebView?>(null) }
    var actionError by remember(id) { mutableStateOf<String?>(null) }
    val terminalUrl = remember(id) { repo.terminalWebSocketUrl(id) }

    LaunchedEffect(id) {
        while (true) {
            runCatching { repo.session(id) }
                .onSuccess { session = it }
                .onFailure { actionError = it.message }
            delay(2000)
        }
    }

    fun runJs(script: String) {
        terminalView?.evaluateJavascript(script, null)
    }

    fun sendSpecialKey(name: String) {
        runJs("window.vibeKey(${JSONObject.quote(name)});")
    }

    fun pasteClipboard() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip ?: return
        val text = clip.getItemAt(0).coerceToText(context)?.toString().orEmpty()
        if (text.isNotEmpty()) {
            runJs("window.vibePaste(${JSONObject.quote(text)});")
        }
    }

    fun showKeyboard() {
        terminalView?.let { webView ->
            webView.requestFocus()
            runJs("window.vibeFocus();")
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(webView, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(session?.title?.ifBlank { "Terminal" } ?: "Terminal", maxLines = 1)
                        session?.let {
                            Text(
                                "${providerDisplayName(it.provider)} · ${it.projectName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(onClick = onOpenChat) {
                        Icon(Icons.Default.ChatBubbleOutline, "Chat mode")
                    }
                    IconButton(onClick = {
                        scope.launch {
                            runCatching { repo.stop(id) }
                                .onFailure { actionError = it.message }
                        }
                    }) {
                        Icon(Icons.Default.Stop, "Stop session")
                    }
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "REAL PTY",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.tertiary,
                )
                Text(
                    session?.status ?: "CONNECTING",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "tmux",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (terminalUrl == null) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("Chưa có kết nối VPS cho terminal.")
                }
            } else {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF030609),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    RemoteTerminalWebView(
                        websocketUrl = terminalUrl,
                        modifier = Modifier.fillMaxSize(),
                        onViewReady = { terminalView = it },
                    )
                }
            }

            actionError?.let {
                Text(
                    it,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 6.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TerminalKey("Esc") { sendSpecialKey("ESC") }
                TerminalKey("Tab") { sendSpecialKey("TAB") }
                TerminalKey("←") { sendSpecialKey("LEFT") }
                TerminalKey("↑") { sendSpecialKey("UP") }
                TerminalKey("↓") { sendSpecialKey("DOWN") }
                TerminalKey("→") { sendSpecialKey("RIGHT") }
                TerminalKey("Enter") { sendSpecialKey("ENTER") }
                TerminalKey("^C") { sendSpecialKey("CTRL_C") }
                TerminalKey("Paste") { pasteClipboard() }
                FilledTonalIconButton(onClick = { showKeyboard() }) {
                    Icon(Icons.Default.Keyboard, "Show keyboard")
                }
            }
        }
    }
}

@Composable
private fun TerminalKey(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 11.dp, vertical = 7.dp),
    ) {
        Text(label, fontFamily = FontFamily.Monospace)
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun RemoteTerminalWebView(
    websocketUrl: String,
    modifier: Modifier = Modifier,
    onViewReady: (WebView) -> Unit,
) {
    val html = remember(websocketUrl) { terminalHtml(websocketUrl) }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(AndroidColor.rgb(3, 6, 9))
                isFocusable = true
                isFocusableInTouchMode = true
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.javaScriptCanOpenWindowsAutomatically = false
                settings.setSupportMultipleWindows(false)
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                webViewClient = WebViewClient()
                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean = true
                }
                loadDataWithBaseURL(
                    "http://vibecode.local/",
                    html,
                    "text/html",
                    "UTF-8",
                    null,
                )
                onViewReady(this)
            }
        },
        update = { onViewReady(it) },
    )
}

private fun terminalHtml(websocketUrl: String): String {
    val quotedUrl = JSONObject.quote(websocketUrl)
    return """
<!doctype html>
<html>
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no" />
  <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/@xterm/xterm@6.0.0/css/xterm.css" />
  <style>
    html, body, #terminal { width:100%; height:100%; margin:0; padding:0; background:#030609; overflow:hidden; }
    body { touch-action: manipulation; }
    #terminal { box-sizing:border-box; padding:6px 4px 2px 6px; }
    #status { position:fixed; right:8px; top:5px; z-index:20; padding:2px 6px; border-radius:5px;
      font:11px monospace; color:#94a3b8; background:rgba(3,6,9,.82); pointer-events:none; }
    .xterm .xterm-viewport { scrollbar-width:thin; }
  </style>
</head>
<body>
  <div id="terminal"></div>
  <div id="status">connecting…</div>
  <script type="module">
    import { Terminal } from 'https://cdn.jsdelivr.net/npm/@xterm/xterm@6.0.0/+esm';
    import { FitAddon } from 'https://cdn.jsdelivr.net/npm/@xterm/addon-fit@0.11.0/+esm';

    const status = document.getElementById('status');
    const term = new Terminal({
      cursorBlink: true,
      cursorStyle: 'block',
      fontFamily: 'monospace',
      fontSize: 14,
      lineHeight: 1.12,
      scrollback: 5000,
      allowTransparency: false,
      theme: {
        background: '#030609', foreground: '#e5e7eb', cursor: '#a78bfa', cursorAccent: '#030609',
        selectionBackground: '#334155', black: '#111827', brightBlack: '#64748b'
      }
    });
    const fit = new FitAddon();
    term.loadAddon(fit);
    term.open(document.getElementById('terminal'));

    let socket = null;
    let reconnectTimer = null;
    let disposed = false;
    const wsUrl = $quotedUrl;
    const decoder = new TextDecoder('utf-8');

    function sendObject(value) {
      if (socket && socket.readyState === WebSocket.OPEN) socket.send(JSON.stringify(value));
    }
    function sendInput(data) { sendObject({type:'input', data}); }
    function fitAndNotify() {
      try { fit.fit(); } catch (_) {}
      sendObject({type:'resize', cols:term.cols, rows:term.rows});
    }
    function connect() {
      if (disposed) return;
      status.textContent = 'connecting…';
      status.style.color = '#94a3b8';
      socket = new WebSocket(wsUrl);
      socket.binaryType = 'arraybuffer';
      socket.onopen = () => {
        status.textContent = 'live';
        status.style.color = '#34d399';
        fitAndNotify();
        setTimeout(fitAndNotify, 120);
      };
      socket.onmessage = async (event) => {
        if (typeof event.data === 'string') {
          try {
            const message = JSON.parse(event.data);
            if (message.type === 'error') term.writeln('\r\n\x1b[31m[VibeCode] ' + message.message + '\x1b[0m');
          } catch (_) {
            term.write(event.data);
          }
          return;
        }
        if (event.data instanceof ArrayBuffer) {
          term.write(decoder.decode(new Uint8Array(event.data), {stream:true}));
        } else if (event.data instanceof Blob) {
          const data = new Uint8Array(await event.data.arrayBuffer());
          term.write(decoder.decode(data, {stream:true}));
        }
      };
      socket.onclose = () => {
        status.textContent = 'reconnecting…';
        status.style.color = '#f59e0b';
        if (!disposed) reconnectTimer = setTimeout(connect, 1500);
      };
      socket.onerror = () => {
        status.textContent = 'connection error';
        status.style.color = '#f87171';
      };
    }

    term.onData(sendInput);
    term.onResize(({cols, rows}) => sendObject({type:'resize', cols, rows}));
    new ResizeObserver(() => setTimeout(fitAndNotify, 40)).observe(document.getElementById('terminal'));

    window.vibeFocus = () => term.focus();
    window.vibePaste = (text) => { sendInput(String(text || '')); term.focus(); };
    window.vibeKey = (name) => {
      const keys = {
        ESC:'\x1b', TAB:'\t', ENTER:'\r', CTRL_C:'\x03',
        LEFT:'\x1b[D', RIGHT:'\x1b[C', UP:'\x1b[A', DOWN:'\x1b[B'
      };
      const data = keys[name];
      if (data) sendInput(data);
      term.focus();
    };
    window.addEventListener('beforeunload', () => {
      disposed = true;
      if (reconnectTimer) clearTimeout(reconnectTimer);
      if (socket) socket.close();
    });

    connect();
    setTimeout(fitAndNotify, 80);
  </script>
</body>
</html>
""".trimIndent()
}
