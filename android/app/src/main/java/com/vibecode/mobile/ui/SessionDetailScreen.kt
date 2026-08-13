package com.vibecode.mobile.ui

import android.net.Uri
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vibecode.mobile.data.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun SessionDetailScreen(repo:VibeRepository,id:String,onBack:()->Unit){
    val ctx=LocalContext.current;val scope=rememberCoroutineScope();var session by remember{mutableStateOf<Session?>(null)};var text by remember{mutableStateOf("")};var pending by remember{mutableStateOf<List<Attachment>>(emptyList())};var busy by remember{mutableStateOf(false)}
    suspend fun refresh(){session=runCatching{repo.session(id)}.getOrNull()}
    LaunchedEffect(id){while(true){refresh();kotlinx.coroutines.delay(1500)}}
    fun pasteClipboardAttachment(){
        val cm=ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip=cm.primaryClip ?: return
        val uri=clip.getItemAt(0).uri ?: return
        scope.launch { busy=true; runCatching{repo.upload(id,ctx.contentResolver,uri)}.onSuccess{pending=pending+it}; busy=false }
    }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()){uris:List<Uri>->scope.launch{busy=true;for(uri in uris){runCatching{repo.upload(id,ctx.contentResolver,uri)}.onSuccess{pending=pending+it}};busy=false}}
    Scaffold(topBar={TopAppBar(title={Text(session?.title ?: "Session")},navigationIcon={IconButton(onBack){Icon(Icons.Default.ArrowBack,null)}},actions={IconButton(onClick={scope.launch{repo.stop(id);refresh()}}){Icon(Icons.Default.Stop,null)}})}){pad->Column(Modifier.padding(pad).fillMaxSize()){
        session?.let{s->Row(Modifier.padding(horizontal=16.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){AssistChip({}, {Text(s.provider)});StatusBadge(s.status)}}
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){session?.messages?.let{msgs->items(msgs,key={it.id}){m->MessageCard(m)}};session?.lastOutput?.takeIf{it.isNotBlank()}?.let{out->item{Card{Text(out,Modifier.padding(12.dp),style=MaterialTheme.typography.bodySmall)}}}}
        if(pending.isNotEmpty())Row(Modifier.padding(horizontal=12.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)){pending.take(4).forEach{InputChip(selected=true,onClick={},label={Text(it.originalName,maxLines=1)},trailingIcon={Icon(Icons.Default.AttachFile,null)})}}
        Row(Modifier.padding(12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){IconButton(onClick={picker.launch(arrayOf("*/*"))}){Icon(Icons.Default.AttachFile,null)};IconButton(onClick={pasteClipboardAttachment()}){Icon(Icons.Default.ContentPaste,null)};OutlinedTextField(text,{text=it},Modifier.weight(1f),placeholder={Text("Nhập phản hồi...")},maxLines=5);FilledIconButton(onClick={scope.launch{busy=true;runCatching{repo.send(id,text,pending)};text="";pending=emptyList();refresh();busy=false}},enabled=!busy&&(text.isNotBlank()||pending.isNotEmpty())){Icon(Icons.Default.Send,null)}}
    }}
}
@Composable private fun MessageCard(m:SessionMessage){Card{Column(Modifier.padding(12.dp)){Text(if(m.role=="USER")"Bạn" else m.role,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary);if(m.text.isNotBlank())Text(m.text);m.attachments.forEach{Text("📎 ${it.originalName}",style=MaterialTheme.typography.bodySmall)}}}}
