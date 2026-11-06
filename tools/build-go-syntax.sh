#!/usr/bin/env bash
# Offline Go parser, packaged as an executable lib*.so like librg.so (not loaded through JNI).
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "$0")/.." && pwd)"
SDK="${ANDROID_HOME:-$HOME/Android/Sdk}"
NDK="${ANDROID_NDK_HOME:-$(ls -d "$SDK"/ndk/* | sort -V | tail -1)}"
LLVM="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin"
export GOCACHE="${GOCACHE:-/tmp/wex-go-syntax-build-cache}"
export GOTOOLCHAIN=local
cd "$ROOT/tools/go-syntax"
gofmt -w main.go main_test.go
go test -v ./...
for spec in 'arm64 arm64-v8a aarch64-linux-android' 'amd64 x86_64 x86_64-linux-android'; do
    read -r arch abi target <<<"$spec"
    mkdir -p "$ROOT/app/src/main/jniLibs/$abi"
    GOOS=android GOARCH="$arch" CGO_ENABLED=1 CC="$LLVM/${target}28-clang" \
        go build -buildvcs=false -trimpath -buildmode=pie \
        -ldflags='-s -w -linkmode external -extldflags "-Wl,-z,max-page-size=16384"' \
        -o "$ROOT/app/src/main/jniLibs/$abi/libgosyntax.so" .
    "$LLVM/llvm-readelf" -lW "$ROOT/app/src/main/jniLibs/$abi/libgosyntax.so" | grep -E 'INTERP|LOAD'
done
mkdir -p "$ROOT/app/src/main/assets/licenses"
cp "$ROOT/tools/go-syntax/GO-LICENSE" "$ROOT/app/src/main/assets/licenses/go-syntax-GO-LICENSE.txt"
printf 'GO_SYNTAX_ANDROID_BUILD_OK: arm64-v8a, x86_64\n'
