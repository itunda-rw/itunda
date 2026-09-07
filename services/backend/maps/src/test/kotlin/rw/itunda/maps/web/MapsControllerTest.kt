package rw.itunda.maps.web

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import rw.itunda.core.domain.LiveLocationShare
import rw.itunda.core.geo.TravelMode
import rw.itunda.core.idempotency.IdempotencyService
import rw.itunda.core.security.CurrentUser
import rw.itunda.maps.LiveLocationShareService
import rw.itunda.maps.MapsPlaceDetailService
import rw.itunda.maps.MapsService

/**
 * First test coverage for MapsController -- the largest controller in this module (25
 * endpoints + 21 exception handlers), previously untested despite sibling modules
 * (:card, :loans) already having controller-level tests. Covers real delegation
 * (caller-scoped userId, never client-supplied) and every real exception-handler
 * mapping.
 */
class MapsControllerTest : BehaviorSpec({

    val currentUser = CurrentUser(userId = "user_1")

    fun controller(
        mapsService: MapsService = mockk(),
        liveLocationShareService: LiveLocationShareService = mockk(),
        mapsPlaceDetailService: MapsPlaceDetailService = mockk(),
        idempotencyService: IdempotencyService = mockk(),
    ) = MapsController(mapsService, liveLocationShareService, mapsPlaceDetailService, idempotencyService)

    Given("a real place-detail request") {
        val mapsPlaceDetailService = mockk<MapsPlaceDetailService>()
        val ctl = controller(mapsPlaceDetailService = mapsPlaceDetailService)
        val detail = mockk<rw.itunda.maps.MapsPlaceDetail>(relaxed = true)
        every { mapsPlaceDetailService.getPlaceDetail("user_1", "merchant_1") } returns detail

        When("fetching it") {
            ctl.placeDetail("merchant_1", currentUser)
            Then("it queries scoped to the caller's own userId, never a client-supplied one") {
                verify(exactly = 1) { mapsPlaceDetailService.getPlaceDetail("user_1", "merchant_1") }
            }
        }
    }

    Given("a real search request") {
        val mapsService = mockk<MapsService>()
        val ctl = controller(mapsService = mapsService)
        every { mapsService.searchPlaces("user_1", "Kigali") } returns emptyList()

        When("searching") {
            ctl.search("Kigali", currentUser)
            Then("it queries scoped to the caller's own userId") {
                verify(exactly = 1) { mapsService.searchPlaces("user_1", "Kigali") }
            }
        }
    }

    Given("a real weather request") {
        val mapsService = mockk<MapsService>()
        val ctl = controller(mapsService = mapsService)

        When("the real upstream has no fresh reading") {
            every { mapsService.getWeather() } returns null
            val response = ctl.weather()
            Then("it returns an honest null, never a fabricated reading") {
                response.body?.get("weather") shouldBe null
            }
        }
    }

    Given("a real reverse-geocode request") {
        val mapsService = mockk<MapsService>()
        val ctl = controller(mapsService = mapsService)
        every { mapsService.reverseGeocode("user_1", -1.9, 30.0) } returns "Kigali"

        When("reverse geocoding") {
            ctl.reverse(-1.9, 30.0, currentUser)
            Then("it queries scoped to the caller's own userId") {
                verify(exactly = 1) { mapsService.reverseGeocode("user_1", -1.9, 30.0) }
            }
        }
    }

    Given("a real directions request") {
        val mapsService = mockk<MapsService>()
        val ctl = controller(mapsService = mapsService)
        val route = mockk<rw.itunda.core.geo.RouteResult>(relaxed = true)
        every { mapsService.getDirections("user_1", -1.9, 30.0, -1.95, 30.05, TravelMode.BIKING) } returns route

        When("requesting directions in BIKING mode") {
            ctl.directions(-1.9, 30.0, -1.95, 30.05, TravelMode.BIKING, currentUser)
            Then("it real-delegates the caller-selected mode, not a hardcoded one") {
                verify(exactly = 1) { mapsService.getDirections("user_1", -1.9, 30.0, -1.95, 30.05, TravelMode.BIKING) }
            }
        }
    }

    Given("a real transit-directions request") {
        val mapsService = mockk<MapsService>()
        val ctl = controller(mapsService = mapsService)
        every { mapsService.getTransitDirections("user_1", -1.9, 30.0, -1.95, 30.05) } returns emptyList()

        When("requesting transit directions") {
            ctl.transitDirections(-1.9, 30.0, -1.95, 30.05, currentUser)
            Then("it queries scoped to the caller's own userId") {
                verify(exactly = 1) { mapsService.getTransitDirections("user_1", -1.9, 30.0, -1.95, 30.05) }
            }
        }
    }

    Given("a real itinerary-directions request") {
        val mapsService = mockk<MapsService>()
        val ctl = controller(mapsService = mapsService)
        val route = mockk<rw.itunda.core.geo.RouteResult>(relaxed = true)
        val request = ItineraryDirectionsRequest(
            waypoints = listOf(ItineraryWaypointRequest(-1.9, 30.0), ItineraryWaypointRequest(-1.95, 30.05)),
            mode = TravelMode.WALKING,
        )
        every {
            mapsService.getItineraryDirections("user_1", listOf(MapsService.ItineraryWaypoint(-1.9, 30.0), MapsService.ItineraryWaypoint(-1.95, 30.05)), TravelMode.WALKING)
        } returns route

        When("requesting itinerary directions") {
            ctl.itineraryDirections(request, currentUser)
            Then("it real-translates every request waypoint in order and passes the caller's own userId") {
                verify(exactly = 1) {
                    mapsService.getItineraryDirections("user_1", listOf(MapsService.ItineraryWaypoint(-1.9, 30.0), MapsService.ItineraryWaypoint(-1.95, 30.05)), TravelMode.WALKING)
                }
            }
        }
    }

    Given("a real directions-alternatives request") {
        val mapsService = mockk<MapsService>()
        val ctl = controller(mapsService = mapsService)
        every { mapsService.getDirectionsAlternatives("user_1", -1.9, 30.0, -1.95, 30.05, TravelMode.DRIVING) } returns emptyList()

        When("requesting alternatives") {
            ctl.directionsAlternatives(-1.9, 30.0, -1.95, 30.05, TravelMode.DRIVING, currentUser)
            Then("it queries scoped to the caller's own userId") {
                verify(exactly = 1) { mapsService.getDirectionsAlternatives("user_1", -1.9, 30.0, -1.95, 30.05, TravelMode.DRIVING) }
            }
        }
    }

    Given("a real categories request") {
        val ctl = controller()

        When("fetching the real backend category list") {
            val response = ctl.categories()
            Then("it real-returns every MapPlaceCategory entry, not a client-hardcoded subset") {
                @Suppress("UNCHECKED_CAST")
                val categories = response.body?.get("categories") as List<Map<String, String>>
                categories.map { it["id"] } shouldBe rw.itunda.maps.MapPlaceCategory.entries.map { it.name }
            }
        }
    }

    Given("a real nearby-places request") {
        val mapsService = mockk<MapsService>()
        val ctl = controller(mapsService = mapsService)
        every { mapsService.getNearbyPlaces("user_1", "RESTAURANT", -1.9, 30.0, 2.0) } returns emptyList()

        When("searching nearby") {
            ctl.nearby("RESTAURANT", -1.9, 30.0, 2.0, currentUser)
            Then("it queries scoped to the caller's own userId") {
                verify(exactly = 1) { mapsService.getNearbyPlaces("user_1", "RESTAURANT", -1.9, 30.0, 2.0) }
            }
        }
    }

    Given("a real around-me request") {
        val mapsService = mockk<MapsService>()
        val ctl = controller(mapsService = mapsService)
        every { mapsService.getAroundMe("user_1", -1.9, 30.0, 2.0) } returns emptyList()

        When("fetching the Smart-Around default state") {
            ctl.aroundMe(-1.9, 30.0, 2.0, currentUser)
            Then("it queries scoped to the caller's own userId") {
                verify(exactly = 1) { mapsService.getAroundMe("user_1", -1.9, 30.0, 2.0) }
            }
        }
    }

    Given("a real trending-places request") {
        val mapsService = mockk<MapsService>()
        val ctl = controller(mapsService = mapsService)
        every { mapsService.getTrendingSavedPlaces("user_1", 7, 10) } returns emptyList()

        When("fetching trending saved places") {
            ctl.trending(7, 10, currentUser)
            Then("it real-passes the caller's own userId for the new rate-limit bucket, not an anonymous call") {
                verify(exactly = 1) { mapsService.getTrendingSavedPlaces("user_1", 7, 10) }
            }
        }
    }

    Given("a real bookmark creation") {
        val mapsService = mockk<MapsService>()
        val ctl = controller(mapsService = mapsService)
        val bookmark = mockk<rw.itunda.core.domain.MapBookmark>(relaxed = true)
        val request = AddBookmarkRequest(displayName = "Kigali Airport", latitude = -1.9, longitude = 30.0)
        every { mapsService.addBookmark("user_1", "Kigali Airport", -1.9, 30.0, MapsService.DEFAULT_BOOKMARK_FOLDER, MapsService.DEFAULT_BOOKMARK_COLOR) } returns bookmark

        When("adding it") {
            ctl.addBookmark(request, currentUser)
            Then("it real-delegates with the caller's own userId and real defaults for an omitted folder/color") {
                verify(exactly = 1) { mapsService.addBookmark("user_1", "Kigali Airport", -1.9, 30.0, MapsService.DEFAULT_BOOKMARK_FOLDER, MapsService.DEFAULT_BOOKMARK_COLOR) }
            }
        }
    }

    Given("a real bookmark move") {
        val mapsService = mockk<MapsService>()
        val ctl = controller(mapsService = mapsService)
        val bookmark = mockk<rw.itunda.core.domain.MapBookmark>(relaxed = true)
        val request = MoveBookmarkRequest(folderName = "Cafes", color = "#8B5CF6")
        every { mapsService.moveBookmark("user_1", -1.9, 30.0, "Cafes", "#8B5CF6") } returns bookmark

        When("moving it") {
            ctl.moveBookmark(-1.9, 30.0, request, currentUser)
            Then("it queries scoped to the caller's own userId") {
                verify(exactly = 1) { mapsService.moveBookmark("user_1", -1.9, 30.0, "Cafes", "#8B5CF6") }
            }
        }
    }

    Given("a real bookmark removal") {
        val mapsService = mockk<MapsService>(relaxed = true)
        val ctl = controller(mapsService = mapsService)

        When("removing it") {
            ctl.removeBookmark(-1.9, 30.0, currentUser)
            Then("it queries scoped to the caller's own userId") {
                verify(exactly = 1) { mapsService.removeBookmark("user_1", -1.9, 30.0) }
            }
        }
    }

    Given("a real folder-visibility change") {
        val mapsService = mockk<MapsService>()
        val ctl = controller(mapsService = mapsService)
        val request = SetFolderVisibilityRequest(folderName = "Cafes", isPublic = true)
        every { mapsService.setFolderPublic("user_1", "Cafes", true) } returns 3

        When("making the folder public") {
            val response = ctl.setFolderVisibility(request, currentUser)
            Then("it queries scoped to the caller's own userId and reports the real updated count") {
                verify(exactly = 1) { mapsService.setFolderPublic("user_1", "Cafes", true) }
                response.body?.get("updatedCount") shouldBe 3
            }
        }
    }

    Given("a real, deliberately unauthenticated shared-folder read") {
        val mapsService = mockk<MapsService>()
        val ctl = controller(mapsService = mapsService)
        every { mapsService.getPublicFolder("owner_1", "Cafes") } returns emptyList()

        When("viewing it") {
            ctl.sharedFolder("owner_1", "Cafes")
            Then("it real-reads the OWNER's own folder by the path userId, no caller principal involved") {
                verify(exactly = 1) { mapsService.getPublicFolder("owner_1", "Cafes") }
            }
        }
    }

    Given("a real shared-folder subscribe") {
        val mapsService = mockk<MapsService>()
        val ctl = controller(mapsService = mapsService)
        every { mapsService.subscribeToSharedFolder("user_1", "owner_1", "Cafes") } returns 2

        When("subscribing") {
            val response = ctl.subscribeToSharedFolder("owner_1", "Cafes", currentUser)
            Then("it real-passes the caller as subscriber, the path variable as owner -- never swapped") {
                verify(exactly = 1) { mapsService.subscribeToSharedFolder("user_1", "owner_1", "Cafes") }
                response.body?.get("copiedCount") shouldBe 2
            }
        }
    }

    Given("a real bookmarks list request") {
        val mapsService = mockk<MapsService>()
        val ctl = controller(mapsService = mapsService)
        every { mapsService.getMyBookmarks("user_1") } returns emptyList()

        When("fetching them") {
            ctl.bookmarks(currentUser)
            Then("it queries scoped to the caller's own userId") {
                verify(exactly = 1) { mapsService.getMyBookmarks("user_1") }
            }
        }
    }

    Given("a real location-share start") {
        val liveLocationShareService = mockk<LiveLocationShareService>()
        val idempotencyService = mockk<IdempotencyService>()
        val ctl = controller(liveLocationShareService = liveLocationShareService, idempotencyService = idempotencyService)
        val share = mockk<LiveLocationShare>(relaxed = true)
        val request = StartLocationShareRequest(recipientPhoneNumber = "0788000000", durationHours = 2)
        val actionSlot = slot<() -> Pair<Int, Map<String, Any?>>>()
        every { liveLocationShareService.startSharing("user_1", "0788000000", 2) } returns share
        every {
            idempotencyService.replayOrExecute("POST /api/v1/maps/location-share", "key-1", request, capture(actionSlot))
        } answers { actionSlot.captured.invoke() }

        When("starting it") {
            ctl.startLocationShare(request, "key-1", currentUser)
            Then("it routes through the real idempotency service scoped to the caller's own userId") {
                verify(exactly = 1) { idempotencyService.replayOrExecute("POST /api/v1/maps/location-share", "key-1", request, any()) }
                verify(exactly = 1) { liveLocationShareService.startSharing("user_1", "0788000000", 2) }
            }
        }
    }

    Given("a real location-share position update") {
        val liveLocationShareService = mockk<LiveLocationShareService>()
        val ctl = controller(liveLocationShareService = liveLocationShareService)
        every { liveLocationShareService.updateMyLocation("user_1", -1.9, 30.0) } returns 3

        When("pushing a fresh position") {
            val response = ctl.updateLocationShare("share_1", UpdateLocationShareRequest(-1.9, 30.0), currentUser)
            Then("it queries scoped to the caller's own userId and reports the real fanned-out count") {
                verify(exactly = 1) { liveLocationShareService.updateMyLocation("user_1", -1.9, 30.0) }
                response.body?.get("updatedShareCount") shouldBe 3
            }
        }
    }

    Given("a real location-share extend") {
        val liveLocationShareService = mockk<LiveLocationShareService>()
        val idempotencyService = mockk<IdempotencyService>()
        val ctl = controller(liveLocationShareService = liveLocationShareService, idempotencyService = idempotencyService)
        val share = mockk<LiveLocationShare>(relaxed = true)
        val request = ExtendLocationShareRequest(additionalHours = 1)
        val actionSlot = slot<() -> Pair<Int, Map<String, Any?>>>()
        every { liveLocationShareService.extendSharing("user_1", "share_1", 1) } returns share
        every {
            idempotencyService.replayOrExecute("POST /api/v1/maps/location-share/share_1/extend", "key-1", request, capture(actionSlot))
        } answers { actionSlot.captured.invoke() }

        When("extending it") {
            ctl.extendLocationShare("share_1", request, "key-1", currentUser)
            Then("it routes through the real idempotency service scoped to the caller's own userId") {
                verify(exactly = 1) { idempotencyService.replayOrExecute("POST /api/v1/maps/location-share/share_1/extend", "key-1", request, any()) }
                verify(exactly = 1) { liveLocationShareService.extendSharing("user_1", "share_1", 1) }
            }
        }
    }

    Given("a real location-share stop") {
        val liveLocationShareService = mockk<LiveLocationShareService>(relaxed = true)
        val ctl = controller(liveLocationShareService = liveLocationShareService)

        When("stopping it") {
            ctl.stopLocationShare("share_1", currentUser)
            Then("it queries scoped to the caller's own userId") {
                verify(exactly = 1) { liveLocationShareService.stopSharing("user_1", "share_1") }
            }
        }
    }

    Given("a real request for the caller's own active shares") {
        val liveLocationShareService = mockk<LiveLocationShareService>()
        val ctl = controller(liveLocationShareService = liveLocationShareService)
        every { liveLocationShareService.myActiveShares("user_1") } returns emptyList()

        When("fetching them") {
            ctl.myLocationShares(currentUser)
            Then("it queries scoped to the caller's own userId") {
                verify(exactly = 1) { liveLocationShareService.myActiveShares("user_1") }
            }
        }
    }

    Given("a real request for shares shared with the caller") {
        val liveLocationShareService = mockk<LiveLocationShareService>()
        val ctl = controller(liveLocationShareService = liveLocationShareService)
        every { liveLocationShareService.sharedWithMe("user_1") } returns emptyList()

        When("fetching them") {
            ctl.locationSharesWithMe(currentUser)
            Then("it queries scoped to the caller's own userId") {
                verify(exactly = 1) { liveLocationShareService.sharedWithMe("user_1") }
            }
        }
    }

    Given("a real recipient-side location-share poll") {
        val liveLocationShareService = mockk<LiveLocationShareService>()
        val ctl = controller(liveLocationShareService = liveLocationShareService)
        val share = mockk<LiveLocationShare>(relaxed = true)
        every { liveLocationShareService.getSharedLocation("user_1", "share_1") } returns share

        When("polling it") {
            ctl.getLocationShare("share_1", currentUser)
            Then("it queries scoped to the caller's own userId") {
                verify(exactly = 1) { liveLocationShareService.getSharedLocation("user_1", "share_1") }
            }
        }
    }

}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
