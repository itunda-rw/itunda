package rw.itunda.marketplace.web

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import rw.itunda.core.web.ApiError
import rw.itunda.marketplace.MechanicNotFoundException
import rw.itunda.marketplace.VehicleInspectionService

// Mapped under api/v1/system/vehicle-inspection-mechanics specifically so it
// inherits SecurityConfig's existing hasRole("ADMIN") rule on the system path
// prefix -- same real RBAC gate MerchantModerationAdminController already uses, no
// new attack surface. See VehicleInspectionMechanic.kt's own doc comment for why
// this exists: mechanics are real money-receiving business actors (same shape as
// Merchant) that previously had no admin lever at all to take one out of the
// public booking list.
@RestController
@RequestMapping("/api/v1/system/vehicle-inspection-mechanics")
class VehicleInspectionMechanicModerationAdminController(
    private val vehicleInspectionService: VehicleInspectionService,
) {

    @GetMapping
    fun list(): ResponseEntity<Map<String, Any?>> {
        val mechanics = vehicleInspectionService.getAllMechanics().map {
            mapOf("mechanicId" to it.id, "businessName" to it.businessName, "available" to it.available, "suspended" to it.suspended, "createdAt" to it.createdAt.toString())
        }
        return ResponseEntity.ok(mapOf("success" to true, "mechanics" to mechanics))
    }

    @PostMapping("/{mechanicId}/suspend")
    fun suspend(@PathVariable mechanicId: String): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "mechanic" to vehicleInspectionService.suspendMechanic(mechanicId)))

    @PostMapping("/{mechanicId}/reactivate")
    fun reactivate(@PathVariable mechanicId: String): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "mechanic" to vehicleInspectionService.reactivateMechanic(mechanicId)))

    @ExceptionHandler(MechanicNotFoundException::class)
    fun handleNotFound(ex: MechanicNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("MECHANIC_NOT_FOUND", ex.message ?: "Not found"))
}
