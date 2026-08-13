package com.vibecode.mobile.data

import android.content.ContentResolver
import android.net.Uri
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.WebSocket

class VibeRepository(private var config:MachineConfig?) {
    private var api=config?.let(::ApiClient)
    private var socket:WebSocket?=null
    private val _sessions=MutableStateFlow<List<Session>>(emptyList()); val sessions:StateFlow<List<Session>> = _sessions
    private val _projects=MutableStateFlow<List<Project>>(emptyList()); val projects:StateFlow<List<Project>> = _projects
    private val _machine=MutableStateFlow<MachineInfo?>(null); val machine:StateFlow<MachineInfo?> = _machine
    private val _error=MutableStateFlow<String?>(null); val error:StateFlow<String?> = _error

    fun setMachine(c:MachineConfig?){ socket?.cancel(); config=c; api=c?.let(::ApiClient); _sessions.value=emptyList(); _projects.value=emptyList(); _machine.value=null }
    suspend fun refresh(){ val a=api?:return; runCatching{coroutineScope{ val s=async{a.sessions()}; val p=async{a.projects()}; val m=async{a.machine()}; Triple(s.await(),p.await(),m.await()) }}.onSuccess{_sessions.value=it.first;_projects.value=it.second;_machine.value=it.third;_error.value=null}.onFailure{_error.value=it.message} }
    fun connect(scope:CoroutineScope){ val a=api?:return; socket?.cancel(); socket=a.socket(onEvent={e-> if(e.type.startsWith("session.")) scope.launch{refresh()}},onClosed={}) }
    suspend fun session(id:String)=api!!.session(id)
    suspend fun send(id:String,text:String,attachments:List<Attachment>)=api!!.sendMessage(id,text,attachments)
    suspend fun upload(id:String,resolver:ContentResolver,uri:Uri)=api!!.upload(id,resolver,uri)
    suspend fun stop(id:String)=api!!.stopSession(id)
    suspend fun create(provider:String,projectId:String,title:String,prompt:String)=api!!.createSession(provider,projectId,title,prompt)
    suspend fun files(projectId:String,path:String)=api!!.files(projectId,path)
    suspend fun readFile(projectId:String,path:String)=api!!.readFile(projectId,path)
    suspend fun search(projectId:String,q:String)=api!!.search(projectId,q)
}
