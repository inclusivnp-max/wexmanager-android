#!/usr/bin/env bash
# Сборка ripgrep (Rust) для Android: arm64-v8a и x86_64 → app/src/main/jniLibs/<abi>/librg.so.
# Файл называется lib*.so, чтобы установщик Android распаковал его в nativeLibraryDir —
# единственное место, откуда приложению (targetSdk 29+) разрешено запускать исполняемые файлы.
#
# Нужны: rustup с целями aarch64-linux-android и x86_64-linux-android, Android NDK r28+.
#   RUST_HOME=~/.local/share/wex-rust tools/build-ripgrep.sh
set -euo pipefail
RG_VERSION="${RG_VERSION:-15.2.0}"
API=28
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
RUST_HOME="${RUST_HOME:-$HOME/.local/share/wex-rust}"
export RUSTUP_HOME="$RUST_HOME/rustup" CARGO_HOME="$RUST_HOME/cargo" PATH="$RUST_HOME/cargo/bin:$PATH"
SDK="${ANDROID_HOME:-$HOME/Android/Sdk}"
NDK="${ANDROID_NDK_HOME:-$(ls -d "$SDK"/ndk/* | sort -V | tail -1)}"
LLVM="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin"
OUT="$ROOT/build/ripgrep"

export CARGO_PROFILE_RELEASE_LTO=true
export CARGO_PROFILE_RELEASE_CODEGEN_UNITS=1
export CARGO_PROFILE_RELEASE_STRIP=true
export CARGO_PROFILE_RELEASE_PANIC=abort

build() {
    local triple="$1" abi="$2" clang_prefix="$3"
    local upper; upper="$(echo "$triple" | tr 'a-z-' 'A-Z_')"
    export "CARGO_TARGET_${upper}_LINKER=$LLVM/${clang_prefix}${API}-clang"
    export "CC_${triple//-/_}=$LLVM/${clang_prefix}${API}-clang"
    export "AR_${triple//-/_}=$LLVM/llvm-ar"
    # Страницы 16 КБ (Android 15+): выравнивание сегментов ELF.
    export "CARGO_TARGET_${upper}_RUSTFLAGS=-C link-arg=-Wl,-z,max-page-size=16384"
    cargo install ripgrep --version "$RG_VERSION" --locked --target "$triple" --root "$OUT/$abi" --force
    mkdir -p "$ROOT/app/src/main/jniLibs/$abi"
    cp "$OUT/$abi/bin/rg" "$ROOT/app/src/main/jniLibs/$abi/librg.so"
    "$LLVM/llvm-readelf" -lW "$ROOT/app/src/main/jniLibs/$abi/librg.so" | awk '/LOAD/ {print "  LOAD align", $NF}' | head -2
    ls -la "$ROOT/app/src/main/jniLibs/$abi/librg.so"
}

build aarch64-linux-android arm64-v8a aarch64-linux-android
build x86_64-linux-android x86_64 x86_64-linux-android
