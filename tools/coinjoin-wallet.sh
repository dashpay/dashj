#!/bin/bash
#
# coinjoin-wallet.sh — create a Dash wallet, show its recovery phrase and a
# deposit address, sync the block chain and mix the funds with CoinJoin.
#
# Usage:
#   ./coinjoin-wallet.sh <data-dir> [dump] [--debuglog]
#
# With "dump" as the second argument, only the wallet dump is printed and the
# script exits (no sync or mix).
#
# With --debuglog, WalletTool's debug logging is enabled for the sync and mix
# steps; without it they run quietly.
#
# All files (wallet, block chain, masternode lists, CoinJoin reports) are
# stored in <data-dir>. Optional environment variables:
#
#   NET      network: TEST (default), MAIN, REGTEST or DEVNET
#   ROUNDS   number of mixing rounds (default 16)
#   SESSIONS number of parallel mixing sessions (default 4)
#   AMOUNT   amount to mix in DASH (default: entire wallet balance)
#
# See tools/COINJOIN-WALLET.md for full instructions.

set -euo pipefail

# Pull option flags out of the argument list.
DEBUGLOG=""
ARGS=()
for arg in "$@"; do
    case "$arg" in
        --debuglog) DEBUGLOG="--debuglog" ;;
        *) ARGS+=("$arg") ;;
    esac
done
set -- "${ARGS[@]+"${ARGS[@]}"}"

if [ $# -lt 1 ]; then
    echo "Usage: $0 <data-dir> [dump] [--debuglog]" >&2
    exit 1
fi

ACTION="${2:-}"
if [ -n "$ACTION" ] && [ "$ACTION" != "dump" ]; then
    echo "Error: unknown action '$ACTION' (only 'dump' is supported)" >&2
    exit 1
fi

case "$1" in
    *" "*)
        echo "Error: the data directory path must not contain spaces." >&2
        exit 1
        ;;
esac

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

mkdir -p "$1"
DATA_DIR="$(cd "$1" && pwd)"

NET="${NET:-TEST}"
ROUNDS="${ROUNDS:-16}"
SESSIONS="${SESSIONS:-4}"
AMOUNT="${AMOUNT:-}"

WALLET="coinjoin-$(echo "$NET" | tr '[:upper:]' '[:lower:]').wallet"
MNEMONIC_FILE="$DATA_DIR/${WALLET%.wallet}-recovery-phrase.txt"

wallet_tool() {
    "$ROOT_DIR/gradlew" -q --console=plain -p "$ROOT_DIR" :dashj-tools:wallet_tool \
        "-PappArgs=$1" "-PworkDir=$DATA_DIR"
}

# WalletTool cannot run without the BLS native library (libdashjbls); there is
# no Java fallback. Build it from the contrib submodule if it is missing.
# The x11 native library is optional (pure-Java fallback) but much faster, so
# it is built too when missing — failure to build it is only a warning.
BLS_LIB_DIR="$ROOT_DIR/contrib/dashj-bls/bls/target/cmake"
X11_LIB_DIR="$ROOT_DIR/contrib/x11/build"

check_native_libs() {
    if ls "$BLS_LIB_DIR"/libdashjbls.* >/dev/null 2>&1; then
        return
    fi
    echo "==> BLS native library not found, building it (this only happens once)..."
    if [ ! -f "$ROOT_DIR/contrib/dashj-bls/pom.xml" ]; then
        echo "==> Initializing the contrib/dashj-bls git submodule..."
        git -C "$ROOT_DIR" submodule update --init --recursive
    fi
    if ! command -v mvn >/dev/null 2>&1; then
        echo "Error: Maven (mvn) is required to build the BLS native library." >&2
        echo "Install Maven and CMake, then re-run, or build it manually:" >&2
        echo "    cd $ROOT_DIR/contrib/dashj-bls && mvn package -DskipTests" >&2
        exit 1
    fi
    (cd "$ROOT_DIR/contrib/dashj-bls" && mvn -q package -DskipTests)
    if ! ls "$BLS_LIB_DIR"/libdashjbls.* >/dev/null 2>&1; then
        echo "Error: the build finished but $BLS_LIB_DIR/libdashjbls.* is still missing." >&2
        echo "Check that CMake and a C++ toolchain are installed, then build manually:" >&2
        echo "    cd $ROOT_DIR/contrib/dashj-bls && mvn package -DskipTests" >&2
        exit 1
    fi
    echo "==> BLS native library built successfully."
}

check_x11_lib() {
    if ls "$X11_LIB_DIR"/libx11.* >/dev/null 2>&1; then
        return
    fi
    echo "==> x11 native library not found, building it (this only happens once)..."
    if ! command -v cmake >/dev/null 2>&1; then
        echo "Warning: CMake not found, skipping the x11 build." >&2
        echo "Mixing will still work but hashing will use the slower Java fallback." >&2
        return
    fi
    if (mkdir -p "$X11_LIB_DIR" && cd "$X11_LIB_DIR" && cmake .. && cmake --build .) \
        && ls "$X11_LIB_DIR"/libx11.* >/dev/null 2>&1; then
        echo "==> x11 native library built successfully."
    else
        echo "Warning: the x11 build failed. Mixing will still work but hashing" >&2
        echo "will use the slower Java fallback. To build it manually:" >&2
        echo "    cd $ROOT_DIR/contrib/x11 && mkdir -p build && cd build && cmake .. && cmake --build ." >&2
    fi
}

save_mnemonic() {
    MNEMONIC="$(wallet_tool "dump --net=$NET --wallet=$WALLET --dump-privkeys" \
        | grep -m1 '^Seed as words:' | sed 's/^Seed as words:[[:space:]]*//')"
    (umask 077 && echo "$MNEMONIC" > "$MNEMONIC_FILE")
    echo "Recovery phrase saved to $MNEMONIC_FILE"
}

echo "Data directory: $DATA_DIR"
echo "Network:        $NET"
echo "Wallet file:    $WALLET"
echo

check_native_libs
check_x11_lib

# Dump-only mode: print the wallet dump and exit.
if [ "$ACTION" = "dump" ]; then
    if [ ! -f "$DATA_DIR/$WALLET" ]; then
        echo "Error: wallet $DATA_DIR/$WALLET does not exist" >&2
        exit 1
    fi
    wallet_tool "dump --net=$NET --wallet=$WALLET --rounds=$ROUNDS"
    exit 0
fi

# If the wallet already exists, skip straight to sync and mix.
if [ -f "$DATA_DIR/$WALLET" ]; then
    echo "==> Wallet already exists, going straight to sync and mix."
    if [ ! -f "$MNEMONIC_FILE" ]; then
        echo "==> Recovery phrase file is missing, creating it..."
        save_mnemonic
    fi
else
    # 1. Create the wallet.
    echo "==> Creating wallet..."
    wallet_tool "create --net=$NET --wallet=$WALLET"

    # 2. Show the mnemonic seed and save it to a file.
    echo
    echo "==> Recovery phrase (mnemonic seed):"
    save_mnemonic
    echo
    echo "    $MNEMONIC"
    echo
    echo "    WRITE THESE WORDS DOWN AND KEEP THEM SAFE."
    echo "    They are the only way to restore the wallet if the file is lost."

    # 3. Show a deposit address.
    echo
    echo "==> Deposit address:"
    ADDRESS="$(wallet_tool "current-receive-addr --net=$NET --wallet=$WALLET" | tail -n1)"
    echo
    echo "    $ADDRESS"
    echo
    echo "Send the funds you want to mix to the address above."
    read -r -p "Press Enter when the funds have been sent to start syncing and mixing... "
fi

# 4. Sync the block chain.
echo
echo "==> Syncing block chain (this can take a while on first run)..."
wallet_tool "sync --net=$NET --wallet=$WALLET${DEBUGLOG:+ $DEBUGLOG}"

# 5. Mix.
echo
echo "==> Mixing with $ROUNDS rounds and $SESSIONS sessions..."
MIX_ARGS="mix --net=$NET --wallet=$WALLET --rounds=$ROUNDS --sessions=$SESSIONS${DEBUGLOG:+ $DEBUGLOG}"
if [ -n "$AMOUNT" ]; then
    MIX_ARGS="$MIX_ARGS --amount=$AMOUNT"
fi
wallet_tool "$MIX_ARGS"

echo
echo "==> Done. A CoinJoin report was written to $DATA_DIR."
