//
//  IDSIcons.swift
//  itunda's own navigation/action icon set (2026-08-24) -- part of the cross-platform
//  icon-consistency initiative (see commit 03938893's motion-primitives header and
//  commit d2164c4d's web icon-phase-2 header, both on agent/itunda-agent-network, for
//  the full research background). Direct user follow-up: "let's search how toss
//  archived [cross-platform consistency] ... let's itunda look the same across all
//  three platforms as toss do". Real audit found itunda's actual gap: web used
//  lucide-react, Android used Material Icons, iOS uses SF Symbols -- the same UI
//  concept rendered as 3 visually different glyphs. web already shipped
//  icons/ItundaIcons.tsx (IconBack/IconChevronRight/IconClose/IconSearch/IconAdd, a
//  0-24 coordinate space, 2.4-unit stroke, round caps/joins); Android got the same 5
//  as IdsIcons (core/designsystem, ImageVector-based). This file is the iOS port --
//  same byte-identical geometry, not redrawn or approximated.
//
//  Unlike itundaface's illustration-style glyphs (which needed a full hand-rolled
//  SVG-path-string parser in ItundaFaceCanvas.swift for complex curves), these 5
//  icons are geometrically trivial -- 2-3 straight line segments each, or a circle +
//  line -- so they're built as plain SwiftUI `Shape` structs rather than reusing that
//  parser; a directly-readable native Shape is the right engineering choice for
//  geometry this simple. Each Shape's `path(in rect:)` scales the 0-24 reference
//  coordinate space to whatever rect it's given (`rect.width/24`, `rect.height/24`),
//  matching how SF Symbols themselves scale to any requested size.
//
//  Phase 3 (same session): Star/Send/Bell/ShieldCheck/Eye/EyeOff, matching web's
//  phase-3 icons/ItundaIcons.tsx and Android's IdsIcons additions -- byte-identical
//  path data. Real call-site audit found and skipped several icon-name strings fed
//  into shared generic components (IconGridSection's `(label, symbolName)` tuples,
//  FlatRow/TopBarActionButton's `symbol:` params) that mix these concepts with many
//  unrelated SF Symbols in the same config array -- retrofitting those shared
//  components to accept a custom View instead of a String is a real, separately-
//  scoped structural change, not part of this slice. Rolled out only to direct,
//  single-purpose `Image(systemName:)` call sites.
//

import SwiftUI

private func p(_ x: CGFloat, _ y: CGFloat, in rect: CGRect) -> CGPoint {
    CGPoint(x: rect.minX + x / 24 * rect.width, y: rect.minY + y / 24 * rect.height)
}

public struct IDSBackShape: Shape {
    public init() {}
    public func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: p(15, 4, in: rect))
        path.addLine(to: p(7, 12, in: rect))
        path.addLine(to: p(15, 20, in: rect))
        return path
    }
}

public struct IDSChevronRightShape: Shape {
    public init() {}
    public func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: p(9, 4, in: rect))
        path.addLine(to: p(17, 12, in: rect))
        path.addLine(to: p(9, 20, in: rect))
        return path
    }
}

public struct IDSCloseShape: Shape {
    public init() {}
    public func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: p(5, 5, in: rect))
        path.addLine(to: p(19, 19, in: rect))
        path.move(to: p(19, 5, in: rect))
        path.addLine(to: p(5, 19, in: rect))
        return path
    }
}

public struct IDSSearchShape: Shape {
    public init() {}
    public func path(in rect: CGRect) -> Path {
        var path = Path()
        let center = p(10.5, 10.5, in: rect)
        let radius = 7 / 24 * min(rect.width, rect.height)
        path.addEllipse(in: CGRect(x: center.x - radius, y: center.y - radius, width: radius * 2, height: radius * 2))
        path.move(to: p(20, 20, in: rect))
        path.addLine(to: p(15.3, 15.3, in: rect))
        return path
    }
}

public struct IDSAddShape: Shape {
    public init() {}
    public func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: p(12, 4, in: rect))
        path.addLine(to: p(12, 20, in: rect))
        path.move(to: p(4, 12, in: rect))
        path.addLine(to: p(20, 12, in: rect))
        return path
    }
}

// Phase 3 (2026-08-24, same session, continuing "full commitment, all 3 platforms"):
// byte-identical geometry to web's IconStar/IconSend/IconBell/IconShieldCheck/
// IconEye/IconEyeOff (services/micro-frontends/bank-mfe/src/icons/ItundaIcons.tsx)
// and Android's matching IdsIcons additions. Unlike web/Android these are all
// stroke-only (`.stroke`, never `.fill`) even for Star -- matching the real
// convention web already shipped: a "watched"/"rated" state is communicated by
// color alone (e.g. `.yellow` vs `IDS.Colors.textSecondary`), not by switching to a
// solid glyph, so replacing SF Symbols' separate "star"/"star.fill" pair with a
// single stroke shape is a faithful port, not a regression.
public struct IDSStarShape: Shape {
    public init() {}
    public func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: p(12, 2.5, in: rect))
        path.addLine(to: p(14.9, 9, in: rect))
        path.addLine(to: p(22, 9.7, in: rect))
        path.addLine(to: p(16.7, 14.5, in: rect))
        path.addLine(to: p(18.2, 21.5, in: rect))
        path.addLine(to: p(12, 17.8, in: rect))
        path.addLine(to: p(5.8, 21.5, in: rect))
        path.addLine(to: p(7.3, 14.5, in: rect))
        path.addLine(to: p(2, 9.7, in: rect))
        path.addLine(to: p(9.1, 9, in: rect))
        path.closeSubpath()
        return path
    }
}

public struct IDSSendShape: Shape {
    public init() {}
    public func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: p(3, 11, in: rect))
        path.addLine(to: p(21, 3, in: rect))
        path.addLine(to: p(13, 21, in: rect))
        path.addLine(to: p(11, 13, in: rect))
        path.closeSubpath()
        path.move(to: p(11, 13, in: rect))
        path.addLine(to: p(21, 3, in: rect))
        return path
    }
}

public struct IDSBellShape: Shape {
    public init() {}
    public func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: p(6, 10.5, in: rect))
        path.addCurve(to: p(12, 4, in: rect), control1: p(6, 6.9, in: rect), control2: p(8.7, 4, in: rect))
        path.addCurve(to: p(18, 10.5, in: rect), control1: p(15.3, 4, in: rect), control2: p(18, 6.9, in: rect))
        path.addLine(to: p(18, 14.5, in: rect))
        path.addLine(to: p(20.2, 17.5, in: rect))
        path.addLine(to: p(3.8, 17.5, in: rect))
        path.addLine(to: p(6, 14.5, in: rect))
        path.closeSubpath()
        path.move(to: p(9.8, 19.8, in: rect))
        path.addCurve(to: p(12, 22, in: rect), control1: p(9.8, 21, in: rect), control2: p(10.8, 22, in: rect))
        path.addCurve(to: p(14.2, 19.8, in: rect), control1: p(13.2, 22, in: rect), control2: p(14.2, 21, in: rect))
        return path
    }
}

public struct IDSShieldCheckShape: Shape {
    public init() {}
    public func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: p(12, 2.5, in: rect))
        path.addLine(to: p(20, 5.5, in: rect))
        path.addLine(to: p(20, 11, in: rect))
        path.addCurve(to: p(12, 21.8, in: rect), control1: p(20, 16.2, in: rect), control2: p(16.6, 20.4, in: rect))
        path.addCurve(to: p(4, 11, in: rect), control1: p(7.4, 20.4, in: rect), control2: p(4, 16.2, in: rect))
        path.addLine(to: p(4, 5.5, in: rect))
        path.closeSubpath()
        path.move(to: p(8.5, 12, in: rect))
        path.addLine(to: p(11, 14.5, in: rect))
        path.addLine(to: p(15.5, 9.5, in: rect))
        return path
    }
}

public struct IDSEyeShape: Shape {
    public init() {}
    public func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: p(2, 12, in: rect))
        path.addCurve(to: p(12, 4.5, in: rect), control1: p(4, 7, in: rect), control2: p(8, 4.5, in: rect))
        path.addCurve(to: p(22, 12, in: rect), control1: p(16, 4.5, in: rect), control2: p(20, 7, in: rect))
        path.addCurve(to: p(12, 19.5, in: rect), control1: p(20, 17, in: rect), control2: p(16, 19.5, in: rect))
        path.addCurve(to: p(2, 12, in: rect), control1: p(8, 19.5, in: rect), control2: p(4, 17, in: rect))
        path.closeSubpath()
        let center = p(12, 12, in: rect)
        let radius = 3 / 24 * min(rect.width, rect.height)
        path.addEllipse(in: CGRect(x: center.x - radius, y: center.y - radius, width: radius * 2, height: radius * 2))
        return path
    }
}

public struct IDSEyeOffShape: Shape {
    public init() {}
    public func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: p(4.2, 4.2, in: rect))
        path.addLine(to: p(19.8, 19.8, in: rect))
        path.move(to: p(10.3, 5.1, in: rect))
        path.addCurve(to: p(12, 4.8, in: rect), control1: p(10.9, 4.9, in: rect), control2: p(11.4, 4.8, in: rect))
        path.addCurve(to: p(22, 12.3, in: rect), control1: p(16, 4.8, in: rect), control2: p(20, 7.3, in: rect))
        path.addCurve(to: p(19.8, 15.9, in: rect), control1: p(21.4, 13.7, in: rect), control2: p(20.7, 14.9, in: rect))
        path.move(to: p(6.4, 6.9, in: rect))
        path.addCurve(to: p(2, 12.3, in: rect), control1: p(4.4, 8.2, in: rect), control2: p(2.9, 10.1, in: rect))
        path.addCurve(to: p(12, 19.8, in: rect), control1: p(4, 17.3, in: rect), control2: p(8, 19.8, in: rect))
        path.addCurve(to: p(15.8, 19, in: rect), control1: p(13.3, 19.8, in: rect), control2: p(14.6, 19.5, in: rect))
        path.move(to: p(9.6, 10, in: rect))
        path.addCurve(to: p(9, 11.8, in: rect), control1: p(9.2, 10.5, in: rect), control2: p(9, 11.1, in: rect))
        path.addCurve(to: p(12, 14.8, in: rect), control1: p(9, 13.5, in: rect), control2: p(10.3, 14.8, in: rect))
        path.addCurve(to: p(13.8, 14.2, in: rect), control1: p(12.7, 14.8, in: rect), control2: p(13.3, 14.6, in: rect))
        return path
    }
}

/// Real drop-in replacement for `Image(systemName: "chevron.left").font(...)
/// .foregroundColor(...)`-shaped call sites -- `size`/`color` cover the real
/// variance seen across existing call sites (different `.font(.system(size:))`
/// values, different `.foregroundColor`s), stroke width scales proportionally with
/// size to keep the same real 2.4/24 ratio web/Android use at any requested size.
/// `relativeTo`, when given, makes the icon respond to the user's real OS Dynamic
/// Type setting via `UIFontMetrics.scaledValue(for:)` -- the same real mechanism
/// `IDS.scaledFont` already uses for text, mirrored here so replacing an SF Symbol
/// (which auto-scales with Dynamic Type by default) doesn't silently regress the
/// accessibility work an earlier pass this session did (90+ sites converted from
/// raw `Font.system(size:)` to `IDS.scaledFont` specifically so text/icons respond
/// to a user's real accessibility text-size setting). `nil` (the default) keeps a
/// fixed size, for call sites that genuinely want one (e.g. a small inline glyph
/// where scaling would break a tight layout).
public extension IDS {
    struct Icons {
        private static func scaled(_ size: CGFloat, relativeTo style: UIFont.TextStyle?) -> CGFloat {
            guard let style else { return size }
            return UIFontMetrics(forTextStyle: style).scaledValue(for: size)
        }

        public static func back(size: CGFloat = 24, color: Color = .primary, relativeTo style: UIFont.TextStyle? = nil) -> some View {
            let s = scaled(size, relativeTo: style)
            return IDSBackShape().stroke(color, style: StrokeStyle(lineWidth: 2.4 / 24 * s, lineCap: .round, lineJoin: .round))
                .frame(width: s, height: s)
        }
        public static func chevronRight(size: CGFloat = 24, color: Color = .primary, relativeTo style: UIFont.TextStyle? = nil) -> some View {
            let s = scaled(size, relativeTo: style)
            return IDSChevronRightShape().stroke(color, style: StrokeStyle(lineWidth: 2.4 / 24 * s, lineCap: .round, lineJoin: .round))
                .frame(width: s, height: s)
        }
        public static func close(size: CGFloat = 24, color: Color = .primary, relativeTo style: UIFont.TextStyle? = nil) -> some View {
            let s = scaled(size, relativeTo: style)
            return IDSCloseShape().stroke(color, style: StrokeStyle(lineWidth: 2.4 / 24 * s, lineCap: .round, lineJoin: .round))
                .frame(width: s, height: s)
        }
        public static func search(size: CGFloat = 24, color: Color = .primary, relativeTo style: UIFont.TextStyle? = nil) -> some View {
            let s = scaled(size, relativeTo: style)
            return IDSSearchShape().stroke(color, style: StrokeStyle(lineWidth: 2.4 / 24 * s, lineCap: .round, lineJoin: .round))
                .frame(width: s, height: s)
        }
        public static func add(size: CGFloat = 24, color: Color = .primary, relativeTo style: UIFont.TextStyle? = nil) -> some View {
            let s = scaled(size, relativeTo: style)
            return IDSAddShape().stroke(color, style: StrokeStyle(lineWidth: 2.4 / 24 * s, lineCap: .round, lineJoin: .round))
                .frame(width: s, height: s)
        }
        public static func star(size: CGFloat = 24, color: Color = .primary, relativeTo style: UIFont.TextStyle? = nil) -> some View {
            let s = scaled(size, relativeTo: style)
            return IDSStarShape().stroke(color, style: StrokeStyle(lineWidth: 2.4 / 24 * s, lineCap: .round, lineJoin: .round))
                .frame(width: s, height: s)
        }
        public static func send(size: CGFloat = 24, color: Color = .primary, relativeTo style: UIFont.TextStyle? = nil) -> some View {
            let s = scaled(size, relativeTo: style)
            return IDSSendShape().stroke(color, style: StrokeStyle(lineWidth: 2.4 / 24 * s, lineCap: .round, lineJoin: .round))
                .frame(width: s, height: s)
        }
        public static func bell(size: CGFloat = 24, color: Color = .primary, relativeTo style: UIFont.TextStyle? = nil) -> some View {
            let s = scaled(size, relativeTo: style)
            return IDSBellShape().stroke(color, style: StrokeStyle(lineWidth: 2.4 / 24 * s, lineCap: .round, lineJoin: .round))
                .frame(width: s, height: s)
        }
        public static func shieldCheck(size: CGFloat = 24, color: Color = .primary, relativeTo style: UIFont.TextStyle? = nil) -> some View {
            let s = scaled(size, relativeTo: style)
            return IDSShieldCheckShape().stroke(color, style: StrokeStyle(lineWidth: 2.4 / 24 * s, lineCap: .round, lineJoin: .round))
                .frame(width: s, height: s)
        }
        public static func eye(size: CGFloat = 24, color: Color = .primary, relativeTo style: UIFont.TextStyle? = nil) -> some View {
            let s = scaled(size, relativeTo: style)
            return IDSEyeShape().stroke(color, style: StrokeStyle(lineWidth: 2.4 / 24 * s, lineCap: .round, lineJoin: .round))
                .frame(width: s, height: s)
        }
        public static func eyeOff(size: CGFloat = 24, color: Color = .primary, relativeTo style: UIFont.TextStyle? = nil) -> some View {
            let s = scaled(size, relativeTo: style)
            return IDSEyeOffShape().stroke(color, style: StrokeStyle(lineWidth: 2.4 / 24 * s, lineCap: .round, lineJoin: .round))
                .frame(width: s, height: s)
        }
    }
}
