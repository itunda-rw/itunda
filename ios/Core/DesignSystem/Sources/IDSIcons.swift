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

/// Real drop-in replacement for `Image(systemName: "chevron.left").font(...)
/// .foregroundColor(...)`-shaped call sites -- `size`/`color` cover the real
/// variance seen across existing call sites (different `.font(.system(size:))`
/// values, different `.foregroundColor`s), stroke width scales proportionally with
/// size to keep the same real 2.4/24 ratio web/Android use at any requested size.
public extension IDS {
    struct Icons {
        public static func back(size: CGFloat = 24, color: Color = .primary) -> some View {
            IDSBackShape().stroke(color, style: StrokeStyle(lineWidth: 2.4 / 24 * size, lineCap: .round, lineJoin: .round))
                .frame(width: size, height: size)
        }
        public static func chevronRight(size: CGFloat = 24, color: Color = .primary) -> some View {
            IDSChevronRightShape().stroke(color, style: StrokeStyle(lineWidth: 2.4 / 24 * size, lineCap: .round, lineJoin: .round))
                .frame(width: size, height: size)
        }
        public static func close(size: CGFloat = 24, color: Color = .primary) -> some View {
            IDSCloseShape().stroke(color, style: StrokeStyle(lineWidth: 2.4 / 24 * size, lineCap: .round, lineJoin: .round))
                .frame(width: size, height: size)
        }
        public static func search(size: CGFloat = 24, color: Color = .primary) -> some View {
            IDSSearchShape().stroke(color, style: StrokeStyle(lineWidth: 2.4 / 24 * size, lineCap: .round, lineJoin: .round))
                .frame(width: size, height: size)
        }
        public static func add(size: CGFloat = 24, color: Color = .primary) -> some View {
            IDSAddShape().stroke(color, style: StrokeStyle(lineWidth: 2.4 / 24 * size, lineCap: .round, lineJoin: .round))
                .frame(width: size, height: size)
        }
    }
}
