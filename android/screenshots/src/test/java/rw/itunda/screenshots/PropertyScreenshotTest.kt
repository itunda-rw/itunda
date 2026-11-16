package rw.itunda.screenshots

import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import rw.itunda.core.designsystem.theme.IdsTheme
import rw.itunda.core.network.PropertyListingDto
import rw.itunda.core.network.JobPostDto
import rw.itunda.core.network.CommunityPostDto
import rw.itunda.feature.community.impl.CommunityPostCard
import rw.itunda.feature.jobs.impl.JobPostListRow
import rw.itunda.feature.property.impl.PropertyListRow

/** Property list rows with the prototype's sample listings. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-xxhdpi")
class PropertyScreenshotTest {
    @get:Rule val rule = createComposeRule()

    private fun l(id: String, type: String, title: String, price: Double, hood: String, beds: Int?, sqm: Double) = PropertyListingDto(
        id = id, listerId = "x", listingType = type, propertyType = "apartment", title = title, description = "",
        price = price, bedrooms = beds, sizeSqm = sqm, status = "AVAILABLE", createdAt = "2026-11-14T08:00:00Z", neighborhood = hood,
    )

    @Test fun rows() {
        rule.setContent {
            IdsTheme(darkTheme = false) {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    PropertyListRow(l("1", "RENT", "Studio, owner direct", 120000.0, "Kimironko", 1, 30.0), "Apartment", "Today") {}
                    PropertyListRow(l("2", "RENT", "2-bedroom apartment", 250000.0, "Kimironko", 2, 70.0), "Apartment", "2 d ago") {}
                    PropertyListRow(l("3", "SALE", "Family house with yard", 48000000.0, "Remera", 3, 180.0), "House", "5 d ago") {}
                }
            }
        }
        rule.onRoot().captureRoboImage("build/screenshots/property-rows.png")
    }

    @Test fun jobRows() {
        fun j(id: String, t: String, cat: String, pay: Double, type: String) = JobPostDto(
            id = id, posterId = "p", category = cat, title = t, description = "", payType = type, payAmount = pay,
            status = "OPEN", createdAt = "2026-11-14T08:00:00Z", neighborhood = "Kimironko",
        )
        rule.setContent {
            IdsTheme(darkTheme = false) {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    JobPostListRow(j("1", "Barista helper", "Food", 5000.0, "DAILY"), "Food", "1 h ago") {}
                    JobPostListRow(j("2", "Shop assistant", "Shop", 4000.0, "DAILY"), "Shop", "2 h ago") {}
                    JobPostListRow(j("3", "Weekend tutor, maths", "Tutoring", 6000.0, "TASK"), "Tutoring", "Yesterday") {}
                }
            }
        }
        rule.onRoot().captureRoboImage("build/screenshots/jobs-rows.png")
    }

    @Test fun communityRows() {
        fun c(id: String, cat: String, t: String, b: String, likes: Long, comments: Long) = CommunityPostDto(
            id = id, authorId = "a", category = cat, title = t, body = b, status = "ACTIVE",
            likeCount = likes, commentCount = comments, createdAt = "2026-11-14T08:00:00Z", neighborhood = "Remera",
        )
        rule.setContent {
            IdsTheme(darkTheme = false) {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    CommunityPostCard(c("1", "general", "Umuganda this Saturday", "Umuganda this Saturday at 8. Bring your own gloves if you have them.", 22, 1), "Announcement", false, {}, {})
                    CommunityPostCard(c("2", "general", "Reliable tailor?", "Where can I find a reliable tailor near Kimironko market? I need a school uniform fixed by Monday.", 4, 2), "Ask", false, {}, {})
                }
            }
        }
        rule.onRoot().captureRoboImage("build/screenshots/community-rows.png")
    }

    @Test fun communityDetail() {
        rule.setContent {
            IdsTheme(darkTheme = false) {
                Column(Modifier.fillMaxSize().background(Ids.colors.background).padding(20.dp)) {
                    rw.itunda.feature.community.impl.CommunityPostHeader("Claire U.", "Remera · 1 h", "Ask")
                    Text("Does anyone know a good plumber in Remera? Our tap has been leaking all week.", Modifier.padding(top = 10.dp))
                    Text("Replies · 2", Modifier.padding(top = 24.dp))
                    rw.itunda.feature.community.impl.CommunityReplyRow("Jean P.", "Try the plumber near Remera market, he came the same day for me.")
                    rw.itunda.feature.community.impl.CommunityReplyRow("Sandrine K.", "I can send you his number.")
                }
            }
        }
        rule.onRoot().captureRoboImage("build/screenshots/community-detail.png")
    }
}
