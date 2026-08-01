import SwiftUI
import CoreDesignSystem
import CoreNetwork

/// Real Kakao T 시외버스 (intercity bus booking, item 224) -- real PEER-TO-PEER
/// coach-operator trip pool (any user self-registers as an operator and posts a
/// scheduled route, no admin gate/real transport-licensing check). Fare is known and
/// charged in FULL at booking time -- distinct from RideScreenView.swift/
/// DesignatedDriverScreenView.swift/BikeRentalScreenView.swift/ParkingScreenView.swift,
/// which all either escrow-hold or settle at session end. bank-mfe/Android already have
/// this; this is the first iOS client, mirroring its Find-a-bus/My-routes toggle
/// exactly. Same honest "hours from now" v1 scope-down RideScreenView.swift's own
/// scheduled-ride booking already establishes for the departure time -- no real
/// date/time-picker precedent exists in this app.
struct BusScreenView: View {
    var onBack: () -> Void = {}
    @State private var tab = 0

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: onBack) { Image(systemName: "chevron.left").foregroundColor(IDS.Colors.textPrimary) }
                Spacer()
                Text("Bus").font(.headline).foregroundColor(IDS.Colors.textPrimary)
                Spacer()
                Color.clear.frame(width: 20)
            }
            .padding()

            Picker("", selection: $tab) {
                Text("Find a bus").tag(0)
                Text("My routes").tag(1)
            }
            .pickerStyle(.segmented)
            .padding(.horizontal)

            if tab == 0 {
                BusRideContent()
            } else {
                BusOperateContent()
            }
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
    }
}

private struct BusRideContent: View {
    @State private var origin = ""
    @State private var destination = ""
    @State private var trips: [BusTripDto]?
    @State private var myBookings: [BusBookingDto] = []
    @State private var seatCounts: [String: String] = [:]
    @State private var busyTripId: String?
    @State private var busyBookingId: String?
    @State private var error: String?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                if let error {
                    Text(error).font(.footnote).foregroundColor(.red)
                }
                VStack(alignment: .leading, spacing: 8) {
                    TextField("From", text: $origin)
                        .padding(12).background(Color(.tertiarySystemBackground)).cornerRadius(10)
                    TextField("To", text: $destination)
                        .padding(12).background(Color(.tertiarySystemBackground)).cornerRadius(10)
                    Button(action: { Task { await search() } }) {
                        Text("Search").bold().foregroundColor(.white)
                            .frame(maxWidth: .infinity).padding(.vertical, 12)
                            .background(IDS.Colors.brand).cornerRadius(10)
                    }
                }
                .padding(16).background(Color(.secondarySystemBackground)).cornerRadius(12)

                if let trips {
                    if trips.isEmpty {
                        Text("No upcoming trips found.").font(.footnote).foregroundColor(IDS.Colors.textSecondary)
                    } else {
                        ForEach(trips) { trip in
                            VStack(alignment: .leading, spacing: 6) {
                                Text("\(trip.origin) → \(trip.destination)").bold().foregroundColor(IDS.Colors.textPrimary)
                                Text("🕒 Departs \(String(trip.departureTime.prefix(16)).replacingOccurrences(of: "T", with: " "))")
                                    .font(.caption).bold().foregroundColor(IDS.Colors.brand)
                                Text("\(formatMoneyBus(trip.farePerSeat)) RWF/seat · \(trip.availableSeats) seat(s) left")
                                    .font(.caption).foregroundColor(IDS.Colors.textSecondary)
                                HStack {
                                    TextField("Seats", text: Binding(
                                        get: { seatCounts[trip.id] ?? "1" },
                                        set: { seatCounts[trip.id] = $0 }
                                    ))
                                    .keyboardType(.numberPad)
                                    .padding(10).background(Color(.tertiarySystemBackground)).cornerRadius(8)
                                    .frame(width: 60)
                                    Button(action: { Task { await bookSeats(trip.id) } }) {
                                        Text(busyTripId == trip.id ? "…" : "Book seats").bold().foregroundColor(.white)
                                            .frame(maxWidth: .infinity).padding(.vertical, 12)
                                            .background(IDS.Colors.brand).cornerRadius(10)
                                    }
                                    .disabled(busyTripId == trip.id)
                                }
                            }
                            .padding(16).background(Color(.secondarySystemBackground)).cornerRadius(12)
                        }
                    }
                } else {
                    ProgressView().frame(maxWidth: .infinity).padding(20)
                }

                let activeBookings = myBookings.filter { $0.status == "BOOKED" }
                if !activeBookings.isEmpty {
                    Text("Your bookings").bold().foregroundColor(IDS.Colors.textPrimary)
                    ForEach(activeBookings) { booking in
                        HStack {
                            VStack(alignment: .leading, spacing: 2) {
                                Text("\(booking.seatCount) seat(s)").bold().foregroundColor(IDS.Colors.textPrimary)
                                Text("\(formatMoneyBus(booking.totalFare)) RWF").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                            }
                            Spacer()
                            Button(action: { Task { await cancelBooking(booking.id) } }) {
                                Text(busyBookingId == booking.id ? "…" : "Cancel").font(.caption).bold()
                                    .padding(.horizontal, 14).padding(.vertical, 10)
                                    .background(Color(.tertiarySystemBackground)).cornerRadius(10)
                            }
                            .disabled(busyBookingId == booking.id)
                        }
                        .padding(16).background(Color(.secondarySystemBackground)).cornerRadius(12)
                    }
                }
            }
            .padding(.horizontal)
        }
        .task { await search(); await loadBookings() }
    }

    private func search() async {
        trips = (try? await NetworkClient.shared.searchBusTrips(origin: origin.trimmingCharacters(in: .whitespaces).isEmpty ? nil : origin, destination: destination.trimmingCharacters(in: .whitespaces).isEmpty ? nil : destination).trips) ?? []
    }

    private func loadBookings() async {
        myBookings = (try? await NetworkClient.shared.getMyBusBookings().bookings) ?? myBookings
    }

    private func bookSeats(_ tripId: String) async {
        let seats = Int(seatCounts[tripId] ?? "1") ?? 1
        guard seats >= 1 else { return }
        busyTripId = tripId
        error = nil
        do {
            _ = try await NetworkClient.shared.bookBusSeats(tripId: tripId, seatCount: seats)
            await search()
            await loadBookings()
        } catch {
            self.error = "Could not book these seats."
        }
        busyTripId = nil
    }

    private func cancelBooking(_ bookingId: String) async {
        busyBookingId = bookingId
        error = nil
        do {
            _ = try await NetworkClient.shared.cancelBusBooking(bookingId: bookingId)
            await loadBookings()
        } catch {
            self.error = "Could not cancel this booking."
        }
        busyBookingId = nil
    }
}

private struct BusOperateContent: View {
    @State private var origin = ""
    @State private var destination = ""
    @State private var departureHours = ""
    @State private var totalSeats = ""
    @State private var farePerSeat = ""
    @State private var posting = false
    @State private var myTrips: [BusTripDto] = []
    @State private var loaded = false
    @State private var error: String?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                if let error {
                    Text(error).font(.footnote).foregroundColor(.red)
                }
                VStack(alignment: .leading, spacing: 8) {
                    Text("Post a route").font(.headline).foregroundColor(IDS.Colors.textPrimary)
                    Text("Any itunda user can post a scheduled trip -- no transport-licensing check.")
                        .font(.caption).foregroundColor(IDS.Colors.textSecondary)
                    TextField("Origin", text: $origin)
                        .padding(12).background(Color(.tertiarySystemBackground)).cornerRadius(10)
                    TextField("Destination", text: $destination)
                        .padding(12).background(Color(.tertiarySystemBackground)).cornerRadius(10)
                    TextField("Departs in (hours from now)", text: $departureHours)
                        .keyboardType(.decimalPad)
                        .padding(12).background(Color(.tertiarySystemBackground)).cornerRadius(10)
                    TextField("Total seats", text: $totalSeats)
                        .keyboardType(.numberPad)
                        .padding(12).background(Color(.tertiarySystemBackground)).cornerRadius(10)
                    TextField("Fare per seat (RWF)", text: $farePerSeat)
                        .keyboardType(.decimalPad)
                        .padding(12).background(Color(.tertiarySystemBackground)).cornerRadius(10)
                    Button(action: { Task { await postTrip() } }) {
                        Text(posting ? "Posting…" : "Post route").bold().foregroundColor(.white)
                            .frame(maxWidth: .infinity).padding(.vertical, 12)
                            .background(IDS.Colors.brand).cornerRadius(10)
                    }
                    .disabled(posting)
                }
                .padding(16).background(Color(.secondarySystemBackground)).cornerRadius(12)

                if !loaded {
                    ProgressView().frame(maxWidth: .infinity).padding(20)
                } else if myTrips.isEmpty {
                    Text("You haven't posted any routes yet.").font(.footnote).foregroundColor(IDS.Colors.textSecondary)
                } else {
                    Text("Your routes").bold().foregroundColor(IDS.Colors.textPrimary)
                    ForEach(myTrips) { trip in
                        VStack(alignment: .leading, spacing: 4) {
                            Text("\(trip.origin) → \(trip.destination)").bold().foregroundColor(IDS.Colors.textPrimary)
                            Text("🕒 Departs \(String(trip.departureTime.prefix(16)).replacingOccurrences(of: "T", with: " "))")
                                .font(.caption).bold().foregroundColor(IDS.Colors.brand)
                            Text("\(trip.availableSeats)/\(trip.totalSeats) seats left · \(formatMoneyBus(trip.farePerSeat)) RWF/seat")
                                .font(.caption).foregroundColor(IDS.Colors.textSecondary)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(16).background(Color(.secondarySystemBackground)).cornerRadius(12)
                    }
                }
            }
            .padding(.horizontal)
        }
        .task { await load() }
    }

    private func load() async {
        myTrips = (try? await NetworkClient.shared.getMyBusTrips().trips) ?? myTrips
        loaded = true
    }

    private func postTrip() async {
        guard let hours = Double(departureHours), hours > 0,
              let seats = Int(totalSeats), seats > 0,
              let fare = Double(farePerSeat), fare > 0,
              !origin.trimmingCharacters(in: .whitespaces).isEmpty, !destination.trimmingCharacters(in: .whitespaces).isEmpty else {
            error = "Fill in every field with a real value."
            return
        }
        posting = true
        error = nil
        let departureTime = ISO8601DateFormatter().string(from: Date().addingTimeInterval(hours * 3600))
        do {
            _ = try await NetworkClient.shared.postBusTrip(origin: origin.trimmingCharacters(in: .whitespaces), destination: destination.trimmingCharacters(in: .whitespaces), departureTime: departureTime, totalSeats: seats, farePerSeat: fare)
            origin = ""; destination = ""; departureHours = ""; totalSeats = ""; farePerSeat = ""
            await load()
        } catch {
            self.error = "Could not post this route."
        }
        posting = false
    }
}

private func formatMoneyBus(_ value: Double) -> String {
    let rounded = (value * 100).rounded() / 100
    return rounded == rounded.rounded(.down) ? String(Int64(rounded)) : String(format: "%.2f", rounded)
}
