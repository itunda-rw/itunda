//
//  ItundaFaceFlags5.swift
//  itundaface Flags -- phase 9 batch 5, iOS port of the same batch shipped to
//  Android/bank-mfe same session. See ItundaFaceFlags.swift's own doc comment for
//  the full real sourcing/extraction-technique story (not repeated here). New file
//  since batch 4 already used its own headroom.
//
//  4 real flags, deliberately smaller than prior batches: Qatar (real, substantial --
//  Qatar Airways/Qatari investors acquired 60% of Bugesera International Airport),
//  Israel (real embassy in Kigali, bilateral education/innovation MoUs signed July
//  2026, agriculture cooperation since 2012), Pakistan (real bilateral cooperation
//  agreement, 2026), Russia (real 2026 nuclear-cooperation agreement, real
//  fertilizer/grain trade). Saudi Arabia and Brazil were both researched as real
//  candidates too but DROPPED after a complexity check: Brazil's flattened source
//  has ~295 paths (a real starfield + banner-text illustration, nearly 3x the
//  previous worst case) and picosvg couldn't even parse a percentage-based gradient
//  in its source; Saudi Arabia's real Shahada calligraphy flattens to only 2 <path>
//  elements by path COUNT, but one single path's `d` attribute is ~36,000
//  characters -- path count alone doesn't capture true complexity when same-fill
//  shapes merge into one path. Both are real, well-grounded candidates for a
//  future DEDICATED single-flag file/batch, not silently dropped.
//

import SwiftUI
import CoreDesignSystem

private let flagQatarShapes: [ItundaFaceShape] = [
    .filledPath(d: "M6,50.08 L122,50.08 L122,77.92 L6,77.92 L6,50.08 Z", color: Color(hex: 0x8D1B3D)),
    .filledPath(d: "M40.03,77.92 L6,77.92 L6,50.08 L40.03,50.08 L49.31,51.63 L40.03,53.17 L49.31,54.72 L40.03,56.27 L49.31,57.81 L40.03,59.36 L49.31,60.91 L40.03,62.45 L49.31,64 L40.03,65.55 L49.31,67.09 L40.03,68.64 L49.31,70.19 L40.03,71.73 L49.31,73.28 L40.03,74.83 L49.31,76.37 Z", color: Color(hex: 0xFFFFFF)),
]

private let flagIsraelShapes: [ItundaFaceShape] = [
    .filledPath(d: "M6,21.82 L122,21.82 L122,106.18 L6,106.18 L6,21.82 Z", color: Color(hex: 0xFFFFFF)),
    .filledPath(d: "M6,29.73 L122,29.73 L122,42.91 L6,42.91 L6,29.73 Z", color: Color(hex: 0x0038B8)),
    .filledPath(d: "M6,85.09 L122,85.09 L122,98.27 L6,98.27 L6,85.09 Z", color: Color(hex: 0x0038B8)),
    .filledPath(d: "M64,45.73 L79.82,73.13 L48.18,73.13 L62.74,47.91 Z M64,51.53 L53.2,70.23 L74.8,70.23 L64,51.53 Z", color: Color(hex: 0x0038B8)),
    .filledPath(d: "M64,82.27 L48.18,54.87 L79.82,54.87 L65.26,80.09 Z M64,76.47 L74.8,57.77 L53.2,57.77 L64,76.47 Z", color: Color(hex: 0x0038B8)),
]

private let flagPakistanShapes: [ItundaFaceShape] = [
    .filledPath(d: "M6,25.33 L122,25.33 L122,102.67 L6,102.67 L6,25.33 Z", color: Color(hex: 0xFFFFFF)),
    .filledPath(d: "M35,25.33 L122,25.33 L122,102.67 L35,102.67 L35,25.33 Z", color: Color(hex: 0x01411C)),
    .filledPath(d: "M101.7,64 A23.2,23.2 0 1 1 55.3,64 A23.2,23.2 0 1 1 101.7,64 Z", color: Color(hex: 0xFFFFFF)),
    .filledPath(d: "M100.33,44.6 C108.13,53.38 107.34,66.82 98.56,74.62 C89.78,82.43 76.34,81.64 68.54,72.86 C60.73,64.08 61.52,50.64 70.3,42.83 C79.08,35.03 92.52,35.82 100.33,44.6 Z", color: Color(hex: 0x01411C)),
    .filledPath(d: "M86.96,46.64 L96.73,57.63 L82.36,54.48 L95.84,48.59 L88.4,61.28 Z", color: Color(hex: 0xFFFFFF)),
]

private let flagRussiaShapes: [ItundaFaceShape] = [
    .filledPath(d: "M6,25.33 L122,25.33 L122,64 L6,64 L6,25.33 Z", color: Color(hex: 0xFFFFFF)),
    .filledPath(d: "M6,64 L122,64 L122,102.67 L6,102.67 L6,64 Z", color: Color(hex: 0xD52B1E)),
    .filledPath(d: "M6,51.11 L122,51.11 L122,76.89 L6,76.89 L6,51.11 Z", color: Color(hex: 0x0039A6)),
]

struct FlagQatar: View { var size: CGFloat = 24; var body: some View { ItundaFaceGlyphCanvas(size: size, viewBoxSize: 128, shapes: flagQatarShapes) } }
struct FlagIsrael: View { var size: CGFloat = 24; var body: some View { ItundaFaceGlyphCanvas(size: size, viewBoxSize: 128, shapes: flagIsraelShapes) } }
struct FlagPakistan: View { var size: CGFloat = 24; var body: some View { ItundaFaceGlyphCanvas(size: size, viewBoxSize: 128, shapes: flagPakistanShapes) } }
struct FlagRussia: View { var size: CGFloat = 24; var body: some View { ItundaFaceGlyphCanvas(size: size, viewBoxSize: 128, shapes: flagRussiaShapes) } }
