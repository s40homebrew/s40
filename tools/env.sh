#!/usr/bin/env bash
# SPDX-License-Identifier: MIT
# Source this file from Bash; explicit environment settings always win.
S40_SDK_ROOT=${S40_SDK_ROOT:-$(CDPATH='' cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)}
if [[ -z ${JAVA8_HOME:-} ]]; then
    if [[ $(uname -s) == Darwin ]] && command -v /usr/libexec/java_home >/dev/null; then
        JAVA8_HOME=$(/usr/libexec/java_home -v 1.8 2>/dev/null || true)
    fi
    # java_home can return the old browser-plugin JRE. Require a complete JDK.
    for s40_jdk in "${JAVA8_HOME:-}" "${JAVA_HOME:-}" /Library/Java/JavaVirtualMachines/*/Contents/Home /usr/lib/jvm/*; do
        if [[ -x "$s40_jdk/bin/javac" && -x "$s40_jdk/bin/javap" ]] && "$s40_jdk/bin/java" -version 2>&1 | grep -q 'version "1\.8\.'; then
            JAVA8_HOME=$s40_jdk
            break
        fi
    done
    if [[ ! -x ${JAVA8_HOME:-}/bin/javap ]]; then
        JAVA8_HOME=/path/to/jdk8
    fi
fi
JAVA8_HOME=${JAVA8_HOME:-/path/to/jdk8}
LEGACY_JAVAC=${LEGACY_JAVAC:-$S40_SDK_ROOT/tools/.deps/legacy-javac.jar}
PROGUARD_JAR=${PROGUARD_JAR:-$S40_SDK_ROOT/tools/.deps/proguard-7.10.0/lib/proguard.jar}
SDK_CLASSES=${SDK_CLASSES:-$S40_SDK_ROOT/tools/.deps/S40-SDK-Classes/S40v6/classes/classes.zip}
if [[ -z ${LLVM:-} ]]; then
    if command -v llvm-objcopy >/dev/null; then
        LLVM=$(dirname "$(command -v llvm-objcopy)")
    elif [[ -x /opt/homebrew/opt/llvm/bin/llvm-objcopy ]]; then
        LLVM=/opt/homebrew/opt/llvm/bin
    else
        LLVM=/usr/bin
    fi
fi
CC=${CC:-$LLVM/clang}
LD=${LD:-$(command -v ld.lld || printf '%s' "$LLVM/ld.lld")}
ARM_GCC=${ARM_GCC:-$(command -v arm-none-eabi-gcc || true)}
export S40_SDK_ROOT JAVA8_HOME LEGACY_JAVAC PROGUARD_JAR SDK_CLASSES LLVM CC LD ARM_GCC
