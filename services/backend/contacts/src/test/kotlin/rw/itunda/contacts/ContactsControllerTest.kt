package rw.itunda.contacts

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.http.HttpStatus
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.Contact
import rw.itunda.core.repository.ContactRepository
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError

/**
 * First test coverage for :contacts. Directly instantiates the controller with a
 * mocked repository, no Spring context -- @AuthenticationPrincipal/@RequestBody are
 * just plain method parameters at the JVM level, so calling the method directly
 * exercises the same code Spring would invoke, without needing @WebMvcTest.
 *
 * getContacts's own doc comment names a real IDOR the Express version had (every
 * user's contacts returned to any authenticated caller, no per-user filtering) --
 * this file exists to make sure that fix can't silently regress.
 */
class ContactsControllerTest : BehaviorSpec({

    Given("an authenticated user") {
        val contactRepository = mockk<ContactRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val controller = ContactsController(contactRepository, rateLimiter)
        val currentUser = CurrentUser(userId = "user_1")

        When("listing contacts") {
            val theirContacts = listOf(
                Contact(id = "c_1", userId = "user_1", name = "Eric", bank = "MTN MoMo", acc = "+250788111222", phoneNumber = "+250788111222", color = "#F5FAFF", letter = "E"),
            )
            every { contactRepository.findByUserId("user_1") } returns theirContacts

            Then("it only queries by the caller's own userId, never anyone else's") {
                val response = controller.getContacts(currentUser)
                response.statusCode shouldBe HttpStatus.OK
                @Suppress("UNCHECKED_CAST")
                val body = response.body as Map<String, Any>
                body["contacts"] shouldBe theirContacts
                verify(exactly = 1) { contactRepository.findByUserId("user_1") }
            }
        }

        When("adding a contact with a name and phone number") {
            val saved = slot<Contact>()
            every { contactRepository.save(capture(saved)) } answers { saved.captured }

            Then("it saves the contact tagged with the caller's userId, not a client-supplied one") {
                val response = controller.addContact(
                    AddContactRequest(name = "Uwase", bank = "Bank of Kigali", phoneNumber = "+250788333444"),
                    currentUser,
                )
                response.statusCode shouldBe HttpStatus.CREATED
                saved.captured.userId shouldBe "user_1"
                saved.captured.name shouldBe "Uwase"
                saved.captured.bank shouldBe "Bank of Kigali"
                saved.captured.letter shouldBe "U"
            }
        }

        When("adding a contact with a blank name") {
            Then("it rejects the request before ever touching the repository") {
                val response = controller.addContact(AddContactRequest(name = "", phoneNumber = "+250788333444"), currentUser)
                response.statusCode shouldBe HttpStatus.BAD_REQUEST
                (response.body as ApiError).code shouldBe "INVALID_REQUEST"
                verify(exactly = 0) { contactRepository.save(any()) }
            }
        }

        When("adding a contact with no explicit bank") {
            val saved = slot<Contact>()
            every { contactRepository.save(capture(saved)) } answers { saved.captured }

            Then("it defaults to MTN MoMo, matching the field's fallback in the controller") {
                controller.addContact(AddContactRequest(name = "Jean", bank = null, phoneNumber = "+250788555666"), currentUser)
                saved.captured.bank shouldBe "MTN MoMo"
            }
        }

        // Real bug found live (2026-08-02): addContact had shipped with zero rate
        // limiting, unlike every other real content-creation endpoint in this codebase.
        When("adding a contact within the normal rate") {
            val saved = slot<Contact>()
            every { contactRepository.save(capture(saved)) } answers { saved.captured }

            Then("it checks the real per-user rate limit before writing") {
                controller.addContact(AddContactRequest(name = "Eric", phoneNumber = "+250788111222"), currentUser)
                verify(exactly = 1) { rateLimiter.checkLimit("contacts:add:user_1", limit = 60, window = any()) }
            }
        }

        When("the caller has exceeded the real rate limit") {
            every { rateLimiter.checkLimit("contacts:add:user_1", limit = 60, window = any()) } throws RateLimitExceededException("Too many attempts, please try again later")

            Then("the add is rejected before ever touching the repository") {
                try {
                    controller.addContact(AddContactRequest(name = "Eric", phoneNumber = "+250788111222"), currentUser)
                    throw AssertionError("expected RateLimitExceededException")
                } catch (e: RateLimitExceededException) {
                    // expected
                }
                verify(exactly = 0) { contactRepository.save(any()) }
            }
        }
    }
}) {
    // Same reasoning as LedgerServiceTest.kt/MerchantServiceTest.kt: fresh fixtures per
    // leaf test so a captured slot in one When can't leak into a sibling test.
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
