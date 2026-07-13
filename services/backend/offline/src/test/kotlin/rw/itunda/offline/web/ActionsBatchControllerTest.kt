package rw.itunda.offline.web

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import rw.itunda.core.batch.BatchActionHandler
import rw.itunda.core.security.CurrentUser

private class FakeHandler(override val actionType: String, val result: Pair<Int, Map<String, Any?>>) : BatchActionHandler {
    var lastUserId: String? = null
    var lastIdempotencyKey: String? = null
    var lastBody: Map<String, Any?>? = null

    override fun handle(userId: String, idempotencyKey: String, body: Map<String, Any?>): Pair<Int, Map<String, Any?>> {
        lastUserId = userId
        lastIdempotencyKey = idempotencyKey
        lastBody = body
        return result
    }
}

class ActionsBatchControllerTest : BehaviorSpec({

    Given("two registered handlers for two different action types") {
        val billHandler = FakeHandler("BILL_PAY", 200 to mapOf("success" to true))
        val savingsHandler = FakeHandler("SAVINGS_DEPOSIT", 200 to mapOf("success" to true))
        val controller = ActionsBatchController(listOf(billHandler, savingsHandler))
        val user = CurrentUser(userId = "user_1", role = "USER")

        When("a batch with one action of each type is submitted") {
            val response = controller.batch(
                BatchRequest(
                    actions = listOf(
                        BatchActionRequest("client_1", "BILL_PAY", "key_1", mapOf("billId" to "bill_2")),
                        BatchActionRequest("client_2", "SAVINGS_DEPOSIT", "key_2", mapOf("goalId" to "goal_1")),
                    ),
                ),
                user,
            )

            Then("each action is routed to its own handler with the real user id and its own idempotency key") {
                billHandler.lastUserId shouldBe "user_1"
                billHandler.lastIdempotencyKey shouldBe "key_1"
                billHandler.lastBody shouldBe mapOf("billId" to "bill_2")
                savingsHandler.lastIdempotencyKey shouldBe "key_2"
            }

            Then("the response carries one result per action, in order") {
                @Suppress("UNCHECKED_CAST")
                val results = response.body!!["results"] as List<Map<String, Any?>>
                results.size shouldBe 2
                results[0]["clientActionId"] shouldBe "client_1"
                results[1]["clientActionId"] shouldBe "client_2"
            }
        }
    }

    Given("a batch action of a type nothing handles") {
        val controller = ActionsBatchController(emptyList())
        val user = CurrentUser(userId = "user_1", role = "USER")

        When("it's submitted") {
            val response = controller.batch(
                BatchRequest(actions = listOf(BatchActionRequest("client_1", "SOMETHING_UNKNOWN", "key_1"))),
                user,
            )

            Then("that action alone real-400s with UNKNOWN_ACTION_TYPE, no exception thrown") {
                @Suppress("UNCHECKED_CAST")
                val results = response.body!!["results"] as List<Map<String, Any?>>
                results[0]["status"] shouldBe 400
                @Suppress("UNCHECKED_CAST")
                val body = results[0]["body"] as Map<String, Any?>
                (body["error"] as Map<*, *>)["code"] shouldBe "UNKNOWN_ACTION_TYPE"
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
