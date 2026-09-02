package rw.itunda.core.network

/** Shared identifier for "which savings bucket did the user tap" (Interest Jar vs. a
 * specific Savings Goal) -- carried through rememberSaveable state, so Serializable
 * (not just a plain sealed interface) matters here.
 *
 * Promoted here from :app's ItundaAppScreen.kt (2026-09-02, Banking Feature-module
 * decomposition slice 4) so both :app (ItundaAppScreen's own bucketDetailTarget state +
 * BucketDetailScreen navigation) and :features:banking:impl's BankHubScreen (whose
 * onOpenBucketDetail callback takes this type) can reference the same real type,
 * matching the MoneyActionResult precedent. */
sealed class BucketDetailTarget : java.io.Serializable {
    data object InterestJar : BucketDetailTarget()
    data class Goal(val id: String, val name: String, val currentAmount: Double, val targetAmount: Double) : BucketDetailTarget()
}
