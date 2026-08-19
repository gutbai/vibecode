package com.vibecode.mobile.data

import android.content.ContentResolver
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class ApiClient(private val machine: MachineConfig) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
    private val baseUrl = machine.baseUrl.trim().trimEnd('/')
    private val token = machine.token.trim()

    private fun url(path:String) = baseUrl + path
    private fun req(path:String) = Request.Builder().url(url(path)).header("Authorization","Bearer $token")

    private suspend inline fun <reified T> get(path:String):T = withContext(Dispatchers.IO) {
        http.newCall(req(path).get().build()).execute().use { r ->
            val body=r.body?.string().orEmpty(); if(!r.isSuccessful) throw ApiException(r.code, parseError(body)); json.decodeFromString<T>(body)
        }
    }
    private suspend inline fun <reified T,reified I> post(path:String,input:I):T = withContext(Dispatchers.IO) {
        val body=json.encodeToString(input).toRequestBody("application/json".toMediaTypeOrNull())
        http.newCall(req(path).post(body).build()).execute().use { r -> val text=r.body?.string().orEmpty(); if(!r.isSuccessful) throw ApiException(r.code, parseError(text)); json.decodeFromString<T>(text) }
    }
    private suspend inline fun <reified T,reified I> put(path:String,input:I):T = withContext(Dispatchers.IO) {
        val body=json.encodeToString(input).toRequestBody("application/json".toMediaTypeOrNull())
        http.newCall(req(path).put(body).build()).execute().use { r -> val text=r.body?.string().orEmpty(); if(!r.isSuccessful) throw ApiException(r.code, parseError(text)); json.decodeFromString<T>(text) }
    }
    private fun parseError(raw:String)=runCatching{json.decodeFromString<ServerError>(raw).error}.getOrDefault(raw.ifBlank{"Request failed"})

    suspend fun projects():List<Project> = get("/api/projects")
    suspend fun sessions():List<Session> = get("/api/sessions")
    suspend fun session(id:String):Session = get("/api/sessions/$id")
    suspend fun machine():MachineInfo = get("/api/machine")
    suspend fun logs(limit:Int=300):List<AgentLogEntry> = get("/api/logs?limit=${limit.coerceIn(1,1000)}")
    suspend fun terminalProbe(sessionId:String):String = withContext(Dispatchers.IO) {
        val path="/ws/terminal/${enc(sessionId)}?token=${enc(token)}"
        http.newCall(Request.Builder().url(url(path)).get().build()).execute().use { r ->
            val body=r.body?.string().orEmpty().replace('\n',' ').replace('\r',' ').take(500)
            "HTTP ${r.code} ${r.message}${if(body.isBlank()) "" else ": $body"}"
        }
    }
    suspend fun files(projectId:String,path:String):List<FileNode> = get("/api/projects/$projectId/files?path=${enc(path)}")
    suspend fun readFile(projectId:String,path:String):FileContentResponse = get("/api/projects/$projectId/file?path=${enc(path)}")
    suspend fun writeFile(projectId:String,path:String,content:String,expectedSha256:String):FileWriteResponse =
        put("/api/projects/$projectId/file", FileWriteRequest(path, content, expectedSha256))
    suspend fun fileHistory(projectId:String,path:String):List<FileRevision> =
        get("/api/projects/$projectId/file/history?path=${enc(path)}")
    suspend fun readFileRevision(projectId:String,path:String,revisionId:String):FileRevisionContent =
        get("/api/projects/$projectId/file/revision?path=${enc(path)}&revisionId=${enc(revisionId)}")
    suspend fun restoreFileRevision(projectId:String,path:String,revisionId:String,expectedSha256:String):FileWriteResponse =
        post("/api/projects/$projectId/file/restore", FileRestoreRequest(path, revisionId, expectedSha256))
    suspend fun search(projectId:String,q:String):List<SearchResult> = get("/api/projects/$projectId/search?q=${enc(q)}&limit=500")
    suspend fun slash(projectId:String,provider:String):List<SlashItem> = get("/api/projects/$projectId/slash?provider=${enc(provider)}")
    suspend fun sendMessage(sessionId:String,text:String,attachments:List<Attachment>):SessionMessage =
        post("/api/sessions/$sessionId/messages", SendMessageRequest(text, attachments.map { it.id }))
    suspend fun sendControl(sessionId:String,keys:List<String>) {
        post<OkResponse,Map<String,List<String>>>("/api/sessions/$sessionId/keys", mapOf("keys" to keys))
    }
    suspend fun createSession(provider:String,projectId:String,title:String,prompt:String):Session =
        post("/api/sessions", CreateSessionRequest(provider, projectId, title, prompt))
    suspend fun stopSession(id:String) { post<OkResponse,Map<String,String>>("/api/sessions/$id/stop", emptyMap()) }
    suspend fun upload(sessionId:String,resolver:ContentResolver,uri:Uri):Attachment = withContext(Dispatchers.IO) {
        val name = resolver.query(uri,arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),null,null,null)?.use { c -> if(c.moveToFirst()) c.getString(0) else null } ?: "attachment"
        val mime=resolver.getType(uri) ?: "application/octet-stream"
        val tmp=File.createTempFile("vibecode-","-upload")
        try {
            resolver.openInputStream(uri)!!.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            val multipart=MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("file",name,tmp.asRequestBody(mime.toMediaTypeOrNull())).build()
            http.newCall(req("/api/sessions/$sessionId/attachments").post(multipart).build()).execute().use { r -> val text=r.body?.string().orEmpty(); if(!r.isSuccessful) throw ApiException(r.code, parseError(text)); json.decodeFromString<Attachment>(text) }
        } finally { tmp.delete() }
    }
    fun socket(onEvent:(Event)->Unit,onClosed:(Throwable?)->Unit):WebSocket {
        val wsBase=baseUrl.replaceFirst("https://","wss://").replaceFirst("http://","ws://")
        val request=Request.Builder().url("$wsBase/ws?token=${enc(token)}").build()
        return http.newWebSocket(request,object:WebSocketListener(){
            override fun onMessage(webSocket: WebSocket, text: String) { runCatching{json.decodeFromString<Event>(text)}.onSuccess(onEvent) }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { onClosed(t) }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { onClosed(null) }
        })
    }
    private fun enc(s:String)=URLEncoder.encode(s,"UTF-8")
}
