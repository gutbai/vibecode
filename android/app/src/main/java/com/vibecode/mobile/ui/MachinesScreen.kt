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
import com.vibecode.mobile.data.MachineConfig
import java.util.UUID

@Composable fun MachinesScreen(items:List<MachineConfig>,selectedId:String?,onSelect:(MachineConfig)->Unit,onSave:(MachineConfig)->Unit){var add by remember{mutableStateOf(false)};Column(Modifier.fillMaxSize().padding(16.dp)){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("Machines",style=MaterialTheme.typography.headlineMedium);FilledIconButton({add=true}){Icon(Icons.Default.Add,null)}};Text("Các VPS/PC chạy VibeCode Agent");Spacer(Modifier.height(12.dp));LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)){items(items,key={it.id}){m->ElevatedCard(Modifier.fillMaxWidth().clickable{onSelect(m)}){Row(Modifier.padding(14.dp).fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Column{Text(m.name,style=MaterialTheme.typography.titleMedium);Text(m.baseUrl)};RadioButton(selectedId==m.id,{onSelect(m)})}}}}};if(add)MachineDialog({add=false}){onSave(it);add=false}}
@Composable private fun MachineDialog(onDismiss:()->Unit,onSave:(MachineConfig)->Unit){var name by remember{mutableStateOf("VPS Main")};var url by remember{mutableStateOf("https://")};var token by remember{mutableStateOf("")};AlertDialog(onDismissRequest=onDismiss,title={Text("Thêm machine")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){OutlinedTextField(name,{name=it},label={Text("Tên")});OutlinedTextField(url,{url=it},label={Text("Agent URL")});OutlinedTextField(token,{token=it},label={Text("Token")})}},confirmButton={Button({onSave(MachineConfig(UUID.randomUUID().toString(),name,url,token))},enabled=name.isNotBlank()&&url.isNotBlank()&&token.isNotBlank()){Text("Lưu")}},dismissButton={TextButton(onDismiss){Text("Hủy")}})}
