package com.vibecode.mobile.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vibecode.mobile.data.*
import kotlinx.coroutines.launch

@Composable fun FilesScreen(repo:VibeRepository,enabled:Boolean){
    val projects by repo.projects.collectAsState();val scope=rememberCoroutineScope();var projectId by remember(projects){mutableStateOf(projects.firstOrNull()?.id.orEmpty())};var path by remember{mutableStateOf("")};var nodes by remember{mutableStateOf<List<FileNode>>(emptyList())};var q by remember{mutableStateOf("")};var results by remember{mutableStateOf<List<SearchResult>>(emptyList())};var opened by remember{mutableStateOf<Pair<String,String>?>(null)}
    suspend fun refresh(){if(projectId.isNotBlank())nodes=runCatching{repo.files(projectId,path)}.getOrDefault(emptyList())}
    LaunchedEffect(projectId,path){refresh()}
    if(opened!=null){FileViewer(opened!!.first,opened!!.second){opened=null};return}
    Column(Modifier.fillMaxSize().padding(16.dp)){
        Text("Files & Search",style=MaterialTheme.typography.headlineMedium);Spacer(Modifier.height(10.dp));Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){projects.forEach{p->FilterChip(projectId==p.id,{projectId=p.id;path=""},{Text(p.name)})}}
        Spacer(Modifier.height(8.dp));Row{OutlinedTextField(q,{q=it},Modifier.weight(1f),placeholder={Text("Tìm nội dung trong project")},singleLine=true);IconButton(onClick={scope.launch{results=runCatching{repo.search(projectId,q)}.getOrDefault(emptyList())}}){Icon(Icons.Default.Search,null)}}
        if(results.isNotEmpty()){Text("${results.size} kết quả",style=MaterialTheme.typography.labelMedium);LazyColumn(Modifier.weight(.45f)){items(results){r->ListItem(headlineContent={Text("${r.filePath}:${r.lineNumber}")},supportingContent={Text(r.preview,maxLines=2)},modifier=Modifier.clickable{scope.launch{opened=r.filePath to runCatching{repo.readFile(projectId,r.filePath)}.getOrDefault("")}}})}}}
        Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){IconButton(onClick={path=path.substringBeforeLast('/',"")},enabled=path.isNotBlank()){Icon(Icons.Default.ArrowUpward,null)};Text(if(path.isBlank())"/" else "/$path")}
        LazyColumn(Modifier.weight(1f)){items(nodes,key={it.path}){n->ListItem(leadingContent={Icon(if(n.isDir)Icons.Default.Folder else Icons.Default.Description,null)},headlineContent={Text(n.name)},supportingContent={if(!n.isDir)Text("${n.size} bytes")},modifier=Modifier.clickable{if(n.isDir)path=n.path else scope.launch{opened=n.path to runCatching{repo.readFile(projectId,n.path)}.getOrDefault("")}})}}
    }
}
@Composable private fun FileViewer(path:String,content:String,onBack:()->Unit){Column(Modifier.fillMaxSize().padding(16.dp)){Row{IconButton(onBack){Icon(Icons.Default.ArrowBack,null)};Text(path,style=MaterialTheme.typography.titleMedium)};Card(Modifier.fillMaxSize()){androidx.compose.foundation.lazy.LazyColumn(Modifier.padding(12.dp)){item{Text(content,style=MaterialTheme.typography.bodySmall)}}}}}
