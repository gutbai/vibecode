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
    private val _connected=MutableStateFlow(false); val connected:StateFlow<Boolean> = _connected

    fun setMachine(c:MachineConfig?){
        socket?.cancel()
        config=c
        api=c?.let(::ApiClient)
        _sessions.value=emptyList()
        _projects.value=emptyList()
        _machine.value=null
        _connected.value=false
        _error.value=null
    }

    fun terminalWebSocketUrl(sessionId:String):String? {
        val c=config ?: return null
        val base=c.baseUrl.trim().trimEnd('/')
        if(base.isBlank()) return null
        val wsBase=when {
            base.startsWith("https://",ignoreCase=true) -> "wss://"+base.substring(8)
            base.startsWith("http://",ignoreCase=true) -> "ws://"+base.substring(7)
            base.startsWith("wss://",ignoreCase=true) || base.startsWith("ws://",ignoreCase=true) -> base
            else -> "ws://$base"
        }
        return "$wsBase/ws/terminal/${Uri.encode(sessionId)}?token=${Uri.encode(c.token)}"
    }

    suspend fun refresh(){
        val a=api ?: run {
            _connected.value=false
            _error.value="Chưa chọn machine"
            return
        }
        try {
            val result=coroutineScope{
                val s=async{a.sessions()}
                val p=async{a.projects()}
                val m=async{a.machine()}
                Triple(s.await(),p.await(),m.await())
            }
            _sessions.value=result.first
            _projects.value=result.second
            _machine.value=result.third
            _connected.value=true
            _error.value=null
        } catch(t:Throwable) {
            _connected.value=false
            _error.value=describe(t)
        }
    }

    fun connect(scope:CoroutineScope){
        val a=api?:return
        socket?.cancel()
        socket=a.socket(
            onEvent={e-> if(e.type.startsWith("session.")) scope.launch{refresh()}},
            onClosed={t-> if(t!=null){ _error.value=describe(t) }}
        )
    }

    private suspend fun <T> call(block:suspend (ApiClient)->T):T {
        val a=api ?: error("Chưa chọn machine")
        return try {
            block(a).also {
                _connected.value=true
                _error.value=null
            }
        } catch(t:Throwable) {
            _error.value=describe(t)
            throw t
        }
    }

    private fun describe(t:Throwable):String = when(t) {
        is ApiException -> "HTTP ${t.statusCode}: ${t.message ?: "Request failed"}"
        else -> t.message ?: t::class.simpleName ?: "Không kết nối được Agent"
    }

    suspend fun session(id:String)=call{it.session(id)}
    suspend fun send(id:String,text:String,attachments:List<Attachment>)=call{it.sendMessage(id,text,attachments)}
    suspend fun sendControl(id:String,vararg keys:String)=call{it.sendControl(id,keys.toList())}
    suspend fun upload(id:String,resolver:ContentResolver,uri:Uri)=call{it.upload(id,resolver,uri)}
    suspend fun stop(id:String)=call{it.stopSession(id)}
    suspend fun create(provider:String,projectId:String,title:String,prompt:String)=call{it.createSession(provider,projectId,title,prompt)}
    suspend fun files(projectId:String,path:String)=call{it.files(projectId,path)}
    suspend fun readFile(projectId:String,path:String)=call{it.readFile(projectId,path)}
    suspend fun writeFile(projectId:String,path:String,content:String,expectedSha256:String)=call{it.writeFile(projectId,path,content,expectedSha256)}
    suspend fun fileHistory(projectId:String,path:String)=call{it.fileHistory(projectId,path)}
    suspend fun readFileRevision(projectId:String,path:String,revisionId:String)=call{it.readFileRevision(projectId,path,revisionId)}
    suspend fun restoreFileRevision(projectId:String,path:String,revisionId:String,expectedSha256:String)=call{it.restoreFileRevision(projectId,path,revisionId,expectedSha256)}
    suspend fun search(projectId:String,q:String)=call{it.search(projectId,q)}
    suspend fun slash(projectId:String,provider:String)=call{it.slash(projectId,provider)}
}
