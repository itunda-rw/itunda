package rw.itunda.core.saronite
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
enum class SaroniteLifecycle { INSTALLING, INSTALLED, LAUNCHING, VISIBLE, HIDDEN, SUSPENDED, TERMINATED, FAILED }
enum class SaronitePermissionState { REQUESTED, GRANTED, DENIED, REVOKED }
interface SaroniteTransport { fun send(message:JSONObject); fun close() }
interface SaroniteCapabilityHandler { suspend fun handle(method:String,payload:JSONObject?):JSONObject }
interface SaronitePermissionPolicy { fun state(permission:String):SaronitePermissionState }
class SaroniteHost(private val transport:SaroniteTransport,private val permissions:SaronitePermissionPolicy) {
 private val handlers=ConcurrentHashMap<String,SaroniteCapabilityHandler>(); private var lifecycle=SaroniteLifecycle.INSTALLING
 fun register(capability:String,handler:SaroniteCapabilityHandler){handlers[capability]=handler}
 fun unregister(capability:String){handlers.remove(capability)}
 fun emitLifecycle(next:SaroniteLifecycle){lifecycle=next;transport.send(SaroniteEvent(event="lifecycle",lifecycle=next.name.lowercase()).toJson())}
 fun emitPermission(permission:String){transport.send(SaroniteEvent(event="permission",permission=SaronitePermissionEvent(permission,permissions.state(permission).name.lowercase())).toJson())}
 suspend fun dispatch(request:SaroniteRequest){
  if(request.capability.isBlank()||request.method.isBlank()){respond(request.id,SaroniteError("INVALID_REQUEST","capability and method are required"));return}
  val handler=handlers[request.capability]
  if(handler==null){respond(request.id,SaroniteError("UNKNOWN_CAPABILITY","Capability is not registered: ${request.capability}"));return}
  val permission=permissionFor(request.capability)
  if(permission!=null&&permissions.state(permission)!=SaronitePermissionState.GRANTED){respond(request.id,SaroniteError("PERMISSION_DENIED","Permission is not granted: $permission"));return}
  if(lifecycle==SaroniteLifecycle.TERMINATED||lifecycle==SaroniteLifecycle.FAILED){respond(request.id,SaroniteError("INVALID_STATE","Mini-app host is ${lifecycle.name.lowercase()}"));return}
  try{respond(request.id,result=handler.handle(request.method,request.payload))}
  catch(e:UnsupportedOperationException){respond(request.id,SaroniteError("UNSUPPORTED",e.message?:"Capability method is unsupported"))}
  catch(e:Exception){respond(request.id,SaroniteError("INTERNAL_ERROR",e.message?:"Capability execution failed"))}
 }
 fun close(){lifecycle=SaroniteLifecycle.TERMINATED;emitLifecycle(SaroniteLifecycle.TERMINATED);transport.close()}
 private fun respond(id:String,error:SaroniteError?=null,result:JSONObject?=null){transport.send(SaroniteResponse(id,error==null,result,error).toJson())}
 companion object { fun newRequestId()="srn_${UUID.randomUUID()}"; private fun permissionFor(capability:String):String?=when(capability){"identity"->"identity:read";"location"->"location:read";"camera"->"camera:capture";"contacts"->"contacts:read";"clipboard"->"clipboard:read";"notifications"->"notifications:schedule";"payments"->"payments:request";else->null} }
}
