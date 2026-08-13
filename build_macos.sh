#!/bin/bash
set -e

echo "=== 1. Cleaning up old builds ==="
rm -rf ShutupStudy.app
mkdir -p ShutupStudy.app/Contents/MacOS
mkdir -p ShutupStudy.app/Contents/Resources

echo "=== 2. Copying Info.plist configuration & resources ==="
cp macos/Info.plist ShutupStudy.app/Contents/Info.plist
cp macos/AppIcon.icns ShutupStudy.app/Contents/Resources/AppIcon.icns

echo "=== 3. Compiling Native Swift Code (Ventura 13.0+) ==="
ARCH=$(uname -m)
SDK_PATH=$(xcrun --show-sdk-path --sdk macosx)

echo "Target architecture: $ARCH"
swiftc \
  -o ShutupStudy.app/Contents/MacOS/ShutupStudy \
  macos/ShutupStudyApp.swift \
  macos/Models.swift \
  macos/FirestoreClient.swift \
  macos/AudioSynth.swift \
  macos/Views.swift \
  macos/SessionViewModel.swift \
  -sdk "$SDK_PATH" \
  -target "$ARCH-apple-macos13.0"

echo "=== 4. Launching Shutup & Study Native macOS App ==="
open ShutupStudy.app
echo "Native App launched successfully!"
