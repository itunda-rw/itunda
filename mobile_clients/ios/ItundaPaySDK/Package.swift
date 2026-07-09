// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "ItundaPaySDK",
    platforms: [
        .iOS(.v15)
    ],
    products: [
        .library(
            name: "ItundaPaySDK",
            targets: ["ItundaPaySDK"]),
    ],
    targets: [
        .target(
            name: "ItundaPaySDK"),
        .testTarget(
            name: "ItundaPaySDKTests",
            dependencies: ["ItundaPaySDK"]),
    ]
)
