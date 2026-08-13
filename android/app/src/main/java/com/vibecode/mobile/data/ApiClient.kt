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
    private fun url(path:String) = machine.baseUrl.trimEnd('/') + path
    private fun req(path:String) = Request.Builder().url(url(path)).header("Authorization","Bearer ${machine.token}")

    private suspend inline fun <reified T> get(path:String):T = withContext(Dispatchers.IO) {
        http.newCall(req(path).get().build()).execute().use { r ->
            val body=r.body?.string().orEmpty(); if(!r.isSuccessful) error(parseError(body)); json.decodeFromString<T>(body)
        }
    }
    private suspend inline fun <reified T,reified I> post(path:String,input:I):T = withContext(Dispatchers.IO) {
        val body=json.encodeToString(input).toRequestBody("application/json".toMediaTypeOrNull())
        http.newCall(req(path).post(body).build()).execute().use { r -> val text=r.body?.string().orEmpty(); if(!r.isSuccessful) error(parseError(text)); json.decodeFromString<T>(text) }
    }
    private fun parseError(raw:String)=runCatching{json.decodeFromString<ServerError>(raw).error}.getOrDefault(raw.ifBlank{"Request failed"})

    suspend fun projects():List<Project> = get("/api/projects")
    suspend fun sessions():List<Session> = get("/api/sessions")
    suspend fun session(id:String):Session = get("/api/sessions/$id")
    suspend fun machine():MachineInfo = get("/api/machine")
    suspend fun files(projectId:String,path:String):List<FileNode> = get("/api/projects/$projectId/files?path=${enc(path)}")
    suspend fun readFile(projectId:String,path:String):String = get<FileContentResponse>("/api/projects/$projectId/file?path=${enc(path)}").content
    suspend fun search(projectId:String,q:String):List<SearchResult> = get("/api/projects/$projectId/search?q=${enc(q)}&limit=200")
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
            http.newCall(req("/api/sessions/$sessionId/attachments").post(multipart).build()).execute().use { r -> val text=r.body?.string().orEmpty(); if(!r.isSuccessful) error(parseError(text)); json.decodeFromString<Attachment>(text) }
        } finally { tmp.delete() }
    }
    fun socket(onEvent:(Event)->Unit,onClosed:(Throwable?)->Unit):WebSocket {
        val wsBase=machine.baseUrl.trimEnd('/').replaceFirst("https://","wss://").replaceFirst("http://","ws://")
        val request=Request.Builder().url("$wsBase/ws?token=${enc(machine.token)}").build()
        return http.newWebSocket(request,object:WebSocketListener(){
            override fun onMessage(webSocket: WebSocket, text: String) { runCatching{json.decodeFromString<Event>(text)}.onSuccess(onEvent) }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { onClosed(t) }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { onClosed(null) }
        })
    }
    private fun enc(s:String)=URLEncoder.encode(s,"UTF-8")
}
