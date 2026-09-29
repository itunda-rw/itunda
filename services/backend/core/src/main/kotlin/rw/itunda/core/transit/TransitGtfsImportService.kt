package rw.itunda.core.transit

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.RestClient
import rw.itunda.core.domain.TransitRoute
import rw.itunda.core.domain.TransitScheduledTrip
import rw.itunda.core.domain.TransitStop
import rw.itunda.core.domain.TransitStopTime
import rw.itunda.core.repository.TransitRouteRepository
import rw.itunda.core.repository.TransitScheduledTripRepository
import rw.itunda.core.repository.TransitStopRepository
import rw.itunda.core.repository.TransitStopTimeRepository
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.zip.ZipInputStream

class TransitGtfsFetchFailedException(message: String) : RuntimeException(message)

data class TransitGtfsImportResult(val stops: Int, val routes: Int, val trips: Int, val stopTimes: Int)

/**
 * A real GTFS-zip importer for Kigali's real public-transit schedule (itunda Maps
 * redesign, 2026-08-28) -- see TransitStop.kt's own doc comment for the full account
 * of why this exists and what it feeds. Parses the real, standard GTFS text format
 * (`stops.txt`/`routes.txt`/`trips.txt`/`stop_times.txt`, comma-separated with real
 * RFC 4180 quoting -- some real stop names in this exact feed contain a comma, e.g.
 * "Kigali, Nyarugenge", so a naive `split(",")` would silently corrupt real rows) --
 * never hand-typed sample data. `baseUrl` defaults to the real, validated Kigali GTFS
 * dataset published by the Digital Transport for Africa initiative (TUMI Datahub) --
 * see docs/TOSS_PARITY_MATRIX.md's Maps row for the sourcing.
 *
 * Idempotent by design: `importFromUrl` clears and fully replaces every existing row
 * (`deleteAllInBatch` before `saveAll`) rather than upserting, since a re-import always
 * means "the real upstream feed changed, replace it wholesale" -- same discipline a
 * real GTFS consumer needs (stop_ids/route_ids/trip_ids are only guaranteed stable
 * *within* one feed version, not guaranteed to carry over identically across a
 * republish).
 */
@Service
class TransitGtfsImportService(
    @Value("\${itunda.transit.gtfs-url:https://hub.tumidata.org/dataset/ea010576-23c7-4024-85bb-ea210b19756a/resource/8e63f35c-23fa-4483-b491-40de4a65a13f/download/kigali_gtfs.zip}")
    private val defaultGtfsUrl: String,
    private val transitStopRepository: TransitStopRepository,
    private val transitRouteRepository: TransitRouteRepository,
    private val transitScheduledTripRepository: TransitScheduledTripRepository,
    private val transitStopTimeRepository: TransitStopTimeRepository,
) {
    private val logger = LoggerFactory.getLogger(TransitGtfsImportService::class.java)

    @Transactional
    fun importFromUrl(url: String = defaultGtfsUrl): TransitGtfsImportResult {
        val bytes = try {
            RestClient.create().get().uri(url).retrieve().body(ByteArray::class.java)
                ?: throw TransitGtfsFetchFailedException("Empty response fetching GTFS feed")
        } catch (e: Exception) {
            logger.warn("Real Kigali GTFS fetch failed: {}", e.message)
            throw TransitGtfsFetchFailedException("Couldn't fetch the real GTFS feed: ${e.message}")
        }

        val files = mutableMapOf<String, List<Map<String, String>>>()
        ZipInputStream(bytes.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name.substringAfterLast('/')
                if (name in REQUIRED_FILES) {
                    val reader = BufferedReader(InputStreamReader(zip, StandardCharsets.UTF_8))
                    files[name] = parseCsv(reader.readText())
                }
                entry = zip.nextEntry
            }
        }

        val stopRows = files["stops.txt"] ?: throw TransitGtfsFetchFailedException("GTFS feed is missing stops.txt")
        val routeRows = files["routes.txt"] ?: throw TransitGtfsFetchFailedException("GTFS feed is missing routes.txt")
        val tripRows = files["trips.txt"] ?: throw TransitGtfsFetchFailedException("GTFS feed is missing trips.txt")
        val stopTimeRows = files["stop_times.txt"] ?: throw TransitGtfsFetchFailedException("GTFS feed is missing stop_times.txt")

        val stops = stopRows.mapNotNull { row ->
            val id = row["stop_id"]?.ifBlank { null } ?: return@mapNotNull null
            val lat = row["stop_lat"]?.toDoubleOrNull() ?: return@mapNotNull null
            val lng = row["stop_lon"]?.toDoubleOrNull() ?: return@mapNotNull null
            TransitStop(id = id, name = row["stop_name"]?.ifBlank { null } ?: id, latitude = lat, longitude = lng)
        }
        val routes = routeRows.mapNotNull { row ->
            val id = row["route_id"]?.ifBlank { null } ?: return@mapNotNull null
            TransitRoute(id = id, shortName = row["route_short_name"]?.ifBlank { null }, longName = row["route_long_name"]?.ifBlank { null })
        }
        val trips = tripRows.mapNotNull { row ->
            val id = row["trip_id"]?.ifBlank { null } ?: return@mapNotNull null
            val routeId = row["route_id"]?.ifBlank { null } ?: return@mapNotNull null
            TransitScheduledTrip(id = id, routeId = routeId, serviceId = row["service_id"]?.ifBlank { null })
        }
        val stopTimes = stopTimeRows.mapIndexedNotNull { index, row ->
            val tripId = row["trip_id"]?.ifBlank { null } ?: return@mapIndexedNotNull null
            val stopId = row["stop_id"]?.ifBlank { null } ?: return@mapIndexedNotNull null
            val arrival = parseGtfsTime(row["arrival_time"]) ?: return@mapIndexedNotNull null
            val departure = parseGtfsTime(row["departure_time"]) ?: arrival
            val sequence = row["stop_sequence"]?.toIntOrNull() ?: index
            TransitStopTime(
                id = "transit_stop_time_${tripId}_${stopId}_$sequence",
                tripId = tripId, stopId = stopId,
                arrivalSecondsAfterMidnight = arrival, departureSecondsAfterMidnight = departure, stopSequence = sequence,
            )
        }

        transitStopTimeRepository.deleteAllInBatch()
        transitScheduledTripRepository.deleteAllInBatch()
        transitRouteRepository.deleteAllInBatch()
        transitStopRepository.deleteAllInBatch()
        transitStopRepository.saveAll(stops)
        transitRouteRepository.saveAll(routes)
        transitScheduledTripRepository.saveAll(trips)
        transitStopTimeRepository.saveAll(stopTimes)

        return TransitGtfsImportResult(stops = stops.size, routes = routes.size, trips = trips.size, stopTimes = stopTimes.size)
    }

    companion object {
        private val REQUIRED_FILES = setOf("stops.txt", "routes.txt", "trips.txt", "stop_times.txt")

        /** Real GTFS "HH:MM:SS" time, allowing an hour past 24 for an overnight trip
         * (a real, documented GTFS convention) -- see TransitStopTime.kt's own doc
         * comment for why this can't be a java.time.LocalTime. Null on any malformed
         * value rather than crashing the whole import over one bad real row. */
        fun parseGtfsTime(raw: String?): Int? {
            val parts = raw?.trim()?.split(":") ?: return null
            if (parts.size != 3) return null
            val h = parts[0].toIntOrNull() ?: return null
            val m = parts[1].toIntOrNull() ?: return null
            val s = parts[2].toIntOrNull() ?: return null
            return h * 3600 + m * 60 + s
        }

        /** A real, minimal RFC 4180 CSV parser (quoted fields, embedded commas, escaped
         * "" -> " inside a quoted field) -- GTFS's own spec requires this; a naive
         * split(",") would corrupt any real stop/route name containing a comma. Returns
         * one map per data row, keyed by the real header row's own column names. */
        fun parseCsv(text: String): List<Map<String, String>> {
            val lines = splitCsvLines(text)
            if (lines.isEmpty()) return emptyList()
            val header = lines.first()
            return lines.drop(1).filter { it.isNotEmpty() }.map { row -> header.zip(row).toMap() }
        }

        private fun splitCsvLines(text: String): List<List<String>> {
            val rows = mutableListOf<List<String>>()
            var field = StringBuilder()
            var row = mutableListOf<String>()
            var inQuotes = false
            var i = 0
            while (i < text.length) {
                val c = text[i]
                when {
                    inQuotes && c == '"' && i + 1 < text.length && text[i + 1] == '"' -> { field.append('"'); i++ }
                    c == '"' -> inQuotes = !inQuotes
                    !inQuotes && c == ',' -> { row.add(field.toString().trim()); field = StringBuilder() }
                    !inQuotes && (c == '\n' || c == '\r') -> {
                        if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                        row.add(field.toString().trim())
                        field = StringBuilder()
                        rows.add(row)
                        row = mutableListOf()
                    }
                    else -> field.append(c)
                }
                i++
            }
            if (field.isNotEmpty() || row.isNotEmpty()) { row.add(field.toString().trim()); rows.add(row) }
            return rows
        }
    }
}
