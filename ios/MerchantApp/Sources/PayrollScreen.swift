import SwiftUI
import CoreDesignSystem

/// Real B2B payroll -- see rw.itunda.merchant.PayrollService's own doc comment for why
/// this is real account-to-account money movement, not a demo. merchant-mfe/Android
/// already have this; this is the first iOS client.
struct PayrollTab: View {
    @State private var roster: [PayrollEmployeeDto]?
    @State private var loadError: String?
    @State private var runResult: PayrollRunResponse?

    var body: some View {
        if let runResult {
            PayrollRunConfirmation(result: runResult, onDone: {
                self.runResult = nil
                Task { await load() }
            })
        } else {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    AddEmployeeCard(onAdded: { Task { await load() } })
                    RosterCard(
                        roster: roster,
                        error: loadError,
                        onReload: { Task { await load() } },
                        onRunPayroll: { runResult = $0 }
                    )
                    if let roster {
                        ForEach(roster) { employee in
                            EmployeeRow(employee: employee, onChanged: { Task { await load() } })
                        }
                    }
                }
                .padding(16)
            }
            .task { await load() }
        }
    }

    private func load() async {
        loadError = nil
        do {
            roster = try await MerchantNetworkClient.shared.getPayrollRoster().employees
        } catch {
            loadError = "Could not load the payroll roster."
        }
    }
}

private struct AddEmployeeCard: View {
    let onAdded: () -> Void

    @State private var phoneNumber = ""
    @State private var salaryAmount = ""
    @State private var error: String?
    @State private var submitting = false

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Add an employee").font(.headline)
            Text("Must be an existing Itunda user's phone number — payroll pays directly into their account.")
                .font(.footnote).foregroundColor(.secondary)
            IdsTextField("Phone number (+250788123456)", text: $phoneNumber, keyboardType: .phonePad)
            IdsTextField("Monthly salary (RWF)", text: $salaryAmount, keyboardType: .decimalPad)
            if let error {
                Text(error).font(.footnote).foregroundColor(.red)
            }
            Button(action: { Task { await submit() } }) {
                Text(submitting ? "Adding…" : "Add")
                    .bold().foregroundColor(.white)
                    .frame(maxWidth: .infinity).padding(.vertical, 12)
                    .background(IDS.Colors.brand).cornerRadius(10)
            }
            .disabled(submitting)
        }
        .padding(16).background(Color(.secondarySystemBackground)).cornerRadius(12)
    }

    private func submit() async {
        guard let salary = Double(salaryAmount), salary > 0, !phoneNumber.trimmingCharacters(in: .whitespaces).isEmpty else {
            error = "Enter a real phone number and salary."
            return
        }
        submitting = true
        error = nil
        defer { submitting = false }
        do {
            _ = try await MerchantNetworkClient.shared.addPayrollEmployee(
                AddPayrollEmployeeRequest(phoneNumber: phoneNumber.trimmingCharacters(in: .whitespaces), salaryAmount: salary)
            )
            phoneNumber = ""; salaryAmount = ""
            onAdded()
        } catch {
            self.error = "Could not add this employee."
        }
    }
}

private struct RosterCard: View {
    let roster: [PayrollEmployeeDto]?
    let error: String?
    let onReload: () -> Void
    let onRunPayroll: (PayrollRunResponse) -> Void

    @State private var runError: String?
    @State private var running = false
    @State private var needsDeviceVerification = false

    var body: some View {
        if needsDeviceVerification {
            ZStack {
                Color.black.opacity(0.3).ignoresSafeArea()
                // Real fix (2026-08-10): found live-testing bank-mfe's identical device
                // verification flow -- onVerified used to just clear the flag, so
                // completing the password prompt did nothing; the merchant still had to
                // find and tap "Run payroll" a second time for the batch they'd already
                // confirmed.
                DeviceStepUpDialog(
                    onVerified: { if let roster { Task { await runPayroll(roster: roster) } } else { needsDeviceVerification = false } },
                    onCancel: { needsDeviceVerification = false }
                )
            }
        } else {
            VStack(alignment: .leading, spacing: 8) {
                if let error {
                    Text(error).font(.footnote).foregroundColor(.red)
                    Button("Retry", action: onReload)
                } else if let roster {
                    HStack {
                        Text("Roster (\(roster.count))").font(.headline)
                    }
                    if let runError {
                        Text(runError).font(.footnote).foregroundColor(.red)
                    }
                    Button(action: { Task { await runPayroll(roster: roster) } }) {
                        Text(running ? "Running…" : "Run payroll (\(Int(roster.reduce(0) { $0 + $1.salaryAmount })) RWF)")
                            .bold().foregroundColor(.white)
                            .frame(maxWidth: .infinity).padding(.vertical, 12)
                            .background(roster.isEmpty ? Color.gray : IDS.Colors.brand).cornerRadius(10)
                    }
                    .disabled(running || roster.isEmpty)
                    if roster.isEmpty {
                        Text("No employees on the roster yet.").font(.footnote).foregroundColor(.secondary)
                    }
                } else {
                    Text("Loading…").font(.footnote).foregroundColor(.secondary)
                }
            }
            .padding(16).frame(maxWidth: .infinity, alignment: .leading)
            .background(Color(.secondarySystemBackground)).cornerRadius(12)
        }
    }

    private func runPayroll(roster: [PayrollEmployeeDto]) async {
        running = true
        runError = nil
        needsDeviceVerification = false
        defer { running = false }
        do {
            let result = try await MerchantNetworkClient.shared.runPayroll()
            onRunPayroll(result)
        } catch NetworkError.deviceNotVerified {
            needsDeviceVerification = true
        } catch {
            runError = "Could not run payroll."
        }
    }
}

private struct EmployeeRow: View {
    let employee: PayrollEmployeeDto
    let onChanged: () -> Void

    @State private var removing = false
    @State private var error: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack {
                Text(employee.employeeName).bold().font(.subheadline)
                Spacer()
                Text("\(Int(employee.salaryAmount)) RWF").font(.subheadline)
            }
            if let error {
                Text(error).font(.caption).foregroundColor(.red)
            }
            Button(action: { Task { await remove() } }) {
                Text(removing ? "…" : "Remove").bold().font(.caption)
                    .padding(.horizontal, 12).padding(.vertical, 8)
                    .background(Color(.tertiarySystemBackground)).cornerRadius(8)
            }
            .disabled(removing)
        }
        .padding(12).frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(.tertiarySystemBackground)).cornerRadius(10)
    }

    private func remove() async {
        removing = true
        error = nil
        defer { removing = false }
        do {
            _ = try await MerchantNetworkClient.shared.removePayrollEmployee(employee.id)
            onChanged()
        } catch {
            self.error = "Could not remove this employee."
        }
    }
}

private struct PayrollRunConfirmation: View {
    let result: PayrollRunResponse
    let onDone: () -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text("Payroll paid").font(.title2).bold()
                Text("\(Int(result.totalAmount)) RWF").font(.largeTitle).bold()
                Text("\(result.employeeCount) employees paid").font(.subheadline).foregroundColor(.secondary)
                ForEach(result.payslips) { p in
                    HStack {
                        Text(p.employeeName).font(.footnote)
                        Spacer()
                        Text("\(Int(p.amount)) RWF").bold().font(.footnote)
                    }
                    Divider()
                }
                Button(action: onDone) {
                    Text("Back to roster")
                        .bold().foregroundColor(.white)
                        .frame(maxWidth: .infinity).padding(.vertical, 12)
                        .background(IDS.Colors.brand).cornerRadius(10)
                }
            }
            .padding(16)
        }
    }
}
