package rw.itunda.core.saronite
import org.json.JSONObject
import java.util.UUID
object SaroniteProtocol { const val VERSION = 1 }
data class SaroniteRequest(val id:String,val capability:String,val method:String,val payload:JSONObject?=null,val timeoutMs:Long?=null)
data class SaroniteResponse(val id:String,val ok:Boolean,val result:JSONObject?=null,val error:SaroniteError?=null)
data class SaroniteError(val code:String,val message:String,val detail:JSONObject?=null)
data class SaroniteEvent(val id:String="evt_${UUID.randomUUID()}",val event:String,val lifecycle:String?=null,val permission:SaronitePermissionEvent?=null,val payload:JSONObject?=null)
data class SaronitePermissionEvent(val name:String,val state:String)
fun SaroniteRequest.toJson()=JSONObject().put("protocolVersion",1).put("kind","request").put("id",id).put("capability",capability).put("method",method).also{j->payload?.let{j.put("payload",it)};timeoutMs?.let{j.put("timeoutMs",it)}}
fun SaroniteResponse.toJson()=JSONObject().put("protocolVersion",1).put("kind","response").put("id",id).put("ok",ok).also{j->result?.let{j.put("result",it)};error?.let{e->j.put("error",JSONObject().put("code",e.code).put("message",e.message).also{d->e.detail?.let{d.put("detail",it)}})}}
fun SaroniteEvent.toJson()=JSONObject().put("protocolVersion",1).put("kind","event").put("id",id).put("event",event).also{j->lifecycle?.let{j.put("lifecycle",it)};permission?.let{j.put("permission",JSONObject().put("name",it.name).put("state",it.state))};payload?.let{j.put("payload",it)}}
