package com.vibecode.mobile.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vibecode.mobile.data.*
import kotlinx.coroutines.launch

@Composable fun SessionsScreen(repo:VibeRepository,enabled:Boolean,onOpen:(String)->Unit){
    val sessions by repo.sessions.collectAsState();val projects by repo.projects.collectAsState();val scope=rememberCoroutineScope()
    var filter by remember{mutableStateOf("ALL")};var create by remember{mutableStateOf(false)}
    val shown=remember(sessions,filter){if(filter=="ALL")sessions else sessions.filter{it.status==filter}}
    Column(Modifier.fillMaxSize().padding(16.dp)){
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Column{Text("VibeCode",style=MaterialTheme.typography.headlineMedium,color=MaterialTheme.colorScheme.primary);Text("Claude Code & Codex sessions")};FilledIconButton(onClick={create=true},enabled=enabled){Icon(Icons.Default.Add,null)}}
        Spacer(Modifier.height(16.dp));Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("ALL" to "Tất cả","RUNNING" to "Đang chạy","WAITING_INPUT" to "Cần input","DONE" to "Done").forEach{(v,l)->FilterChip(filter==v,{filter=v},{Text("$l ${if(v=="ALL")sessions.size else sessions.count{it.status==v}}")})}}
        Spacer(Modifier.height(12.dp));LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp)){items(shown,key={it.id}){s->SessionCard(s){onOpen(s.id)}}}
    }
    if(create)CreateSessionDialog(projects,onDismiss={create=false}){provider,project,title,prompt->scope.launch{runCatching{repo.create(provider,project,title,prompt)};repo.refresh();create=false}}
}

@Composable private fun SessionCard(s:Session,onClick:()->Unit){
    ElevatedCard(Modifier.fillMaxWidth().clickable(onClick=onClick)){Column(Modifier.padding(14.dp)){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(s.title.ifBlank{"Untitled session"},style=MaterialTheme.typography.titleMedium);StatusBadge(s.status)};Spacer(Modifier.height(6.dp));Text("${s.provider} · ${s.projectName}",color=MaterialTheme.colorScheme.onSurfaceVariant);Text(s.machineName,style=MaterialTheme.typography.bodySmall)}}
}
@Composable fun StatusBadge(status:String){val c=when(status){"WAITING_INPUT"->MaterialTheme.colorScheme.tertiary;"ERROR"->MaterialTheme.colorScheme.error;"DONE"->MaterialTheme.colorScheme.secondary;else->MaterialTheme.colorScheme.primary};AssistChip({}, {Text(status.replace('_',' '),color=c)})}

@Composable private fun CreateSessionDialog(projects:List<Project>,onDismiss:()->Unit,onCreate:(String,String,String,String)->Unit){
    var provider by remember{mutableStateOf("claude")};var project by remember{mutableStateOf(projects.firstOrNull()?.id.orEmpty())};var title by remember{mutableStateOf("")};var prompt by remember{mutableStateOf("")}
    AlertDialog(onDismissRequest=onDismiss,title={Text("Tạo session")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){Row{FilterChip(provider=="claude",{provider="claude"},{Text("Claude")});Spacer(Modifier.width(8.dp));FilterChip(provider=="codex",{provider="codex"},{Text("Codex")})};projects.forEach{p->FilterChip(project==p.id,{project=p.id},{Text(p.name)})};OutlinedTextField(title,{title=it},label={Text("Tiêu đề")});OutlinedTextField(prompt,{prompt=it},label={Text("Prompt ban đầu")},minLines=3)}},confirmButton={Button(onClick={onCreate(provider,project,title,prompt)},enabled=project.isNotBlank()){Text("Start")}},dismissButton={TextButton(onDismiss){Text("Hủy")}})
}
