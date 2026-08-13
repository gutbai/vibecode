package com.vibecode.mobile.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vibecode.mobile.data.*
import kotlinx.coroutines.launch
import java.util.UUID

private enum class Tab{Sessions,Files,Machines}

@Composable fun VibeCodeApp(){
    val context=LocalContext.current
    val store=remember{ConnectionStore(context)}
    var machines by remember{mutableStateOf(store.loadMachines())}
    var selected by remember{mutableStateOf(store.selectedId()?.let{ id->machines.find{it.id==id}} ?: machines.firstOrNull())}
    val repo=remember{VibeRepository(selected)}
    val scope=rememberCoroutineScope()
    var tab by remember{mutableStateOf(Tab.Sessions)}
    var detailId by remember{mutableStateOf<String?>(null)}
    LaunchedEffect(selected?.id){repo.setMachine(selected);repo.refresh();repo.connect(scope)}

    if(detailId!=null){SessionDetailScreen(repo,detailId!!,onBack={detailId=null});return}
    Scaffold(bottomBar={NavigationBar{
        NavigationBarItem(selected=tab==Tab.Sessions,onClick={tab=Tab.Sessions},icon={Icon(Icons.Default.Terminal,null)},label={Text("Sessions")})
        NavigationBarItem(selected=tab==Tab.Files,onClick={tab=Tab.Files},icon={Icon(Icons.Default.Folder,null)},label={Text("Files")})
        NavigationBarItem(selected=tab==Tab.Machines,onClick={tab=Tab.Machines},icon={Icon(Icons.Default.Dns,null)},label={Text("Machines")})
    }}){pad->Box(Modifier.padding(pad).fillMaxSize()){
        when(tab){
            Tab.Sessions->SessionsScreen(repo,enabled=selected!=null,onOpen={detailId=it})
            Tab.Files->FilesScreen(repo,enabled=selected!=null)
            Tab.Machines->MachinesScreen(machines,selected?.id,onSelect={selected=it;store.select(it.id)},onSave={item->machines=(machines.filterNot{it.id==item.id}+item);store.saveMachines(machines);selected=item;store.select(item.id)})
        }
    }}
}
