import os

def process_file(filepath):
    with open(filepath, 'r') as f:
        content = f.read()

    if 'id("com.android.library")' not in content:
        return

    if 'compileOptions' in content:
        return

    # Find the end of the android block
    # A simple way is to replace the android { ... } block's end.
    # Since they are simply formatted, we can search for the last '}' that closes the android block.
    # Or just replace `compileSdk = 34` with `compileSdk = 34` + the options.
    
    if 'compileSdk = 34' in content:
        insertion = """
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }"""
        new_content = content.replace('compileSdk = 34', 'compileSdk = 34' + insertion)
        with open(filepath, 'w') as f:
            f.write(new_content)
        print(f"Updated {filepath}")

for root, dirs, files in os.walk('.'):
    for name in files:
        if name == 'build.gradle.kts':
            process_file(os.path.join(root, name))
