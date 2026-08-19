package com.vibecode.mobile.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vibecode.mobile.data.*
import kotlinx.coroutines.launch

private enum class Tab{Sessions,Files,Logs,Machines}

@Composable fun VibeCodeApp(){
    val context=androidx.compose.ui.platform.LocalContext.current
    AppLog.init(context)
    val store=remember{ConnectionStore(context)}
    var machines by remember{mutableStateOf(store.loadMachines())}
    var selected by remember{mutableStateOf(store.selectedId()?.let{ id->machines.find{it.id==id}} ?: machines.firstOrNull())}
    val repo=remember{VibeRepository(selected)}
    val connected by repo.connected.collectAsState()
    val error by repo.error.collectAsState()
    val scope=rememberCoroutineScope()
    var tab by remember{mutableStateOf(Tab.Sessions)}
    var detailId by remember{mutableStateOf<String?>(null)}

    fun retry(){
        scope.launch {
            repo.refresh()
            repo.connect(scope)
        }
    }

    LaunchedEffect(selected?.id){
        repo.setMachine(selected)
        repo.refresh()
        repo.connect(scope)
    }

    if(detailId!=null){SessionWorkspaceScreen(repo,detailId!!,onBack={detailId=null});return}
    Scaffold(bottomBar={NavigationBar{
        NavigationBarItem(selected=tab==Tab.Sessions,onClick={tab=Tab.Sessions},icon={Icon(Icons.Default.Terminal,null)},label={Text("Sessions")})
        NavigationBarItem(selected=tab==Tab.Files,onClick={tab=Tab.Files},icon={Icon(Icons.Default.Folder,null)},label={Text("Files")})
        NavigationBarItem(selected=tab==Tab.Logs,onClick={tab=Tab.Logs},icon={Icon(Icons.Default.BugReport,null)},label={Text("Logs")})
        NavigationBarItem(selected=tab==Tab.Machines,onClick={tab=Tab.Machines},icon={Icon(Icons.Default.Dns,null)},label={Text("Machines")})
    }}){pad->
        Column(Modifier.padding(pad).fillMaxSize()){
            if(selected!=null && !connected){
                Surface(
                    modifier=Modifier.fillMaxWidth(),
                    color=if(error!=null) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                ){
                    Row(
                        modifier=Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=8.dp),
                        verticalAlignment=Alignment.CenterVertically,
                        horizontalArrangement=Arrangement.spacedBy(8.dp),
                    ){
                        Icon(Icons.Default.CloudOff,null)
                        Column(Modifier.weight(1f)){
                            Text(if(error==null) "Đang kết nối ${selected?.name.orEmpty()}..." else "Không kết nối được ${selected?.name.orEmpty()}",style=MaterialTheme.typography.labelLarge)
                            if(error!=null) Text(error.orEmpty(),style=MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick={retry()}){Text("Thử lại")}
                    }
                }
            }
            Box(Modifier.fillMaxSize()){
                when(tab){
                    Tab.Sessions->SessionsScreen(repo,enabled=selected!=null&&connected,onOpen={detailId=it})
                    Tab.Files->FilesScreen(repo,enabled=selected!=null&&connected)
                    Tab.Logs->LogsScreen(repo,enabled=selected!=null&&connected)
                    Tab.Machines->MachinesScreen(
                        items=machines,
                        selectedId=selected?.id,
                        onSelect={selected=it;store.select(it.id)},
                        onSave={item->
                            machines=(machines.filterNot{it.id==item.id}+item)
                            store.saveMachines(machines)
                            selected=item
                            store.select(item.id)
                        },
                    )
                }
            }
        }
    }
}
