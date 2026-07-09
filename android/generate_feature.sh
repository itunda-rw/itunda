#!/bin/bash
FEATURE_NAME=$1

if [ -z "$FEATURE_NAME" ]; then
    echo "Usage: $0 <feature_name>"
    echo "Example: $0 profile"
    exit 1
fi

# Convert to lowercase
FEATURE_NAME_LOWER=$(echo "$FEATURE_NAME" | tr '[:upper:]' '[:lower:]')

BASE_DIR="features/$FEATURE_NAME_LOWER"

# Create API Module
mkdir -p "$BASE_DIR/api/src/main/java/rw/itunda/feature/${FEATURE_NAME_LOWER}/api"
cat <<EOF > "$BASE_DIR/api/build.gradle.kts"
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "rw.itunda.feature.${FEATURE_NAME_LOWER}.api"
    compileSdk = 34
}
EOF

# Create Impl Module
mkdir -p "$BASE_DIR/impl/src/main/java/rw/itunda/feature/${FEATURE_NAME_LOWER}/impl"
cat <<EOF > "$BASE_DIR/impl/build.gradle.kts"
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "rw.itunda.feature.${FEATURE_NAME_LOWER}.impl"
    compileSdk = 34
}

dependencies {
    implementation(project(":features:${FEATURE_NAME_LOWER}:api"))
    implementation(project(":core:designsystem"))
}
EOF

# Create Testing Module
mkdir -p "$BASE_DIR/testing/src/main/java/rw/itunda/feature/${FEATURE_NAME_LOWER}/testing"
cat <<EOF > "$BASE_DIR/testing/build.gradle.kts"
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "rw.itunda.feature.${FEATURE_NAME_LOWER}.testing"
    compileSdk = 34
}

dependencies {
    implementation(project(":features:${FEATURE_NAME_LOWER}:api"))
}
EOF

echo "✅ Android feature '$FEATURE_NAME_LOWER' (api, impl, testing) generated successfully at $BASE_DIR"
