//
//  ItundaFaceServices.swift
//  itundaface Seedling/Refresh/Receipt/Shield -- iOS port of Android's
//  core/designsystem/itundaface/ItundaFaceServices.kt (2026-08-26 batch).
//  Same real Noto Emoji sourcing as ItundaFaceWork.swift's own doc comment.
//  Shield drops ~40 decorative stitching-dot circles invisible at icon
//  size; receipt's one dashed perforation line simplified to solid -- see
//  the Android source's own doc comment for the full reasoning.
//

import SwiftUI

private let seedlingShapes: [ItundaFaceShape] = [
    .filledPath(d: "M67.47,48.2l-1.11,18.48c0,0,3.76,10.64,2.71,25.45c-1.01,14.27-4.8,21.49-5.84,23.99c-1.04,2.5-3.96,5.63,0,6.88s10.64,0.63,13.56-0.42c2.92-1.04,4.37-6.84,4.8-9.18c1.05-5.79,1.63-16.74-0.5-27.54C79.46,77.56,76,63.33,76,63.33L67.47,48.2z", color: Color(hex: 0x99C23B)),
    .filledPath(d: "M66.78,45.4c0,0,4.28-13.43,14.12-23.62c9.43-9.76,19.06-12.96,26.78-13.92c8.51-1.06,11.98-0.95,13.44,0.1s0.94,2.7,0.97,4.16c0.19,10.44,0.58,23.97-11.7,36.35C97.28,61.67,87.94,64.04,77.71,65.09c-10.22,1.04-9.06-3.62-9.06-3.62L66.78,45.4z", color: Color(hex: 0x99C23B)),
    .filledPath(d: "M2.98,37.05c-0.37,1.83,9.29,23.31,27.95,30.07c20.09,7.29,39.03,0,39.03,0l1.56-19.34l-4.75-2.38c0,0-10.07-11.11-21.26-15.21c-11.6-4.25-24.71-4.58-33.64,0.19C6.27,33.38,3.18,36.08,2.98,37.05z", color: Color(hex: 0x99C23B)),
    .filledPath(d: "M86.68,30.68c-9.3,11.02-13.99,22.13-14.48,25.03s1.12,2.78,2.03,1.55c1.64-2.22,7.35-12.37,15.85-22.04c7.36-8.36,18.56-17.4,20.2-19.33c1.02-1.2-0.1-2.71-1.64-2.03C107.1,14.53,96.19,19.41,86.68,30.68z", color: Color(hex: 0xE6DC9F)),
    .filledPath(d: "M41.43,41c-13.6-4.68-23.55-4.05-25.58-3.37c-2.03,0.68-1.93,2.9,0.48,3.19s12.24,1.27,24.56,5.7c13.15,4.74,22.52,11.89,24.17,12.86c1.64,0.97,2.71-0.97,1.26-2.71C65.21,55.36,56.43,46.16,41.43,41z", color: Color(hex: 0xE6DC9F)),
]

private let refreshCardShapes: [ItundaFaceShape] = [
    .filledPath(d: "M116,4H12c-4.42,0-8,3.58-8,8v104c0,4.42,3.58,8,8,8h104c4.42,0,8-3.58,8-8V12C124,7.58,120.42,4,116,4z", color: Color(hex: 0x427687)),
    .filledPath(d: "M109.7,4H11.5C7.37,4.03,4.03,7.37,4,11.5v97.9c-0.01,4.14,3.34,7.49,7.48,7.5c0.01,0,0.01,0,0.02,0h98.1c4.14,0.01,7.49-3.34,7.5-7.48c0-0.01,0-0.01,0-0.02V11.5c0.09-4.05-3.13-7.41-7.18-7.5C109.85,4,109.77,4,109.7,4z", color: Color(hex: 0x8CAFBF)),
    // Real flat-color resolution of a 0.5-opacity B4E1ED-over-8CAFBF fill.
    .filledPath(d: "M31.6,14.4c-0.09-0.38-0.22-0.75-0.4-1.1c-2.4-5.6-13-2.8-16.1,1c-2.9,3.5-2.6,8.8-3,13c-1,11.9,6.5,0.5,10.6-3.3C25.6,21.2,32.7,19.4,31.6,14.4z", color: Color(hex: 0xA0C8D6)),
    .filledPath(
        d: "M40.35,64.73c1.58,1.39,3.99,1.24,5.38-0.34c0.62-0.7,0.96-1.61,0.95-2.54V48.43c0-1.06,0.85-1.91,1.91-1.92h32.6c4.6,0,11.12,1.44,11.5,11.51l14.19,12.75l0,0c0.77-2.91,1.15-5.91,1.12-8.92c0-17-12-30.68-26.84-30.68H48.59c-1.05-0.01-1.9-0.86-1.91-1.91V15.83c-0.02-2.1-1.73-3.8-3.84-3.78c-0.92,0.01-1.8,0.34-2.49,0.95l-23,23c-1.59,1.41-1.73,3.85-0.31,5.44c0.1,0.11,0.2,0.22,0.31,0.31L40.35,64.73z",
        color: Color(hex: 0xFAFAFA)
    ),
    .filledPath(
        d: "M83.68,59c-1.57-1.39-3.97-1.25-5.36,0.32c-0.62,0.7-0.96,1.62-0.96,2.56v13.39c-0.01,1.06-0.86,1.91-1.92,1.92h-32.6c-4.6,0-11-1.44-11.5-11.5L17.15,52.94l0,0c-0.77,2.91-1.16,5.9-1.15,8.91c0,17,12,30.68,26.84,30.68h32.6c1.06,0.01,1.91,0.86,1.92,1.92v13.42c0,2.1,1.7,3.81,3.81,3.81c0.92,0,1.82-0.33,2.51-0.94l23-23c1.59-1.4,1.74-3.82,0.34-5.41c-0.11-0.12-0.22-0.24-0.34-0.34L83.68,59z",
        color: Color(hex: 0xFAFAFA)
    ),
]

private let receiptShapes: [ItundaFaceShape] = [
    .filledPath(
        d: "M115.12,3.04l-0.25,0c0,0,0,0,0,0c-0.08,0-1.49,0.03-3.7,2.4l-5.01,6.1l-6.5-7.9l-6.49,7.9l-6.5-7.9l-6.49,7.9l-6.5-7.9l-6.49,7.9l-6.49-7.9l-6.49,7.9l-6.49-7.9l-6.49,7.9l-6.49-7.9l-6.49,7.89l-3.38-4.34c-3.44-4.13-5.59-3.74-5.68-3.73l-0.2,0.04l0.1,103.08l-5.39,5.87l-5.76-6.91c0,0-3.48,5.03,1.01,11.83c3.59,5.45,15.05,4.89,19.73,4.89l67.97,0.06c11.55-0.07,14.7-2.51,16.94-12.86c1.54-7.13,1.53-17.64,1.53-21.6L115.12,3.04z " +
            "M70.69,105.55l-6.33,6.91l-6.33-6.91l-6.33,6.91l-6.33-6.91l-6.33,6.91l-6.33-6.91l-6.33,6.91l-3.69-4.03l-0.09-98.17l5.66,6.88l6.49-7.9l6.49,7.9l6.49-7.9l6.49,7.9l6.49-7.9l6.49,7.9l6.49-7.9l6.5,7.9l6.49-7.9l6.5,7.9l6.49-7.9l6.5,7.9l5.37-6.54l-0.02,76.76l0,0.9c0.01,4.18,0.05,13.98-1.45,20.44c-1.54,6.69-3.97,9.39-8.95,9.97c-1.48,0.17-9.25,0.36-16.45-13.1c-0.12-0.23-0.31-0.18-0.35-0.17c-0.01,0-0.02,0.01-0.03,0.01c-0.07-0.3-0.11-0.48-0.11-0.48l-7.15,7.52L70.69,105.55z",
        color: Color(hex: 0x6FBFF0)
    ),
    .filledPath(
        d: "M106.15,14.34l-6.49-7.9l-6.49,7.9l-6.49-7.9l-6.49,7.9l-6.49-7.9l-6.49,7.9l-6.49-7.9l-6.49,7.9l-6.49-7.9l-6.49,7.9l-6.49-7.9l-6.49,7.9l-6.79-8.26h-0.67l0.11,116.04l79.08,0.12c16.68,0,13.34-34.81,13.34-34.81l0.02-81.36h-0.38L106.15,14.34z",
        color: Color(hex: 0xFFFFFF)
    ),
    .filledCircle(cx: 65.6, cy: 31.23, r: 8, color: Color(hex: 0x94D1E0)),
    .strokedLine(x1: 32.41, y1: 47.62, x2: 70.89, y2: 47.62, color: Color(hex: 0x82AEC0), width: 4),
    .strokedLine(x1: 88.44, y1: 47.62, x2: 100.13, y2: 47.62, color: Color(hex: 0x82AEC0), width: 4),
    .strokedLine(x1: 88.44, y1: 60.76, x2: 100.13, y2: 60.76, color: Color(hex: 0x82AEC0), width: 4),
    .strokedLine(x1: 59.32, y1: 60.76, x2: 69.89, y2: 60.76, color: Color(hex: 0x82AEC0), width: 4),
    .strokedLine(x1: 32.41, y1: 60.76, x2: 50.03, y2: 60.76, color: Color(hex: 0x82AEC0), width: 4),
    .strokedLine(x1: 82.12, y1: 97.94, x2: 100.13, y2: 97.94, color: Color(hex: 0x82AEC0), width: 4),
    .strokedLine(x1: 88.44, y1: 73.9, x2: 100.13, y2: 73.9, color: Color(hex: 0x82AEC0), width: 4),
    .strokedLine(x1: 32.41, y1: 73.9, x2: 63.57, y2: 73.9, color: Color(hex: 0x82AEC0), width: 4),
    .strokedLine(x1: 32.81, y1: 87.04, x2: 98.95, y2: 87.04, color: Color(hex: 0x82AEC0), width: 4),
]

private let shieldEmojiShapes: [ItundaFaceShape] = [
    .filledPath(d: "M16.77,19.75c-1,0-1.7,0.8-1.7,1.8v0.1c-1.5,25.91,3.03,59.27,24.01,83.52c12.1,14.7,23.91,18.53,24.51,18.73c0,0,0.28,0.09,0.54,0.09s0.57-0.1,0.57-0.1c0.5-0.2,12.48-4.02,24.49-18.72c19.91-24.21,24.01-58.82,24.01-83.52v-0.1c0-1-0.8-1.8-1.7-1.8c-0.3,0-29.11-1-46.01-15.3l0,0c-0.7-0.6-1.7-0.6-2.4,0C45.98,18.65,17.07,19.65,16.77,19.75z", color: Color(hex: 0xB0BEC5)),
    .filledPath(d: "M111.49,19.75c-0.3,0-29.11-1-46.01-15.3C65.08,4.1,64.65,4,64.24,4c-0.03,0-0.07,0-0.11,0v120c0.27-0.01,0.62-0.11,0.62-0.12c0.6-0.25,12.44-4,24.44-18.7c19.91-24.21,24.01-58.82,24.01-83.52v-0.1C113.2,20.55,112.4,19.75,111.49,19.75z", color: Color(hex: 0x84B0C1)),
    .filledPath(d: "M26.33,28.31c-0.82,0-1.02,1.02-1.02,1.74v0.1c0,19.72,3.06,47.4,19,66.71c9.6,11.75,19,14.81,19.41,14.91l0.41,0.1l0.41-0.1c0.41-0.1,9.81-3.17,19.41-14.91c15.94-19.31,19-46.89,19-66.71v-0.1c0-0.82-0.41-1.43-1.23-1.43h0.1c-0.2,0-23.19-0.82-36.67-12.16l0,0c-1.19-0.98-1.94-0.2-1.94-0.2C49.63,27.6,26.64,28.31,26.33,28.31L26.33,28.31z", color: Color(hex: 0x2F7889)),
    .filledPath(d: "M29.18,30.07c-0.76,0-0.94,0.96-0.94,1.64v0.1c0,18.57,2.83,44.65,17.57,62.84c8.88,11.07,17.57,13.95,17.95,14.05l0.38,0.1l0.38-0.1c0.38-0.1,9.07-2.98,17.95-14.05c14.73-18.19,17.57-44.17,17.57-62.84v-0.1c0-0.77-0.38-1.35-1.13-1.35h0.09c-0.19,0-21.44-0.77-33.91-11.45l0,0c-0.94-0.95-1.79-0.19-1.79-0.19C50.72,29.4,29.47,30.07,29.18,30.07L29.18,30.07z", color: Color(hex: 0xC9E3E6)),
    .filledPath(d: "M98.89,30.36h0.09c-0.19,0-21.44-0.77-33.91-11.45c-0.34-0.34-0.66-0.46-0.94-0.47v90.35l0.38-0.1c0.38-0.1,9.07-2.98,17.95-14.05c14.73-18.19,17.57-44.17,17.57-62.84v-0.1C100.02,30.94,99.65,30.36,98.89,30.36z", color: Color(hex: 0xB0BEC5)),
]

public struct SeedlingGlyph: View {
    public var size: CGFloat = 24
    public init(size: CGFloat = 24) { self.size = size }
    public var body: some View { ItundaFaceGlyphCanvas(size: size, viewBoxSize: 128, shapes: seedlingShapes) }
}

public struct RefreshCardGlyph: View {
    public var size: CGFloat = 24
    public init(size: CGFloat = 24) { self.size = size }
    public var body: some View { ItundaFaceGlyphCanvas(size: size, viewBoxSize: 128, shapes: refreshCardShapes) }
}

public struct ReceiptGlyph: View {
    public var size: CGFloat = 24
    public init(size: CGFloat = 24) { self.size = size }
    public var body: some View { ItundaFaceGlyphCanvas(size: size, viewBoxSize: 128, shapes: receiptShapes) }
}

public struct ShieldEmojiGlyph: View {
    public var size: CGFloat = 24
    public init(size: CGFloat = 24) { self.size = size }
    public var body: some View { ItundaFaceGlyphCanvas(size: size, viewBoxSize: 128, shapes: shieldEmojiShapes) }
}
