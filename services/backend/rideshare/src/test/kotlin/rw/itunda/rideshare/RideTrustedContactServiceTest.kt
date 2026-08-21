package rw.itunda.rideshare

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import rw.itunda.core.domain.Conversation
import rw.itunda.core.domain.Message
import rw.itunda.core.domain.RideDriver
import rw.itunda.core.domain.RideTrip
import rw.itunda.core.domain.RideTripStatus
import rw.itunda.core.domain.RideTrustedContact
import rw.itunda.core.domain.User
import rw.itunda.core.repository.RideDriverRepository
import rw.itunda.core.repository.RideTripRepository
import rw.itunda.core.repository.RideTrustedContactRepository
import rw.itunda.core.repository.UserRepository
import rw.itunda.messaging.MessagingService
import java.math.BigDecimal
import java.util.Optional

/**
 * First test coverage for real Uber Safety "Trusted Contacts" -- see
 * RideTrustedContact.kt's own doc comment for the full sourced account.
 */
class RideTrustedContactServiceTest : BehaviorSpec({

    Given("a real itunda user") {
        val rideTrustedContactRepository = mockk<RideTrustedContactRepository>()
        val rideTripRepository = mockk<RideTripRepository>()
        val rideDriverRepository = mockk<RideDriverRepository>()
        val userRepository = mockk<UserRepository>()
        val messagingService = mockk<MessagingService>()
        val service = RideTrustedContactService(rideTrustedContactRepository, rideTripRepository, rideDriverRepository, userRepository, messagingService)

        val friendUser = User(id = "friend_1", phoneNumber = "+250788000002", firstName = "Alice", lastName = "M", passwordHash = "x")

        When("adding a real trusted contact by phone number") {
            every { userRepository.findByPhoneNumber("+250788000002") } returns friendUser
            every { rideTrustedContactRepository.existsByUserIdAndContactUserId("user_1", "friend_1") } returns false
            every { rideTrustedContactRepository.countByUserId("user_1") } returns 0L
            val savedSlot = slot<RideTrustedContact>()
            every { rideTrustedContactRepository.save(capture(savedSlot)) } answers { firstArg() }

            val contact = service.add("user_1", "+250788000002", "  Alice  ")

            Then("it resolves the real user by phone and trims the display name") {
                contact.contactUserId shouldBe "friend_1"
                contact.contactName shouldBe "Alice"
                savedSlot.captured.userId shouldBe "user_1"
            }
        }

        When("the phone number doesn't resolve to a real itunda account") {
            every { userRepository.findByPhoneNumber("+250788999999") } returns null

            Then("it throws RideTrustedContactRecipientNotFoundException") {
                try {
                    service.add("user_1", "+250788999999", "Nobody")
                    error("expected RideTrustedContactRecipientNotFoundException")
                } catch (e: RideTrustedContactRecipientNotFoundException) {
                    // expected
                }
            }
        }

        When("trying to add yourself as a trusted contact") {
            val selfUser = User(id = "user_1", phoneNumber = "+250788000001", firstName = "Self", lastName = "T", passwordHash = "x")
            every { userRepository.findByPhoneNumber("+250788000001") } returns selfUser

            Then("it throws RideTrustedContactSelfException") {
                try {
                    service.add("user_1", "+250788000001", "Me")
                    error("expected RideTrustedContactSelfException")
                } catch (e: RideTrustedContactSelfException) {
                    // expected
                }
            }
        }

        When("the same contact is already on the list") {
            every { userRepository.findByPhoneNumber("+250788000002") } returns friendUser
            every { rideTrustedContactRepository.existsByUserIdAndContactUserId("user_1", "friend_1") } returns true

            Then("it throws RideTrustedContactAlreadyAddedException") {
                try {
                    service.add("user_1", "+250788000002", "Alice")
                    error("expected RideTrustedContactAlreadyAddedException")
                } catch (e: RideTrustedContactAlreadyAddedException) {
                    // expected
                }
            }
        }

        When("the real Uber cap of 5 trusted contacts is already reached") {
            every { userRepository.findByPhoneNumber("+250788000002") } returns friendUser
            every { rideTrustedContactRepository.existsByUserIdAndContactUserId("user_1", "friend_1") } returns false
            every { rideTrustedContactRepository.countByUserId("user_1") } returns 5L

            Then("it throws RideTooManyTrustedContactsException") {
                try {
                    service.add("user_1", "+250788000002", "Alice")
                    error("expected RideTooManyTrustedContactsException")
                } catch (e: RideTooManyTrustedContactsException) {
                    // expected
                }
            }
        }

        When("removing a trusted contact that isn't theirs") {
            every { rideTrustedContactRepository.findByIdAndUserId("contact_1", "user_1") } returns null

            Then("it throws RideTrustedContactNotFoundException") {
                try {
                    service.remove("user_1", "contact_1")
                    error("expected RideTrustedContactNotFoundException")
                } catch (e: RideTrustedContactNotFoundException) {
                    // expected
                }
            }
        }
    }

    Given("a real active trip with 2 trusted contacts on file") {
        val rideTrustedContactRepository = mockk<RideTrustedContactRepository>()
        val rideTripRepository = mockk<RideTripRepository>()
        val rideDriverRepository = mockk<RideDriverRepository>()
        val userRepository = mockk<UserRepository>()
        val messagingService = mockk<MessagingService>()
        val service = RideTrustedContactService(rideTrustedContactRepository, rideTripRepository, rideDriverRepository, userRepository, messagingService)

        val trip = RideTrip(
            id = "ride_trip_1", passengerId = "user_1", driverId = "driver_1", pickupAddress = "A",
            pickupLatitude = -1.95, pickupLongitude = 30.06, dropoffAddress = "B", dropoffLatitude = -1.96, dropoffLongitude = 30.09,
            distanceKm = BigDecimal("3.5"), fare = BigDecimal("1875"), platformFee = BigDecimal("28.13"), transactionId = "txn_1",
            status = RideTripStatus.DRIVER_ASSIGNED,
        )
        val driver = RideDriver(id = "driver_1", userId = "driver_user_1", accountId = "account_driver_1", currentLatitude = -1.955, currentLongitude = 30.07)
        val contacts = listOf(
            RideTrustedContact(id = "rtc_1", userId = "user_1", contactUserId = "friend_1", contactName = "Alice"),
            RideTrustedContact(id = "rtc_2", userId = "user_1", contactUserId = "friend_2", contactName = "Bob"),
        )

        When("sending a real status update to every trusted contact") {
            every { rideTripRepository.findById("ride_trip_1") } returns Optional.of(trip)
            every { rideTrustedContactRepository.findByUserIdOrderByCreatedAtDesc("user_1") } returns contacts
            every { rideDriverRepository.findById("driver_1") } returns Optional.of(driver)
            every { messagingService.startOrGetConversation("user_1", "friend_1") } returns Conversation(id = "conv_1", participantAId = "user_1", participantBId = "friend_1")
            every { messagingService.startOrGetConversation("user_1", "friend_2") } returns Conversation(id = "conv_2", participantAId = "user_1", participantBId = "friend_2")
            every { messagingService.sendMessage(any(), any(), any()) } answers {
                Message(id = "msg_1", conversationId = secondArg(), senderId = firstArg(), body = thirdArg())
            }

            val sentCount = service.sendStatusToTrustedContacts("user_1", "ride_trip_1")

            Then("it fans the real trip status out to both contacts and returns the real count sent") {
                sentCount shouldBe 2
                verify(exactly = 1) { messagingService.sendMessage("user_1", "conv_1", match { it.contains("DRIVER_ASSIGNED") && it.contains(driver.currentLatitude.toString()) }) }
                verify(exactly = 1) { messagingService.sendMessage("user_1", "conv_2", any()) }
            }
        }

        When("a stranger (not the real passenger) tries to send status") {
            every { rideTripRepository.findById("ride_trip_1") } returns Optional.of(trip)

            Then("it throws RideTripNotFoundException, not a 403 that would confirm the trip exists") {
                try {
                    service.sendStatusToTrustedContacts("stranger", "ride_trip_1")
                    error("expected RideTripNotFoundException")
                } catch (e: RideTripNotFoundException) {
                    // expected
                }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
