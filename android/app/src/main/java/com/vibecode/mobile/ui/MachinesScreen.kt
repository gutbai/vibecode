package com.vibecode.mobile.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vibecode.mobile.data.MachineConfig
import java.util.UUID

@Composable
fun MachinesScreen(
    items:List<MachineConfig>,
    selectedId:String?,
    onSelect:(MachineConfig)->Unit,
    onSave:(MachineConfig)->Unit,
){
    var add by remember{mutableStateOf(false)}
    var editing by remember{mutableStateOf<MachineConfig?>(null)}

    Column(Modifier.fillMaxSize().padding(16.dp)){
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
            Text("Machines",style=MaterialTheme.typography.headlineMedium)
            FilledIconButton({add=true}){Icon(Icons.Default.Add,null)}
        }
        Text("Các VPS/PC chạy VibeCode Agent")
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)){
            items(items,key={it.id}){m->
                ElevatedCard(Modifier.fillMaxWidth().clickable{onSelect(m)}){
                    Row(
                        Modifier.padding(14.dp).fillMaxWidth(),
                        horizontalArrangement=Arrangement.SpaceBetween,
                        verticalAlignment=Alignment.CenterVertically,
                    ){
                        Column(Modifier.weight(1f)){
                            Text(m.name,style=MaterialTheme.typography.titleMedium)
                            Text(m.baseUrl)
                        }
                        IconButton(onClick={editing=m}){Icon(Icons.Default.Edit,"Sửa machine")}
                        RadioButton(selected=selectedId==m.id,onClick={onSelect(m)})
                    }
                }
            }
        }
    }

    if(add){
        MachineDialog(initial=null,onDismiss={add=false}){onSave(it);add=false}
    }
    editing?.let{current->
        MachineDialog(initial=current,onDismiss={editing=null}){onSave(it);editing=null}
    }
}

@Composable
private fun MachineDialog(
    initial:MachineConfig?,
    onDismiss:()->Unit,
    onSave:(MachineConfig)->Unit,
){
    var name by remember(initial?.id){mutableStateOf(initial?.name ?: "VPS Main")}
    var url by remember(initial?.id){mutableStateOf(initial?.baseUrl ?: "https://")}
    var token by remember(initial?.id){mutableStateOf(initial?.token ?: "")}

    val cleanName=name.trim()
    val cleanUrl=url.trim().trimEnd('/')
    val cleanToken=token.trim()

    AlertDialog(
        onDismissRequest=onDismiss,
        title={Text(if(initial==null) "Thêm machine" else "Sửa machine")},
        text={
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
                OutlinedTextField(name,{name=it},label={Text("Tên")},singleLine=true)
                OutlinedTextField(url,{url=it},label={Text("Agent URL")},singleLine=true)
                OutlinedTextField(token,{token=it},label={Text("Token")},singleLine=true)
            }
        },
        confirmButton={
            Button(
                onClick={
                    onSave(MachineConfig(initial?.id ?: UUID.randomUUID().toString(),cleanName,cleanUrl,cleanToken))
                },
                enabled=cleanName.isNotBlank()&&cleanUrl.isNotBlank()&&cleanToken.isNotBlank(),
            ){Text("Lưu")}
        },
        dismissButton={TextButton(onDismiss){Text("Hủy")}},
    )
}
