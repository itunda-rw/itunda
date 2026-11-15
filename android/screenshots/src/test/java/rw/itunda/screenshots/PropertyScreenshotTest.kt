package rw.itunda.screenshots

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
}
