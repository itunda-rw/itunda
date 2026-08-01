import SwiftUI
import CoreDesignSystem

private let DAYS = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"]
private let DAY_LABEL: [String: String] = [
    "MONDAY": "Mon", "TUESDAY": "Tue", "WEDNESDAY": "Wed", "THURSDAY": "Thu",
    "FRIDAY": "Fri", "SATURDAY": "Sat", "SUNDAY": "Sun",
]

/// Real local-business appointment booking, owner side -- see
/// rw.itunda.merchant.MerchantBookingService on the backend. Two real, independent
/// jobs: declare a weekly availability schedule (top), and confirm/decline/complete
/// incoming requests (below). merchant-mfe/Android already have this; this is the
/// first iOS client.
struct BookingTab: View {
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                AvailabilityEditor()
                Divider()
                BookingQueue()
            }
            .padding(16)
        }
    }
}

private struct AvailabilityEditor: View {
    @State private var windows: [AvailabilityWindowDto]?
    @State private var day = DAYS[0]
    @State private var start = "09:00"
    @State private var end = "17:00"
    @State private var error: String?
    @State private var saving = false

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Weekly availability").bold()
            Text("Customers can only request an appointment inside these windows.")
                .font(.footnote).foregroundColor(.secondary)

            if let windows {
                if windows.isEmpty {
                    Text("No availability set yet — add a window below.").font(.footnote)
                } else {
                    ForEach(windows, id: \.dayOfWeek) { w in
                        HStack {
                            Text("\(DAY_LABEL[w.dayOfWeek] ?? w.dayOfWeek) \(String(w.startTime.prefix(5)))-\(String(w.endTime.prefix(5)))")
                                .font(.footnote)
                            Spacer()
                            Button("Remove") { save(windows.filter { $0.dayOfWeek != w.dayOfWeek || $0.startTime != w.startTime } ) }
                                .disabled(saving)
                        }
                    }
                }
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 10) {
                        ForEach(DAYS, id: \.self) { d in
                            Text(DAY_LABEL[d] ?? d)
                                .bold(d == day)
                                .foregroundColor(d == day ? IDS.Colors.brand : .secondary)
                                .onTapGesture { day = d }
                        }
                    }
                }
                HStack {
                    TextField("Start (HH:mm)", text: $start)
                        .padding(10).background(Color(.tertiarySystemBackground)).cornerRadius(8)
                    TextField("End (HH:mm)", text: $end)
                        .padding(10).background(Color(.tertiarySystemBackground)).cornerRadius(8)
                }
                if let error {
                    Text(error).font(.footnote).foregroundColor(.red)
                }
                Button(action: {
                    guard let startMinutes = parseHm(start), let endMinutes = parseHm(end), startMinutes < endMinutes else {
                        error = "Enter a real start time before the end time (HH:mm)."
                        return
                    }
                    save(windows + [AvailabilityWindowDto(dayOfWeek: day, startTime: "\(start):00", endTime: "\(end):00")])
                }) {
                    Text(saving ? "Saving…" : "Add window")
                        .bold().foregroundColor(.white)
                        .frame(maxWidth: .infinity).padding(.vertical, 12)
                        .background(IDS.Colors.brand).cornerRadius(10)
                }
                .disabled(saving)
            } else {
                ProgressView()
            }
        }
        .task { await load() }
    }

    private func load() async {
        windows = (try? await MerchantNetworkClient.shared.getMyAvailability())?.windows ?? []
    }

    private func save(_ next: [AvailabilityWindowDto]) {
        saving = true
        error = nil
        Task {
            do {
                windows = try await MerchantNetworkClient.shared.setAvailability(SetAvailabilityRequest(windows: next)).windows
            } catch {
                self.error = "Couldn't save your availability. Try again."
            }
            saving = false
        }
    }

    private func parseHm(_ v: String) -> Int? {
        let parts = v.split(separator: ":")
        guard parts.count == 2, let h = Int(parts[0]), let m = Int(parts[1]), (0...23).contains(h), (0...59).contains(m) else { return nil }
        return h * 60 + m
    }
}

private struct BookingQueue: View {
    @State private var bookings: [MerchantBookingDto]?
    @State private var error: String?
    @State private var busyId: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Bookings").bold()
            if let error {
                Text(error).font(.footnote).foregroundColor(.red)
            }
            if let bookings {
                let active = bookings.filter { $0.status == "REQUESTED" || $0.status == "CONFIRMED" }
                if active.isEmpty {
                    Text("No open bookings right now.").font(.footnote).foregroundColor(.secondary)
                } else {
                    ForEach(active) { booking in
                        BookingRow(booking: booking, busy: busyId == booking.id, onRespond: { confirm in respond(booking.id, confirm: confirm) }, onComplete: { complete(booking.id) })
                    }
                }
            } else {
                ProgressView()
            }
        }
        .task {
            await refresh()
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 8_000_000_000)
                await refresh()
            }
        }
    }

    private func refresh() async {
        do {
            bookings = try await MerchantNetworkClient.shared.getMerchantBookings().bookings
            error = nil
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }

    private func respond(_ bookingId: String, confirm: Bool) {
        busyId = bookingId
        Task {
            do {
                _ = try await MerchantNetworkClient.shared.respondToBooking(bookingId, confirm: confirm)
                await refresh()
            } catch {
                self.error = "Couldn't update this booking."
            }
            busyId = nil
        }
    }

    private func complete(_ bookingId: String) {
        busyId = bookingId
        Task {
            do {
                _ = try await MerchantNetworkClient.shared.completeBooking(bookingId)
                await refresh()
            } catch {
                self.error = "Couldn't update this booking."
            }
            busyId = nil
        }
    }
}

private struct BookingRow: View {
    let booking: MerchantBookingDto
    let busy: Bool
    let onRespond: (Bool) -> Void
    let onComplete: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                BookingStatusBadge(status: booking.status)
                Spacer()
                Text("\(booking.bookingDate) \(String(booking.startTime.prefix(5)))").bold()
            }
            Text(booking.serviceName).font(.footnote)
            if let notes = booking.notes, !notes.isEmpty {
                Text("Note: \(notes)").font(.footnote)
            }
            if booking.status == "REQUESTED" {
                HStack {
                    Button(action: { onRespond(true) }) {
                        Text("Confirm").bold().foregroundColor(.white)
                            .frame(maxWidth: .infinity).padding(.vertical, 10)
                            .background(IDS.Colors.brand).cornerRadius(10)
                    }
                    .disabled(busy)
                    Button(action: { onRespond(false) }) {
                        Text("Decline").bold()
                            .padding(.horizontal, 14).padding(.vertical, 10)
                            .background(Color(.tertiarySystemBackground)).cornerRadius(10)
                    }
                    .disabled(busy)
                }
            } else if booking.status == "CONFIRMED" {
                Button(action: onComplete) {
                    Text(busy ? "Updating…" : "Mark completed").bold().foregroundColor(.white)
                        .frame(maxWidth: .infinity).padding(.vertical, 10)
                        .background(IDS.Colors.brand).cornerRadius(10)
                }
                .disabled(busy)
            }
        }
        .padding(14).frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(.secondarySystemBackground)).cornerRadius(12)
    }
}

private struct BookingStatusBadge: View {
    let status: String

    private var label: String {
        switch status {
        case "REQUESTED": return "Requested"
        case "CONFIRMED": return "Confirmed"
        case "DECLINED": return "Declined"
        case "CANCELLED": return "Cancelled"
        case "COMPLETED": return "Completed"
        default: return status
        }
    }

    var body: some View {
        Text(label)
            .font(.caption2).bold()
            .padding(.horizontal, 8).padding(.vertical, 2)
            .background(Color(.tertiarySystemBackground)).cornerRadius(8)
    }
}
