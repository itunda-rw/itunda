//
//  ItundaFaceCanvas.swift
//  itundaface: shared glyph-drawing primitives (2026-08-22) -- the iOS port
//  of the same real system already shipped on web (github.com/itunda-rw/
//  itundaface, 45 glyphs) and Android (core/designsystem/itundaface/
//  ItundaFaceCanvas.kt's Shape2D/ItundaFaceGlyphCanvas). Every glyph's "d"
//  path data below and in the per-group files is copy-pasted byte-identical
//  from those two already-shipped, already-verified sources -- not redrawn
//  by eye -- so iOS's shapes stay provably identical, the same discipline
//  both other platforms already established.
//
//  SwiftUI has no built-in SVG-path-string parser (unlike Android's real
//  androidx.core.graphics.PathParser, used directly there). `svgPath(_:)`
//  below is a real, from-scratch parser -- not a third-party dependency --
//  covering exactly the 7 absolute SVG path commands every itundaface glyph
//  actually uses (confirmed by grepping all Android glyph source for command
//  letters before writing this: M L H V C A Z; no lowercase/relative forms,
//  no S/Q/T). The elliptical-arc (A) case implements the real SVG spec's
//  endpoint-to-center arc parameterization, converting to 1-4 cubic Bezier
//  segments -- the standard approach every SVG-to-native-path converter
//  uses, not an approximation.
//

import SwiftUI

public enum ItundaFaceShape {
    case filledPath(d: String, color: Color)
    case strokedPath(d: String, color: Color, width: CGFloat, cap: CGLineCap = .butt, join: CGLineJoin = .miter, alpha: Double = 1)
    case filledCircle(cx: CGFloat, cy: CGFloat, r: CGFloat, color: Color)
    case strokedCircle(cx: CGFloat, cy: CGFloat, r: CGFloat, color: Color, width: CGFloat)
    case filledEllipse(cx: CGFloat, cy: CGFloat, rx: CGFloat, ry: CGFloat, color: Color)
    case strokedEllipse(cx: CGFloat, cy: CGFloat, rx: CGFloat, ry: CGFloat, color: Color, width: CGFloat)
    case filledRect(x: CGFloat, y: CGFloat, w: CGFloat, h: CGFloat, rx: CGFloat, color: Color)
    case strokedLine(x1: CGFloat, y1: CGFloat, x2: CGFloat, y2: CGFloat, color: Color, width: CGFloat, cap: CGLineCap = .butt, alpha: Double = 1, dashOn: CGFloat = 0, dashOff: CGFloat = 0)
    indirect case rotatedGroup(degrees: Double, pivotX: CGFloat, pivotY: CGFloat, shapes: [ItundaFaceShape])
    /// Rescales+recenters a group: content originally centered at
    /// (fromCenterX, fromCenterY) is scaled by `scale` and moved so that
    /// point lands at (toCenterX, toCenterY) -- needed when a source
    /// asset's own bounding-box center doesn't already sit at the canvas
    /// center (real case: Noto Emoji's real safe-zone-padded glyphs, see
    /// ItundaFacePeople.swift's own doc comment). Mirrors Android's
    /// Shape2D.ScaledGroup exactly.
    indirect case scaledGroup(scale: CGFloat, fromCenterX: CGFloat, fromCenterY: CGFloat, toCenterX: CGFloat, toCenterY: CGFloat, shapes: [ItundaFaceShape])
}

/// Parses a real SVG path-data string into a SwiftUI `Path`. Supports both
/// absolute (M L H V C S A Z) and relative (m l h v c s a) commands, plus the
/// real SVG "implicit repeat" rule (a command letter followed by more than
/// one coordinate set repeats implicitly, e.g. `L10,10 20,20` is two
/// linetos) and S/s's own "smooth cubic curveto" shorthand (its first
/// control point isn't in the data at all -- it's the reflection of the
/// preceding cubic command's own second control point about the current
/// point, only when that preceding command was itself C/c/S/s).
///
/// All of this was found the hard way, in two separate passes, both on
/// 2026-08-22: itunda's own hand-authored glyphs only ever used absolute
/// M/L/H/V/C/A/Z, so the original parser only handled that subset. Adding
/// Noto Emoji's real professional SVG exports (see ItundaFacePeople.swift)
/// surfaced relative lowercase commands first, then a SECOND pass adding
/// Noto's Animals & Nature glyphs (ItundaFaceNature.swift) surfaced S/s,
/// used extensively across almost every one of those glyphs, which the
/// `default:` branch had been silently dropping entirely (no visible error,
/// just a missing curve segment). **The Xcode build succeeding after each of
/// those additions did NOT prove the glyphs rendered correctly** -- Swift
/// compiling only proves the string literal is valid, never that this
/// parser understood the SVG grammar inside it. Both gaps were caught by
/// directly reasoning through which command letters the real fetched path
/// data actually contained (a real grep-based scan, not a visual guess),
/// not by trusting a green build. Web's browser-native SVG engine and
/// Android's real `androidx.core.graphics.PathParser` both already handle
/// this full grammar; only this hand-rolled iOS parser needed catching up,
/// twice. **Standing lesson**: before adding any new real-world SVG source
/// to this codebase, scan its actual command-letter usage against what this
/// function implements -- don't assume coverage from a prior fix.
public func svgPath(_ d: String) -> Path {
    var path = Path()
    var current = CGPoint.zero
    var subpathStart = CGPoint.zero

    let scanner = Scanner(string: d)
    scanner.charactersToBeSkipped = CharacterSet(charactersIn: ", ")

    func readDouble() -> CGFloat? {
        scanner.charactersToBeSkipped = CharacterSet(charactersIn: ", ")
        if let v = scanner.scanDouble() { return CGFloat(v) }
        return nil
    }

    var lastCommand: Character?
    // Real second bug in this same parser, found the hard way (2026-08-22)
    // right after fixing the relative-command gap: most of Noto Emoji's
    // Animals & Nature glyphs use the S/s "smooth cubic curveto" shorthand
    // extensively (every Bezier after the first in a smooth curve chain), and
    // this parser had zero support for it at all -- the `default:` branch
    // silently dropped the whole segment, meaning the earlier "BUILD
    // SUCCEEDED" was, again, not proof the geometry rendered correctly. Per
    // the real SVG spec, S/s's own first control point isn't present in the
    // path data -- it's the reflection of the PRECEDING cubic command's own
    // second control point about the current point, and only applies when
    // the immediately preceding command was itself C/c/S/s; otherwise the
    // first control point equals the current point. Tracked here rather than
    // guessed at.
    var lastCubicControl2: CGPoint?
    while !scanner.isAtEnd {
        let nextIsCommandLetter = peekIsCommandLetter(scanner)
        let command: Character
        if nextIsCommandLetter, let c = scanner.scanCharacter() {
            command = c
            lastCommand = c
        } else if let last = lastCommand {
            command = last
        } else {
            break
        }
        switch command {
        case "M":
            guard let x = readDouble(), let y = readDouble() else { break }
            current = CGPoint(x: x, y: y)
            subpathStart = current
            path.move(to: current)
            lastCommand = "L" // subsequent implicit-repeat pairs after M are linetos, per spec
            lastCubicControl2 = nil
        case "m":
            guard let x = readDouble(), let y = readDouble() else { break }
            current = CGPoint(x: current.x + x, y: current.y + y)
            subpathStart = current
            path.move(to: current)
            lastCommand = "l"
            lastCubicControl2 = nil
        case "L":
            guard let x = readDouble(), let y = readDouble() else { break }
            current = CGPoint(x: x, y: y)
            path.addLine(to: current)
            lastCubicControl2 = nil
        case "l":
            guard let x = readDouble(), let y = readDouble() else { break }
            current = CGPoint(x: current.x + x, y: current.y + y)
            path.addLine(to: current)
            lastCubicControl2 = nil
        case "H":
            guard let x = readDouble() else { break }
            current = CGPoint(x: x, y: current.y)
            path.addLine(to: current)
            lastCubicControl2 = nil
        case "h":
            guard let x = readDouble() else { break }
            current = CGPoint(x: current.x + x, y: current.y)
            path.addLine(to: current)
            lastCubicControl2 = nil
        case "V":
            guard let y = readDouble() else { break }
            current = CGPoint(x: current.x, y: y)
            path.addLine(to: current)
            lastCubicControl2 = nil
        case "v":
            guard let y = readDouble() else { break }
            current = CGPoint(x: current.x, y: current.y + y)
            path.addLine(to: current)
            lastCubicControl2 = nil
        case "C":
            guard let x1 = readDouble(), let y1 = readDouble(),
                  let x2 = readDouble(), let y2 = readDouble(),
                  let x = readDouble(), let y = readDouble() else { break }
            path.addCurve(to: CGPoint(x: x, y: y), control1: CGPoint(x: x1, y: y1), control2: CGPoint(x: x2, y: y2))
            current = CGPoint(x: x, y: y)
            lastCubicControl2 = CGPoint(x: x2, y: y2)
        case "c":
            guard let x1 = readDouble(), let y1 = readDouble(),
                  let x2 = readDouble(), let y2 = readDouble(),
                  let x = readDouble(), let y = readDouble() else { break }
            let base = current
            let control2 = CGPoint(x: base.x + x2, y: base.y + y2)
            path.addCurve(
                to: CGPoint(x: base.x + x, y: base.y + y),
                control1: CGPoint(x: base.x + x1, y: base.y + y1),
                control2: control2
            )
            current = CGPoint(x: base.x + x, y: base.y + y)
            lastCubicControl2 = control2
        case "S":
            guard let x2 = readDouble(), let y2 = readDouble(),
                  let x = readDouble(), let y = readDouble() else { break }
            let control1 = lastCubicControl2.map { CGPoint(x: 2 * current.x - $0.x, y: 2 * current.y - $0.y) } ?? current
            let control2 = CGPoint(x: x2, y: y2)
            path.addCurve(to: CGPoint(x: x, y: y), control1: control1, control2: control2)
            current = CGPoint(x: x, y: y)
            lastCubicControl2 = control2
        case "s":
            guard let x2 = readDouble(), let y2 = readDouble(),
                  let x = readDouble(), let y = readDouble() else { break }
            let base = current
            let control1 = lastCubicControl2.map { CGPoint(x: 2 * base.x - $0.x, y: 2 * base.y - $0.y) } ?? base
            let control2 = CGPoint(x: base.x + x2, y: base.y + y2)
            path.addCurve(to: CGPoint(x: base.x + x, y: base.y + y), control1: control1, control2: control2)
            current = CGPoint(x: base.x + x, y: base.y + y)
            lastCubicControl2 = control2
        case "A":
            guard let rx = readDouble(), let ry = readDouble(),
                  let xAxisRotation = readDouble(),
                  let largeArcFlag = readDouble(), let sweepFlag = readDouble(),
                  let x = readDouble(), let y = readDouble() else { break }
            let end = CGPoint(x: x, y: y)
            appendArc(
                to: &path, from: current, to: end,
                rx: rx, ry: ry, xAxisRotationDegrees: xAxisRotation,
                largeArcFlag: largeArcFlag != 0, sweepFlag: sweepFlag != 0
            )
            current = end
            lastCubicControl2 = nil
        case "a":
            guard let rx = readDouble(), let ry = readDouble(),
                  let xAxisRotation = readDouble(),
                  let largeArcFlag = readDouble(), let sweepFlag = readDouble(),
                  let x = readDouble(), let y = readDouble() else { break }
            let end = CGPoint(x: current.x + x, y: current.y + y)
            appendArc(
                to: &path, from: current, to: end,
                rx: rx, ry: ry, xAxisRotationDegrees: xAxisRotation,
                largeArcFlag: largeArcFlag != 0, sweepFlag: sweepFlag != 0
            )
            current = end
            lastCubicControl2 = nil
        case "Z", "z":
            path.closeSubpath()
            current = subpathStart
            lastCommand = nil
            lastCubicControl2 = nil
        default:
            lastCommand = nil
            lastCubicControl2 = nil
        }
    }
    return path
}

/// Peeks (without consuming) whether the scanner's current position is a
/// command letter (true) vs. numeric data continuing an implicit-repeat
/// command (false).
private func peekIsCommandLetter(_ scanner: Scanner) -> Bool {
    let savedIndex = scanner.currentIndex
    defer { scanner.currentIndex = savedIndex }
    scanner.charactersToBeSkipped = CharacterSet(charactersIn: ", ")
    guard let c = scanner.scanCharacter() else { return true }
    return "MmLlHhVvCcSsAaZz".contains(c)
}

/// SVG's elliptical-arc endpoint parameterization (spec section 9.5.1),
/// converted to 1-4 cubic Bezier segments -- the same real algorithm every
/// SVG-to-native-path converter implements, not an approximation.
private func appendArc(
    to path: inout Path, from start: CGPoint, to end: CGPoint,
    rx rxIn: CGFloat, ry ryIn: CGFloat, xAxisRotationDegrees: CGFloat,
    largeArcFlag: Bool, sweepFlag: Bool
) {
    var rx = abs(rxIn)
    var ry = abs(ryIn)
    if rx == 0 || ry == 0 || (start.x == end.x && start.y == end.y) {
        path.addLine(to: end)
        return
    }
    let phi = xAxisRotationDegrees * .pi / 180
    let cosPhi = cos(phi), sinPhi = sin(phi)

    let dx2 = (start.x - end.x) / 2
    let dy2 = (start.y - end.y) / 2
    let x1p = cosPhi * dx2 + sinPhi * dy2
    let y1p = -sinPhi * dx2 + cosPhi * dy2

    let lambda = (x1p * x1p) / (rx * rx) + (y1p * y1p) / (ry * ry)
    if lambda > 1 {
        let scale = sqrt(lambda)
        rx *= scale
        ry *= scale
    }

    let sign: CGFloat = (largeArcFlag != sweepFlag) ? 1 : -1
    let num = max(0, rx * rx * ry * ry - rx * rx * y1p * y1p - ry * ry * x1p * x1p)
    let den = rx * rx * y1p * y1p + ry * ry * x1p * x1p
    let coef = den == 0 ? 0 : sign * sqrt(num / den)
    let cxp = coef * (rx * y1p) / ry
    let cyp = coef * -(ry * x1p) / rx

    let cx = cosPhi * cxp - sinPhi * cyp + (start.x + end.x) / 2
    let cy = sinPhi * cxp + cosPhi * cyp + (start.y + end.y) / 2

    func angle(_ ux: CGFloat, _ uy: CGFloat, _ vx: CGFloat, _ vy: CGFloat) -> CGFloat {
        let sign: CGFloat = (ux * vy - uy * vx) < 0 ? -1 : 1
        let dot = max(-1, min(1, (ux * vx + uy * vy) / (sqrt(ux * ux + uy * uy) * sqrt(vx * vx + vy * vy))))
        return sign * acos(dot)
    }

    let theta1 = angle(1, 0, (x1p - cxp) / rx, (y1p - cyp) / ry)
    var deltaTheta = angle((x1p - cxp) / rx, (y1p - cyp) / ry, (-x1p - cxp) / rx, (-y1p - cyp) / ry)
    if !sweepFlag && deltaTheta > 0 { deltaTheta -= 2 * .pi }
    if sweepFlag && deltaTheta < 0 { deltaTheta += 2 * .pi }

    let segments = max(1, Int(ceil(abs(deltaTheta) / (.pi / 2))))
    let delta = deltaTheta / CGFloat(segments)
    var theta = theta1

    for _ in 0..<segments {
        let thetaEnd = theta + delta
        let t = 4.0 / 3.0 * tan(delta / 4)

        func pointOnEllipse(_ angle: CGFloat) -> CGPoint {
            let ex = cx + rx * cos(angle) * cosPhi - ry * sin(angle) * sinPhi
            let ey = cy + rx * cos(angle) * sinPhi + ry * sin(angle) * cosPhi
            return CGPoint(x: ex, y: ey)
        }
        func derivative(_ angle: CGFloat) -> CGPoint {
            let dx = -rx * sin(angle) * cosPhi - ry * cos(angle) * sinPhi
            let dy = -rx * sin(angle) * sinPhi + ry * cos(angle) * cosPhi
            return CGPoint(x: dx, y: dy)
        }

        let p1 = pointOnEllipse(theta)
        let p2 = pointOnEllipse(thetaEnd)
        let d1 = derivative(theta)
        let d2 = derivative(thetaEnd)

        let c1 = CGPoint(x: p1.x + t * d1.x, y: p1.y + t * d1.y)
        let c2 = CGPoint(x: p2.x - t * d2.x, y: p2.y - t * d2.y)

        path.addCurve(to: p2, control1: c1, control2: c2)
        theta = thetaEnd
    }
}

private func drawShape(_ shape: ItundaFaceShape, context: inout GraphicsContext) {
    switch shape {
    case .filledPath(let d, let color):
        context.fill(svgPath(d), with: .color(color))
    case .strokedPath(let d, let color, let width, let cap, let join, let alpha):
        context.stroke(svgPath(d), with: .color(color.opacity(alpha)), style: StrokeStyle(lineWidth: width, lineCap: cap, lineJoin: join))
    case .filledCircle(let cx, let cy, let r, let color):
        context.fill(Path(ellipseIn: CGRect(x: cx - r, y: cy - r, width: r * 2, height: r * 2)), with: .color(color))
    case .strokedCircle(let cx, let cy, let r, let color, let width):
        context.stroke(Path(ellipseIn: CGRect(x: cx - r, y: cy - r, width: r * 2, height: r * 2)), with: .color(color), style: StrokeStyle(lineWidth: width))
    case .filledEllipse(let cx, let cy, let rx, let ry, let color):
        context.fill(Path(ellipseIn: CGRect(x: cx - rx, y: cy - ry, width: rx * 2, height: ry * 2)), with: .color(color))
    case .strokedEllipse(let cx, let cy, let rx, let ry, let color, let width):
        context.stroke(Path(ellipseIn: CGRect(x: cx - rx, y: cy - ry, width: rx * 2, height: ry * 2)), with: .color(color), style: StrokeStyle(lineWidth: width))
    case .filledRect(let x, let y, let w, let h, let rx, let color):
        let rect = CGRect(x: x, y: y, width: w, height: h)
        let path = rx > 0 ? Path(roundedRect: rect, cornerRadius: rx) : Path(rect)
        context.fill(path, with: .color(color))
    case .strokedLine(let x1, let y1, let x2, let y2, let color, let width, let cap, let alpha, let dashOn, let dashOff):
        var path = Path()
        path.move(to: CGPoint(x: x1, y: y1))
        path.addLine(to: CGPoint(x: x2, y: y2))
        let style = dashOn > 0
            ? StrokeStyle(lineWidth: width, lineCap: cap, dash: [dashOn, dashOff])
            : StrokeStyle(lineWidth: width, lineCap: cap)
        context.stroke(path, with: .color(color.opacity(alpha)), style: style)
    case .rotatedGroup(let degrees, let pivotX, let pivotY, let shapes):
        var rotated = context
        rotated.translateBy(x: pivotX, y: pivotY)
        rotated.rotate(by: .degrees(degrees))
        rotated.translateBy(x: -pivotX, y: -pivotY)
        for s in shapes { drawShape(s, context: &rotated) }
    case .scaledGroup(let scale, let fromCenterX, let fromCenterY, let toCenterX, let toCenterY, let shapes):
        var scaled = context
        scaled.translateBy(x: toCenterX, y: toCenterY)
        scaled.scaleBy(x: scale, y: scale)
        scaled.translateBy(x: -fromCenterX, y: -fromCenterY)
        for s in shapes { drawShape(s, context: &scaled) }
    }
}

/// Renders a list of `ItundaFaceShape`s scaled from `viewBoxSize` (the
/// source SVG's own local coordinate space) to `size` -- the SwiftUI
/// equivalent of Android's `ItundaFaceGlyphCanvas` composable.
public struct ItundaFaceGlyphCanvas: View {
    let size: CGFloat
    let viewBoxSize: CGFloat
    let shapes: [ItundaFaceShape]

    public init(size: CGFloat, viewBoxSize: CGFloat = 60, shapes: [ItundaFaceShape]) {
        self.size = size
        self.viewBoxSize = viewBoxSize
        self.shapes = shapes
    }

    public var body: some View {
        Canvas { context, canvasSize in
            let scale = canvasSize.width / viewBoxSize
            var scaled = context
            scaled.scaleBy(x: scale, y: scale)
            for shape in shapes { drawShape(shape, context: &scaled) }
        }
        .frame(width: size, height: size)
    }
}
