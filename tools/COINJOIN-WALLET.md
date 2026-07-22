# CoinJoin wallet quick start

`coinjoin-wallet.sh` uses `WalletTool` to walk through a complete CoinJoin
mixing session:

1. Create a new wallet
2. Show the recovery phrase (mnemonic seed)
3. Show a deposit address
4. Sync the block chain
5. Mix the funds (16 rounds by default)

Everything the tool produces — the wallet file, the SPV block chain
(`testnet.chain`), masternode list files (`mnlistdiff-*.dat`, `qrinfo-*.dat`)
and the CoinJoin report (`coinjoin-report-*.txt`) — is stored in a folder you
choose, so the repository stays clean and separate wallets stay separate.

## Requirements

- JDK 11 (the build itself targets Java 8). On macOS with Homebrew:

  ```bash
  brew install --cask temurin@11
  ```
- The BLS native library (`libdashjbls`) built in `contrib/dashj-bls`. There
  is no Java fallback for it — WalletTool cannot start without it. If it is
  missing, the script initializes the git submodule and builds it
  automatically, which requires Maven, CMake and a C++ toolchain. To build it
  by hand instead:

  ```bash
  git submodule update --init --recursive
  cd contrib/dashj-bls
  mvn package -DskipTests
  ```

- The x11 native library in `contrib/x11` is optional — dashj falls back to a
  pure-Java implementation — but the native version hashes much faster, so the
  script also builds it automatically when missing (requires CMake). If that
  build fails the script only warns and continues. To build it by hand:

  ```bash
  cd contrib/x11
  mkdir -p build && cd build
  cmake .. && cmake --build .
  ```

## Getting the code

```bash
git clone https://github.com/dashpay/dashj.git
cd dashj
git checkout easy-coinjoin-mixer
git submodule update --init --recursive
```

The last command checks out `contrib/dashj-bls`, which is needed to build the
BLS native library.

## Usage

```bash
cd tools
./coinjoin-wallet.sh /path/to/my-wallet-folder
```

The folder is created if it does not exist. **The path must not contain
spaces.**

To inspect an existing wallet without syncing or mixing, pass `dump` as the
second argument — it prints the wallet dump (transactions, balances, mixing
progress at the configured `ROUNDS`) and exits:

```bash
./coinjoin-wallet.sh /path/to/my-wallet-folder dump
```

By default the sync and mix steps run quietly. Pass `--debuglog` (anywhere on
the command line) to enable WalletTool's debug logging for them:

```bash
./coinjoin-wallet.sh /path/to/my-wallet-folder --debuglog
```

The script pauses after printing the deposit address. Send the coins you want
to mix (testnet DASH is available from a faucet), then press Enter to start
the sync and mix. Mixing runs until all funds have been mixed to the requested
number of rounds, then exits and writes a `coinjoin-report-*.txt` summary to
the data folder.

The recovery phrase is also saved to `<wallet-name>-recovery-phrase.txt` in
the data folder (created with owner-only permissions).

If the wallet file already exists the script skips the creation, recovery
phrase and deposit address steps and goes straight to sync and mix, so it can
be re-run on the same folder to continue mixing without pausing. If the
recovery phrase file is missing on a re-run, it is recreated from the wallet.

## Options

Options are passed as environment variables:

| Variable   | Default | Meaning                                          |
|------------|---------|--------------------------------------------------|
| `NET`      | `TEST`  | Network: `TEST`, `MAIN`, `REGTEST` or `DEVNET`   |
| `ROUNDS`   | `16`    | Number of mixing rounds                          |
| `SESSIONS` | `4`     | Number of parallel mixing sessions               |
| `AMOUNT`   | (all)   | Amount to mix in DASH; defaults to whole balance |

Example — mix 5 DASH on testnet with 6 sessions:

```bash
AMOUNT=5.00 SESSIONS=6 ./coinjoin-wallet.sh ~/dash/mix-test
```

## Running the steps by hand

The script drives the `wallet_tool` Gradle task. Each step can also be run
directly from the repository root. `-PworkDir` sets the working directory, so
all files land in your folder; `--wallet` is relative to it.

```bash
# Create a wallet
./gradlew -q :dashj-tools:wallet_tool \
    "-PappArgs=create --net=TEST --wallet=coinjoin-test.wallet" \
    "-PworkDir=$HOME/dash/mix-test"

# Show the recovery phrase (look for the "Seed as words:" line)
./gradlew -q :dashj-tools:wallet_tool \
    "-PappArgs=dump --net=TEST --wallet=coinjoin-test.wallet --dump-privkeys" \
    "-PworkDir=$HOME/dash/mix-test"

# Show a deposit address
./gradlew -q :dashj-tools:wallet_tool \
    "-PappArgs=current-receive-addr --net=TEST --wallet=coinjoin-test.wallet" \
    "-PworkDir=$HOME/dash/mix-test"

# Sync the block chain
./gradlew -q :dashj-tools:wallet_tool \
    "-PappArgs=sync --net=TEST --wallet=coinjoin-test.wallet --debuglog" \
    "-PworkDir=$HOME/dash/mix-test"

# Mix 16 rounds
./gradlew -q :dashj-tools:wallet_tool \
    "-PappArgs=mix --net=TEST --wallet=coinjoin-test.wallet --rounds=16 --sessions=4 --debuglog" \
    "-PworkDir=$HOME/dash/mix-test"
```

The `mix` action syncs the chain itself before mixing, so the explicit `sync`
step is mainly useful for receiving the deposit and checking the balance
beforehand.

## Safety notes

- The recovery phrase is printed to the terminal and saved as plain text to
  `<wallet-name>-recovery-phrase.txt` in the data folder. Anyone who sees
  either can spend the wallet's funds — move the file somewhere safe (or
  delete it after writing the words down) when using real funds on mainnet.
- The wallet file in the data folder is unencrypted unless you encrypt it
  yourself with the `encrypt` action (mixing requires it to be decrypted).