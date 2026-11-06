#!/usr/bin/env bash
# Сборка UnRAR (RARLAB, исходники freeware — см. license.txt в архиве исходников) для Android:
# arm64-v8a и x86_64 → app/src/main/jniLibs/<abi>/libunrar.so. Как и ripgrep, это исполняемый файл
# с именем lib*.so: установщик распаковывает его в nativeLibraryDir, откуда его можно запускать.
# Нужен для RAR5 (junrar читает только RAR4).
#
# «UnRAR source code may be used in any software to handle RAR archives without limitations free of
# charge, but cannot be used to develop RAR (WinRAR) compatible archiver and to re-create RAR
# compression algorithm, which is proprietary. Distribution of modified UnRAR source code in separate
# form or as a part of other software is permitted, provided that full text of this paragraph, starting
# from "UnRAR source code" words, is included in license, or in documentation if license is not
# available, and in source code comments of resulting package.»
set -euo pipefail
UNRAR_VERSION="${UNRAR_VERSION:-7.2.3}"
UNRAR_SHA256="${UNRAR_SHA256:-3995af0aa32b1505a566da053725551a1f0698dc42b2fdf7ba7d65db0d004e33}"
API=28
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SDK="${ANDROID_HOME:-$HOME/Android/Sdk}"
NDK="${ANDROID_NDK_HOME:-$(ls -d "$SDK"/ndk/* | sort -V | tail -1)}"
LLVM="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin"
OUT="$ROOT/build/unrar"
mkdir -p "$OUT"
TARBALL="$OUT/unrarsrc-$UNRAR_VERSION.tar.gz"
[ -f "$TARBALL" ] || curl -sSfL -o "$TARBALL" "https://www.rarlab.com/rar/unrarsrc-$UNRAR_VERSION.tar.gz"
echo "$UNRAR_SHA256  $TARBALL" | sha256sum -c --quiet

build() {
    local abi="$1" prefix="$2"
    local src="$OUT/src-$abi"
    rm -rf "$src" && mkdir -p "$src" && tar xzf "$TARBALL" -C "$src" --strip-components=1
    # -z max-page-size: сегменты по 16 КБ для устройств с такими страницами (Android 15+).
    # getpass() в bionic нет; пароль всегда передаётся ключом -p, интерактивного ввода не бывает.
    make -C "$src" -f makefile unrar -j"$(nproc)" \
        CXX="$LLVM/${prefix}${API}-clang++" \
        CXXFLAGS="-O2 -std=c++11 -fPIE '-Dgetpass(p)=\"\"' -Wno-logical-op-parentheses -Wno-switch -Wno-dangling-else -Wno-deprecated-declarations" \
        LDFLAGS="-pie -static-libstdc++ -Wl,-z,max-page-size=16384 -Wl,--gc-sections" \
        STRIP="$LLVM/llvm-strip" >/dev/null
    "$LLVM/llvm-strip" "$src/unrar"
    mkdir -p "$ROOT/app/src/main/jniLibs/$abi"
    cp "$src/unrar" "$ROOT/app/src/main/jniLibs/$abi/libunrar.so"
    echo "$abi: $(stat -c %s "$src/unrar") байт"
}
build arm64-v8a aarch64-linux-android
build x86_64 x86_64-linux-android
