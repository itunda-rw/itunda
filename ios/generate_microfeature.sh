#!/bin/bash
FEATURE_NAME=$1

if [ -z "$FEATURE_NAME" ]; then
    echo "Usage: $0 <FeatureName>"
    echo "Example: $0 Profile"
    exit 1
fi

# Convert first letter to uppercase for folder name convention
FEATURE_NAME="$(tr '[:lower:]' '[:upper:]' <<< ${FEATURE_NAME:0:1})${FEATURE_NAME:1}"

BASE_DIR="Features/$FEATURE_NAME"

mkdir -p "$BASE_DIR/Interface/Sources"
mkdir -p "$BASE_DIR/Sources"
mkdir -p "$BASE_DIR/Testing/Sources"
mkdir -p "$BASE_DIR/Tests"
mkdir -p "$BASE_DIR/Example/Sources"

# Add .keep files so git tracks the empty directories
touch "$BASE_DIR/Interface/Sources/.keep"
touch "$BASE_DIR/Sources/.keep"
touch "$BASE_DIR/Testing/Sources/.keep"
touch "$BASE_DIR/Tests/.keep"
touch "$BASE_DIR/Example/Sources/.keep"

echo "✅ iOS Microfeature '$FEATURE_NAME' generated successfully at $BASE_DIR"
