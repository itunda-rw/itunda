package rw.itunda.core.network

// Real BuildConfig-avoidance (2026-07-23), same technique as NetworkClient.init(context,
// baseUrl): a Gradle library module can't read :app's generated BuildConfig directly, and
// passing the values in at runtime avoids relocating the buildConfigField/-PtilesBaseUrl=
// override plumbing (see app/build.gradle.kts's own apiBaseUrl comment). Lets RouteMiniMap
// (core/designsystem) and, eventually, MapScreen itself read the same real tiles/glyphs
// base URLs :app's BuildConfig.TILES_BASE_URL/GLYPHS_BASE_URL already provide, without
// either of them depending on :app.
object MapConfig {
    var tilesBaseUrl: String = ""
        private set
    var glyphsBaseUrl: String = ""
        private set

    fun init(tilesBaseUrl: String, glyphsBaseUrl: String) {
        this.tilesBaseUrl = tilesBaseUrl
        this.glyphsBaseUrl = glyphsBaseUrl
    }
}
