package com.vibecode.mobile.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class Project(val id:String,val name:String,val path:String)

@Serializable
data class Attachment(
    val id:String,
    val sessionId:String,
    val originalName:String,
    val localPath:String,
    val mimeType:String="",
    val size:Long=0,
    val sha256:String="",
    val createdAt:String=""
)

@Serializable
data class SessionMessage(
    val id:String,
    val sessionId:String,
    val role:String,
    val text:String="",
    val attachments:List<Attachment> = emptyList(),
    val createdAt:String=""
)

@Serializable
data class Session(
    val id:String,
    val title:String="",
    val provider:String,
    val projectId:String,
    val projectName:String="",
    val projectPath:String="",
    val machineName:String="",
    @SerialName("tmuxName") val tMuxName:String="",
    val status:String="RUNNING",
    val startedAt:String="",
    val updatedAt:String="",
    val lastOutput:String="",
    val messages:List<SessionMessage> = emptyList()
)

@Serializable
data class FileNode(val name:String,val path:String,val isDir:Boolean,val size:Long=0,val modTime:String="")
@Serializable
data class SearchResult(val filePath:String,val lineNumber:Int,val preview:String)
@Serializable
data class SlashItem(val command:String,val description:String="",val kind:String="COMMAND")
@Serializable
data class MachineInfo(val name:String,val os:String,val arch:String,val cpus:Int,val memoryTotalKb:Long=0,val memoryAvailableKb:Long=0)
@Serializable
data class ServerError(val error:String="Unknown error")
@Serializable
data class Event(val type:String,val sessionId:String="",val data:kotlinx.serialization.json.JsonElement?=null,val at:String="")

@Serializable
data class MachineConfig(val id:String,val name:String,val baseUrl:String,val token:String)

@Serializable data class CreateSessionRequest(val provider:String,val projectId:String,val title:String,val prompt:String)
@Serializable data class SendMessageRequest(val text:String,val attachmentIds:List<String>)
@Serializable data class FileContentResponse(val path:String,val content:String,val sha256:String="")
@Serializable data class FileWriteRequest(val path:String,val content:String,val expectedSha256:String="")
@Serializable data class FileWriteResponse(val ok:Boolean,val path:String="",val sha256:String="",val content:String="")
@Serializable data class FileRevision(val id:String,val sha256:String,val size:Long=0,val createdAt:String="")
@Serializable data class FileRevisionContent(val path:String,val content:String,val sha256:String,val createdAt:String="",val revision:FileRevision?=null)
@Serializable data class FileRestoreRequest(val path:String,val revisionId:String,val expectedSha256:String)
@Serializable data class OkResponse(val ok:Boolean)

class ApiException(val statusCode:Int, message:String): IllegalStateException(message)
