package rw.itunda.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import rw.itunda.core.designsystem.theme.IdsTheme
import rw.itunda.core.network.MapPlaceCategory
import rw.itunda.core.network.PlaceSearchResultDto
import rw.itunda.feature.maps.impl.MapSearchScreen

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h788dp-xxhdpi")
class MapSearchScreenshotTest {
    @get:Rule val rule = createComposeRule()

    private val categories = listOf(
        MapPlaceCategory("CAFE", "Cafes"),
        MapPlaceCategory("RESTAURANT", "Restaurants"),
        MapPlaceCategory("BUS_STOP", "Bus stops"),
        MapPlaceCategory("PHARMACY", "Pharmacies"),
        MapPlaceCategory("ATM", "ATMs"),
    )

    private val recents = listOf(
        PlaceSearchResultDto("Kigali Convention Centre, KG 2 Roundabout, Kigali", -1.9536, 30.0928),
        PlaceSearchResultDto("Kimironko Market, Kigali", -1.9441, 30.1250),
        PlaceSearchResultDto("Kigali City Tower, KN 2 St, Kigali", -1.9490, 30.0606),
    )

    private fun shot(name: String, dark: Boolean, query: String = "", results: List<PlaceSearchResultDto>? = null) {
        rule.setContent {
            IdsTheme(darkTheme = dark) {
                MapSearchScreen(
                    query = query,
                    searching = false,
                    searchResults = results,
                    error = null,
                    recentSearches = recents,
                    categories = categories,
                    onBack = {},
                    onQueryChange = {},
                    onRunSearch = {},
                    onSelectPlace = {},
                    onRemoveRecent = {},
                    onClearRecentSearches = {},
                    onQuickSearch = {},
                    onCategoryClick = {},
                )
            }
        }
        rule.onRoot().captureRoboImage("build/screenshots/$name.png")
    }

    @Test fun mapSearchLight() = shot("maps-search-light", false)
    @Test fun mapSearchDark() = shot("maps-search-dark", true)
    @Test fun mapSearchResultsLight() = shot(
        "maps-search-results-light",
        false,
        "Kimironko",
        listOf(PlaceSearchResultDto("Kimironko Market, Kigali", -1.9441, 30.1250)),
    )
    @Test fun mapSearchResultsDark() = shot(
        "maps-search-results-dark",
        true,
        "Kimironko",
        listOf(PlaceSearchResultDto("Kimironko Market, Kigali", -1.9441, 30.1250)),
    )
}
