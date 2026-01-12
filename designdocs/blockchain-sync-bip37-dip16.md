# Blockchain Synchronization with BIP37 and DIP-16 in DashJ

## Overview

This document explains the blockchain synchronization process implemented in DashJ through the `PeerGroup` and `Peer` classes, following:
- **BIP37** (Bloom Filtering) for Simplified Payment Verification (SPV) clients
- **DIP-16** (Headers-First Synchronization) for efficient Dash-specific sync with masternode and quorum support

DashJ implements a sophisticated multi-stage synchronization strategy that downloads block headers first, then masternode lists and LLMQ quorums, and finally retrieves filtered block bodies based on wallet bloom filters.

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [DIP-16 Headers-First Synchronization](#dip-16-headers-first-synchronization)
3. [BIP37 Bloom Filter Implementation](#bip37-bloom-filter-implementation)
4. [Key Classes and Components](#key-classes-and-components)
5. [Synchronization Process Flow](#synchronization-process-flow)
6. [Fast Catchup Optimization](#fast-catchup-optimization)
7. [Filter Exhaustion Handling](#filter-exhaustion-handling)
8. [Thread Safety](#thread-safety)

---

## Architecture Overview

The blockchain synchronization architecture in DashJ consists of two primary classes:

- **`PeerGroup`** (`core/src/main/java/org/bitcoinj/core/PeerGroup.java`) - Manages multiple peer connections, coordinates blockchain download, and maintains merged bloom filters
- **`Peer`** (`core/src/main/java/org/bitcoinj/core/Peer.java`) - Handles individual peer communication, executes block/header downloads, and processes filtered blocks

### High-Level Responsibilities

**PeerGroup:**
- Maintains a pool of peer connections
- Selects and manages the "download peer" for chain synchronization
- Merges bloom filters from all registered wallets using `FilterMerger`
- Distributes updated filters to all connected peers
- Coordinates chain download restart on peer disconnection

**Peer:**
- Executes the actual blockchain download protocol
- Maintains download state (headers vs. bodies mode)
- Processes filtered blocks and matching transactions
- Detects filter exhaustion and triggers recalculation

---

## DIP-16 Headers-First Synchronization

[DIP-16](https://github.com/dashpay/dips/blob/master/dip-0016.md) defines the "Headers-First Synchronization" process for Dash wallets, enabling efficient sync by retrieving blockchain data in stages. This is particularly important for Dash because wallets need masternode quorum information before determining which transactions to request.

### Why Headers-First for Dash?

Unlike Bitcoin, Dash has additional blockchain data beyond blocks and transactions:
- **Masternode Lists**: Deterministic masternode list (DIP-3) tracking active masternodes
- **LLMQ Quorums**: Long-Living Masternode Quorums (DIP-6) for InstantSend and ChainLocks
- **Governance Objects**: Proposals and votes managed by masternodes

Headers-first synchronization allows wallets to:
1. Quickly establish the blockchain height and tip
2. Retrieve masternode and quorum data needed for transaction validation
3. Only then request filtered block bodies for relevant wallet transactions

### Sync Stages

DashJ implements six distinct synchronization stages (PeerGroup.java:192-204):

```java
public enum SyncStage {
    OFFLINE(0),      // No sync in progress, no peers connected
    HEADERS(1),      // Downloading block headers only (80 bytes each)
    MNLIST(2),       // Downloading simplified masternode lists (mnlistdiff)
    PREBLOCKS(3),    // Pre-processing blocks (LLMQ validation, Platform queries)
    BLOCKS(4),       // Downloading full block bodies (filtered via BIP37)
    COMPLETE(5);     // Sync complete, monitoring for new blocks
}
```

### Stage-by-Stage Synchronization Flow

#### Stage 1: HEADERS - Block Header Download

**Objective**: Download all block headers from genesis to chain tip

**Implementation** (Peer.java:1782-1804):
```java
public void startBlockChainHeaderDownload() {
    vDownloadHeaders = true;
    final int blocksLeft = getPeerBlockHeightDifference();
    if (blocksLeft >= 0) {
        // Fire HeadersDownloadStartedEventListener
        lock.lock();
        try {
            blockChainHeaderDownloadLocked(Sha256Hash.ZERO_HASH);
        } finally {
            lock.unlock();
        }
    }
}
```

**Process**:
1. Send `GetHeadersMessage` with block locator (last 100 headers + exponential backoff)
2. Receive up to 2000 headers per response (protocol version 70218)
3. Add headers to separate `headerChain` (not `blockChain`)
4. Validate headers against checkpoints
5. Continue until receiving fewer than `MAX_HEADERS` (sync complete)

**Header Processing** (Peer.java:724-838):
```java
protected void processHeaders(HeadersMessage m) throws ProtocolException {
    if (vDownloadHeaders && headerChain != null) {
        for (Block header : m.getBlockHeaders()) {
            if (!headerChain.add(header)) {
                log.info("Received bad header - try again");
                blockChainHeaderDownloadLocked(Sha256Hash.ZERO_HASH);
                return;
            }
        }

        if (m.getBlockHeaders().size() < HeadersMessage.MAX_HEADERS) {
            system.triggerHeadersDownloadComplete();  // Move to next stage
        } else {
            blockChainHeaderDownloadLocked(Sha256Hash.ZERO_HASH);  // Request more
        }
    }
}
```

**Benefits**:
- Fast: Headers are only ~80 bytes vs. blocks which can be MBs
- Establishes blockchain height quickly
- Enables checkpoint verification
- Provides chain tip for masternode list queries

#### Stage 2: MNLIST - Masternode List Download

**Objective**: Synchronize the deterministic masternode list and LLMQ quorums

**Implementation** (Peer.java:851-876):
```java
public void startMasternodeListDownload() {
    try {
        StoredBlock masternodeListBlock = headerChain.getChainHead().getHeight() != 0 ?
                headerChain.getBlockStore().get(
                    headerChain.getBestChainHeight() - SigningManager.SIGN_HEIGHT_OFFSET) :
                blockChain.getBlockStore().get(
                    blockChain.getBestChainHeight() - SigningManager.SIGN_HEIGHT_OFFSET);

        if (system.masternodeListManager.getListAtChainTip().getHeight() <
                masternodeListBlock.getHeight()) {
            if (system.masternodeListManager.requestQuorumStateUpdate(
                    this, headerChain.getChainHead(), masternodeListBlock)) {
                queueMasternodeListDownloadedListeners(
                    MasternodeListDownloadedListener.Stage.Requesting, null);
            }
        } else {
            system.triggerMnListDownloadComplete();
        }
    } catch (BlockStoreException x) {
        system.triggerMnListDownloadComplete();
    }
}
```

**Masternode List Diff Structure** (SimplifiedMasternodeListDiff.java:15-61):

The `mnlistdiff` message contains incremental updates to the masternode list:

```java
public class SimplifiedMasternodeListDiff extends AbstractDiffMessage {
    private Sha256Hash prevBlockHash;  // Previous block hash
    private Sha256Hash blockHash;      // Current block hash
    PartialMerkleTree cbTxMerkleTree;  // Coinbase tx merkle proof
    Transaction coinBaseTx;             // Coinbase transaction

    // Masternode list updates
    protected HashSet<Sha256Hash> deletedMNs;  // Removed masternodes
    protected ArrayList<SimplifiedMasternodeListEntry> mnList;  // Added/updated MNs

    // LLMQ quorum updates (DIP-4)
    protected ArrayList<Pair<Integer, Sha256Hash>> deletedQuorums;
    protected ArrayList<FinalCommitment> newQuorums;
    protected HashMap<BLSSignature, HashSet<Integer>> quorumsCLSigs;  // ChainLock sigs
}
```

**Process**:
1. Request `mnlistdiff` from current masternode list height to chain tip
2. Apply deletions and additions to local masternode list
3. Validate LLMQ quorum commitments (BLS signatures)
4. Update quorum rotation state for InstantSend/ChainLock validation
5. Trigger completion when masternode list reaches chain tip height

**Why This Matters**:
- InstantSend requires knowing active quorums to validate locks
- ChainLocks require quorum public keys for signature verification
- Governance requires knowing which masternodes can vote

#### Stage 3: PREBLOCKS - Pre-Block Processing (Optional)

**Objective**: Perform application-specific preprocessing before block download

This stage is optional and activated via the `SYNC_BLOCKS_AFTER_PREPROCESSING` flag.

**Use Cases**:
- Dash Platform identity queries
- Additional LLMQ validation
- Governance object synchronization
- Application-specific state preparation

**Implementation** (PeerGroup.java:2387-2410):
```java
mnListDownloadedCallback = new FutureCallback<Integer>() {
    @Override
    public void onSuccess(@Nullable Integer listsSynced) {
        if (flags.contains(SYNC_BLOCKS_AFTER_PREPROCESSING)) {
            setSyncStage(SyncStage.PREBLOCKS);
            queuePreBlockDownloadListeners(peer);
        } else {
            setSyncStage(SyncStage.BLOCKS);
            peer.startBlockChainDownload();
        }
    }
};
```

#### Stage 4: BLOCKS - Block Body Download (with BIP37 Filtering)

**Objective**: Download full block bodies filtered by wallet bloom filters

This stage combines DIP-16's headers-first approach with BIP37 bloom filtering.

**Transition from Headers to Blocks** (Peer.java:1806-1811):
```java
public void continueDownloadingBlocks() {
   if (vDownloadHeaders) {
       setDownloadHeaders(false);  // Disable header-only mode
       startBlockChainDownload();  // Start full block download
   }
}
```

**Process**:
1. Bloom filter already set during peer connection (see BIP37 section)
2. Switch from `GetHeadersMessage` to `GetBlocksMessage`
3. Request filtered blocks (`MSG_FILTERED_BLOCK`) instead of full blocks
4. Receive `MerkleBlock` (header + partial merkle tree) + matching transactions
5. Validate transactions and add to wallet
6. Continue until block chain catches up with header chain

**Conditional Logic** (PeerGroup.java:2481-2524):
```java
if (flags.contains(MasternodeSync.SYNC_FLAGS.SYNC_HEADERS_MN_LIST_FIRST)) {
    if (peer.getBestHeight() > headerChain.getChainHead().getHeight() &&
            syncStage.value <= SyncStage.HEADERS.value) {
        // STAGE 1: Download headers
        setSyncStage(SyncStage.HEADERS);
        peer.startBlockChainHeaderDownload();

    } else if (syncStage.value == SyncStage.MNLIST.value) {
        // STAGE 2: Download masternode lists
        peer.startMasternodeListDownload();

    } else if (flags.contains(SYNC_BLOCKS_AFTER_PREPROCESSING) &&
               syncStage.value < SyncStage.PREBLOCKS.value) {
        // STAGE 3: Pre-process blocks
        setSyncStage(SyncStage.PREBLOCKS);
        queuePreBlockDownloadListeners(peer);

    } else {
        // STAGE 4: Download full block bodies
        setSyncStage(SyncStage.BLOCKS);
        peer.startBlockChainDownload();
    }
}
```

#### Stage 5: COMPLETE - Ongoing Synchronization

**Objective**: Monitor for new blocks and maintain sync state

Once initial sync completes:
- Listen for new block announcements via `inv` messages
- Validate InstantSend locks using synchronized quorum data
- Verify ChainLocks using quorum signatures
- Process governance proposals and votes
- Maintain masternode list updates

### Event-Driven Stage Transitions

Stage transitions are managed via `ListenableFuture` callbacks (PeerGroup.java:2366-2430):

```java
// Headers download completion callback
headersDownloadedCallback = new FutureCallback<Boolean>() {
    @Override
    public void onSuccess(@Nullable Boolean aBoolean) {
        log.info("Stage header download completed successfully");
        if (aBoolean) {
            peer.setDownloadHeaders(false);
            setSyncStage(SyncStage.MNLIST);  // Transition to masternode list sync
            peer.startMasternodeListDownload();
        }
    }

    @Override
    public void onFailure(Throwable throwable) {
        log.info("Stage header download failed");
        peer.setDownloadHeaders(false);
        setSyncStage(SyncStage.BLOCKS);  // Fall back to direct block download
        peer.startBlockChainDownload();
    }
};

// Masternode list completion callback
mnListDownloadedCallback = new FutureCallback<Integer>() {
    @Override
    public void onSuccess(@Nullable Integer listsSynced) {
        if (flags.contains(SYNC_BLOCKS_AFTER_PREPROCESSING)) {
            setSyncStage(SyncStage.PREBLOCKS);
            queuePreBlockDownloadListeners(peer);
        } else {
            setSyncStage(SyncStage.BLOCKS);
            peer.startBlockChainDownload();  // Transition to block download
        }
    }
};
```

### Checkpoint-Based Security

DIP-16 leverages hardcoded checkpoints to prevent deep fork attacks during initial sync (AbstractBlockChain.java:511-513):

```java
// Check that we aren't connecting a block that fails a checkpoint check
if (!params.passesCheckpoint(storedPrev.getHeight() + 1, block.getHash()))
    throw new VerificationException("Block failed checkpoint lockin at " +
        (storedPrev.getHeight() + 1));
```

**Checkpoint Structure**:
- Block height
- Block hash
- Timestamp
- Target difficulty
- Aggregated chainwork

**Benefits**:
- Fast validation of headers without full PoW verification
- Protection against long-range attacks
- Reduced computational requirements for SPV clients

### Download Progress Tracking

DIP-16 sync progress is weighted across stages (DownloadProgressTracker.java:54-60):

```java
private static final double SYNC_HEADERS = 0.30;         // 30% of total sync
private static final double SYNC_MASTERNODE_LIST = 0.05; // 5% of total sync
private static final double SYNC_PREDOWNLOAD = 0.05;     // 5% of total sync
public double blocksWeight;                               // 60% of total sync (default)

double progress = headersWeight * percentHeaders +
                 mnListWeight * percentMnList +
                 preBlocksWeight * percentPreBlocks +
                 blocksWeight * percentBlocks;
```

This provides accurate progress reporting across all sync stages.

### Enabling Headers-First Synchronization

**Configuration Flags** (MasternodeSync.java:87-88):

```java
public static final EnumSet<SYNC_FLAGS> SYNC_DEFAULT_SPV_HEADERS_FIRST =
    EnumSet.of(SYNC_MASTERNODE_LIST,           // Sync masternode lists
               SYNC_QUORUM_LIST,               // Sync LLMQ quorums
               SYNC_CHAINLOCKS,                // Validate ChainLocks
               SYNC_INSTANTSENDLOCKS,          // Validate InstantSend locks
               SYNC_SPORKS,                    // Sync network sporks
               SYNC_HEADERS_MN_LIST_FIRST);    // Enable headers-first mode
```

**Activation** (WalletAppKit.java:142):

```java
vSystem.masternodeSync.addSyncFlag(
    MasternodeSync.SYNC_FLAGS.SYNC_HEADERS_MN_LIST_FIRST);
```

### DIP-16 Complete Synchronization Flow

```
┌─────────────────────────────────────────────────────────────────┐
│ STAGE 0: OFFLINE                                                 │
│ - No peers connected                                             │
│ - Waiting for network                                            │
└────────────────────────────────┬────────────────────────────────┘
                                 │
                  Peer connects & version handshake complete
                                 │
                                 ▼
┌─────────────────────────────────────────────────────────────────┐
│ STAGE 1: HEADERS                                                 │
│ - Send GetHeadersMessage with block locator                      │
│ - Receive up to 2000 headers per HeadersMessage                  │
│ - Add to headerChain (separate from blockChain)                  │
│ - Validate against checkpoints                                   │
│ - Progress: ~80 bytes per block header                           │
│                                                                   │
│ Completion: m.getBlockHeaders().size() < MAX_HEADERS             │
└────────────────────────────────┬────────────────────────────────┘
                                 │
                   system.triggerHeadersDownloadComplete()
                                 │
                                 ▼
┌─────────────────────────────────────────────────────────────────┐
│ STAGE 2: MNLIST                                                  │
│ - Request mnlistdiff from masternodeListManager                  │
│ - Download SimplifiedMasternodeListDiff messages                 │
│ - Apply masternode additions/deletions                           │
│ - Validate LLMQ quorum commitments (BLS signatures)              │
│ - Update quorum rotation state                                   │
│                                                                   │
│ Completion: MN list height >= headerChain tip height             │
└────────────────────────────────┬────────────────────────────────┘
                                 │
                   system.triggerMnListDownloadComplete()
                                 │
                                 ▼
┌─────────────────────────────────────────────────────────────────┐
│ STAGE 3: PREBLOCKS (Optional)                                    │
│ - Platform identity queries                                      │
│ - Additional LLMQ validation                                     │
│ - Governance object sync                                         │
│ - Application-specific preprocessing                             │
│                                                                   │
│ Completion: Application-defined criteria                         │
└────────────────────────────────┬────────────────────────────────┘
                                 │
                      queuePreBlockDownloadListeners()
                                 │
                                 ▼
┌──────────────────────────────────────────────────────────────────┐
│ STAGE 4: BLOCKS                                                  │
│ - setDownloadHeaders(false)                                      │
│ - Send GetBlocksMessage with block locator                       │
│ - Request MSG_FILTERED_BLOCK (BIP37 bloom filtering)             │
│ - Receive MerkleBlock + matching transactions                    │
│ - Validate transactions, add to wallet                           │
│ - Process InstantSend locks (validated via LLMQ data)            │
│ - Verify ChainLocks (validated via quorum signatures)            │
│                                                                  │
│ Completion: blockChain height == headerChain height              │
└────────────────────────────────┬─────────────────────────────────┘
                                 │
                     Chain sync complete event
                                 │
                                 ▼
┌──────────────────────────────────────────────────────────────────┐
│ STAGE 5: COMPLETE                                                │
│ - Monitor for new block inv messages                             │
│ - Validate InstantSend locks on new transactions                 │
│ - Verify ChainLocks on new blocks                                │
│ - Process governance proposals/votes                             │
│ - Maintain masternode list with incremental updates              │
└──────────────────────────────────────────────────────────────────┘
```

### Key DIP-16 Implementation Files

| Component | File | Key Lines |
|-----------|------|-----------|
| **SyncStage Enum** | PeerGroup.java | 192-204 |
| **Headers Download** | Peer.java | 1782-1804 |
| **Header Processing** | Peer.java | 724-838 |
| **MN List Download** | Peer.java | 851-876 |
| **Stage Transitions** | PeerGroup.java | 2366-2430, 2481-2524 |
| **MN List Diff Structure** | SimplifiedMasternodeListDiff.java | 15-100 |
| **Quorum State Management** | QuorumState.java | 47-180 |
| **Download Progress** | DownloadProgressTracker.java | 42-282 |
| **Checkpoint Validation** | AbstractBlockChain.java | 511-513 |
| **Sync Flags Configuration** | MasternodeSync.java | 57-88 |

---

## BIP37 Bloom Filter Implementation

BIP37 enables lightweight SPV clients to request only transactions matching a bloom filter, reducing bandwidth and storage requirements.

### Bloom Filter Lifecycle

1. **Filter Creation**
   - `PeerGroup` aggregates all filter providers (wallets) via `FilterMerger.calculate()`
   - Each wallet contributes:
     - Watched addresses and public keys
     - Output scripts
     - Transaction outpoints
   - Element count is "stair-stepped" (rounded up by 100) to reduce filter regeneration frequency

2. **Filter Distribution**
   ```
   PeerGroup → FilterMerger.calculate() → BloomFilter
                                              ↓
   Peer.setBloomFilter(filter, andQueryMemPool)
                                              ↓
                              Send: FilterLoadMessage to remote peer
                              Send: MemPoolMessage (if andQueryMemPool=true)
   ```

3. **Filter Parameters** (from `BloomFilter` class)
   - **Maximum size**: 36,000 bytes
   - **Maximum hash functions**: 50
   - **Configurable false positive rate**: Default set via `DEFAULT_BLOOM_FILTER_FP_RATE`
   - **Tweak**: Random value maintained across filter updates for privacy
   - **Update flags**:
     - `UPDATE_NONE`: Don't auto-update filter
     - `UPDATE_ALL`: Update filter for all matching outputs
     - `UPDATE_P2PUBKEY_ONLY`: Update only for P2PK/P2PKH outputs

### Filter Recalculation Triggers

Filter recalculation occurs when:

1. **New keys added to wallet** (`walletKeyEventListener`)
2. **Scripts change** (`walletScriptEventListener`)
3. **P2PK outputs received** (`walletCoinsReceivedEventListener`)
4. **False positive rate exceeds threshold** (`peerListener.onBlocksDownloaded()`)
   - Threshold: `bloomFilterFPRate * MAX_FP_RATE_INCREASE`
5. **Manual request** via `PeerGroup.setBloomFilterFalsePositiveRate()`

### Recalculation Modes

The `recalculateFastCatchupAndFilter()` method supports three modes:

| Mode | Description |
|------|-------------|
| `SEND_IF_CHANGED` | Send new filter only if contents changed |
| `DONT_SEND` | Recalculate but don't broadcast to peers |
| `FORCE_SEND_FOR_REFRESH` | Always send, even if unchanged (for high FP rate mitigation) |

---

## Key Classes and Components

### Core Classes

| Class | Location | Purpose |
|-------|----------|---------|
| `Peer` | `core/src/main/java/org/bitcoinj/core/Peer.java` | Individual peer connection handling |
| `PeerGroup` | `core/src/main/java/org/bitcoinj/core/PeerGroup.java` | Peer pool management and coordination |
| `BloomFilter` | `core/src/main/java/org/bitcoinj/core/BloomFilter.java` | BIP37 bloom filter implementation |
| `FilterMerger` | `core/src/main/java/org/bitcoinj/net/FilterMerger.java` | Merges filters from multiple providers |
| `FilteredBlock` | `core/src/main/java/org/bitcoinj/core/FilteredBlock.java` | Block with partial merkle tree |
| `PeerFilterProvider` | `core/src/main/java/org/bitcoinj/core/PeerFilterProvider.java` | Interface for filter generation |

### Key State Variables

**Peer Class:**
```java
@GuardedBy("lock") private boolean downloadBlockBodies = true;
@GuardedBy("lock") private boolean useFilteredBlocks = false;
private volatile BloomFilter vBloomFilter;
private volatile boolean vDownloadData = true;
private volatile boolean vDownloadHeaders = false;
@Nullable private FilteredBlock currentFilteredBlock;
@GuardedBy("lock") @Nullable private List<Sha256Hash> awaitingFreshFilter;
@GuardedBy("lock") private Sha256Hash lastGetBlocksBegin, lastGetBlocksEnd;
@GuardedBy("lock") private long fastCatchupTimeSecs;
```

**PeerGroup Class:**
```java
@GuardedBy("lock") private Peer downloadPeer;
private final FilterMerger bloomFilterMerger;
@GuardedBy("lock") @Nullable private PeerDataEventListener downloadListener;
```

---

## Synchronization Process Flow

### 1. Initialization Phase

```
Application
    ↓
PeerGroup.start()
    ↓
Connect to peers
    ↓
PeerGroup.handleNewPeer(peer)
    ├─→ Calculate merged bloom filter
    ├─→ Send filter via peer.setBloomFilter()
    └─→ Select download peer (if first peer or > maxConnections/2)
```

### 2. Download Peer Selection

When a download peer is selected (`PeerGroup.startBlockChainDownloadFromPeer()`):

```
PeerGroup.startBlockChainDownloadFromPeer(peer)
    ├─→ Set downloadPeer = peer
    ├─→ Start ChainDownloadSpeedCalculator
    └─→ Initiate sync based on sync stage:
         ├─→ BLOCKS: peer.startBlockChainDownload()
         └─→ PREBLOCKS: Queue pre-block download listeners
```

### 3. Block Chain Download Process

**Core method: `Peer.blockChainDownloadLocked(Sha256Hash toHash)`**

This method implements the iterative blockchain download:

```
blockChainDownloadLocked(toHash)
    ↓
Build BlockLocator (last 100 block headers from chain head)
    ↓
Check downloadBlockBodies flag
    ├─→ true: Send GetBlocksMessage (requests block bodies)
    └─→ false: Send GetHeadersMessage (requests headers only)
    ↓
Peer responds with InvMessage (up to 500 blocks)
    ↓
Peer.processInv() → Send GetDataMessage
    ├─→ If useFilteredBlocks: Request MSG_FILTERED_BLOCK
    └─→ Else: Request MSG_BLOCK
    ↓
Peer sends FilteredBlock or Block
    ↓
Process and add to chain
    ↓
If orphan detected → blockChainDownloadLocked(orphanRoot.hash)
    ↓
Repeat until synchronized
```

**Duplicate Request Prevention:**

The variables `lastGetBlocksBegin` and `lastGetBlocksEnd` track the most recent `getblocks`/`getheaders` request to avoid redundant requests:

```java
if (Objects.equals(lastGetBlocksBegin, chainHeadHash) &&
    Objects.equals(lastGetBlocksEnd, toHash)) {
    log.info("blockChainDownloadLocked({}): ignoring duplicated request", toHash);
    return;
}
```

### 4. Filtered Block Processing

When a `MerkleBlockMessage` arrives (`Peer.processFilteredBlock()`):

```
Start: FilteredBlock received
    ↓
Peer.startFilteredBlock(filteredBlock)
    ├─→ Set currentFilteredBlock
    └─→ Initialize matching transactions list
    ↓
Receive matching transactions
    ↓
Peer.endFilteredBlock(filteredBlock)
    ├─→ Check for filter exhaustion
    ├─→ If exhausted:
    │   ├─→ Add block hash to awaitingFreshFilter
    │   ├─→ Discard block
    │   └─→ Wait for new filter (restart via setBloomFilter())
    └─→ Else: Add block to chain
    ↓
Invoke onBlocksDownloaded listeners
    ↓
Check if more blocks needed → blockChainDownloadLocked()
```

### 5. Block Locator Construction

The block locator is a critical component for efficient sync:

```
Build locator starting from chain head:
    Add last 100 blocks sequentially
    Then exponential backoff:
        step *= 2 each iteration
        Add block at (height - step)
    Continue until genesis block
Result: [head, head-1, ..., head-99, head-101, head-105, ..., genesis]
```

This structure allows peers to find the common ancestor quickly, even after long disconnections.

### 6. Protocol Messages Flow

**BIP37 Message Sequence:**

```
Client (DashJ)                          Peer (Dash Core Node)
      |                                        |
      |--- FilterLoadMessage ----------------->|
      |    (bloom filter parameters)           |
      |                                        |
      |--- MemPoolMessage -------------------->|
      |    (optional, query mempool)           |
      |                                        |
      |<-- FilteredBlock or Inv ---------------|
      |    (matching unconfirmed txs)          |
      |                                        |
      |--- GetBlocksMessage ------------------>|
      |    (block locator, stop hash)          |
      |                                        |
      |<-- InvMessage -------------------------|
      |    (up to 500 block hashes)            |
      |                                         |
      |--- GetDataMessage --------------------->|
      |    (MSG_FILTERED_BLOCK for each hash)  |
      |                                         |
      |<-- MerkleBlockMessage -----------------|
      |    (block header + partial merkle tree)|
      |                                         |
      |<-- TxMessage ---------------------------|
      |    (matching transactions)             |
      |                                         |
      |--- GetBlocksMessage ------------------>|
      |    (continue download)                 |
      |                                        |
      [Repeat until chain synchronized]
```

---

## Fast Catchup Optimization

Fast catchup allows wallets to skip downloading full block data for blocks created before the wallet's earliest key creation time.

### Configuration

Set via `Peer.setDownloadParameters(long fastCatchupTimeSecs, boolean useFilteredBlocks)`:

```java
public void setDownloadParameters(long secondsSinceEpoch, boolean useFilteredBlocks) {
    lock.lock();
    try {
        this.fastCatchupTimeSecs = secondsSinceEpoch;
        this.useFilteredBlocks = useFilteredBlocks;
    } finally {
        lock.unlock();
    }
}
```

### Fast Catchup Process

1. **Initial State:**
   - `downloadBlockBodies = false` (header-only mode)
   - `fastCatchupTimeSecs` set to earliest wallet key time - 1 week

2. **During Header Download:**
   - `Peer.blockChainHeaderDownloadLocked()` sends `GetHeadersMessage`
   - Headers processed in `processHeadersMessage()`
   - Block bodies NOT downloaded

3. **Transition to Full Download:**

   When a header's timestamp exceeds `fastCatchupTimeSecs`:

   ```java
   if (header.getTimeSeconds() >= fastCatchupTimeSecs) {
       log.info("Passed the fast catchup time ({})...",
           Utils.dateTimeFormat(fastCatchupTimeSecs * 1000));
       this.downloadBlockBodies = true;
       this.lastGetBlocksBegin = Sha256Hash.ZERO_HASH; // Prevent duplicate detection
       blockChainDownloadLocked(Sha256Hash.ZERO_HASH);
       return;
   }
   ```

4. **Full Block Download:**
   - Switch to `GetBlocksMessage` (full blocks or filtered blocks)
   - Process transactions matching bloom filter

### Benefits

- **Reduced bandwidth**: Headers are ~80 bytes vs. full blocks (can be MBs)
- **Faster initial sync**: Skip irrelevant historical data
- **Lower storage**: Don't store transactions before wallet creation

---

## Filter Exhaustion Handling

Filter exhaustion occurs when a wallet generates new keys during sync, making the current bloom filter incomplete.

### Detection

Implemented in `Peer.checkForFilterExhaustion(FilteredBlock m)`:

```java
private boolean checkForFilterExhaustion(FilteredBlock m) {
    for (Wallet wallet : wallets) {
        if (wallet.checkForFilterExhaustion(m)) {
            return true;
        }
    }
    return false;
}
```

Wallet checks if:
- New keys were added since filter was sent
- Received block might contain transactions for those new keys

### Handling Process

When exhaustion detected:

```
Filter Exhaustion Detected
    ↓
Set awaitingFreshFilter = new LinkedList<>()
    ↓
Add current block hash to awaitingFreshFilter
    ↓
Drain all orphan blocks and add to awaitingFreshFilter
    ↓
Discard current and pending blocks
    ↓
Wait for new filter...
    ↓
PeerGroup.recalculateFastCatchupAndFilter(SEND_IF_CHANGED)
    ↓
Peer.setBloomFilter(newFilter)
    ├─→ Send FilterLoadMessage
    └─→ Call maybeRestartChainDownload()
         ↓
    Send ping/pong to ensure filter applied
         ↓
    blockChainDownloadLocked() to re-request awaiting blocks
```

### Critical Implementation Details

From `Peer.setBloomFilter()`:

```java
public void setBloomFilter(BloomFilter filter, boolean andQueryMemPool) {
    checkNotNull(filter, "Clearing filters is not currently supported");
    final VersionMessage version = vPeerVersionMessage;
    checkNotNull(version, "Cannot set filter before version handshake is complete");

    if (version.isBloomFilteringSupported()) {
        vBloomFilter = filter;
        sendMessage(filter.toBloomFilterMessage());

        if (andQueryMemPool)
            sendMessage(new MemoryPoolMessage());

        // Ping/pong to wait for filter to be applied
        ListenableFuture<Long> future = ping();
        Futures.addCallback(future, new FutureCallback<Long>() {
            @Override
            public void onSuccess(Long result) {
                maybeRestartChainDownload();
            }
            // ...
        });
    }
}
```

The ping/pong ensures the remote peer has applied the new filter before resuming download, preventing missed transactions.

---

## Thread Safety

### Locking Strategy

Both `Peer` and `PeerGroup` use `ReentrantLock` for thread safety:

```java
// Peer.java
protected final ReentrantLock lock = Threading.lock("peer");

// PeerGroup.java
protected final ReentrantLock lock = Threading.lock("peergroup");
```

### Guarded Variables

**Peer:**
- `downloadBlockBodies` - Controls header vs. body download
- `fastCatchupTimeSecs` - Fast catchup timestamp
- `awaitingFreshFilter` - Blocks awaiting filter recalculation
- `lastGetBlocksBegin/End` - Duplicate request tracking

**PeerGroup:**
- `downloadPeer` - Currently selected download peer
- `downloadListener` - Chain download event listener
- `inactives` - Queue of inactive peer addresses
- `backoffMap` - Exponential backoff for failed connections

### Thread-Safe Collections

- `CopyOnWriteArrayList` for listener lists (both classes)
- `CopyOnWriteArrayList<Peer> peers` in PeerGroup
- `CopyOnWriteArrayList<Wallet> wallets` in Peer

### Executor Usage

PeerGroup uses a `ListeningScheduledExecutorService` to serialize operations that:
- Access user-provided code (wallet listeners)
- Require ordering relative to other jobs
- Avoid lock contention with user code

Example:
```java
executor.submit(new Runnable() {
    @Override
    public void run() {
        recalculateFastCatchupAndFilter(FilterRecalculateMode.SEND_IF_CHANGED);
    }
});
```

---

## Key Synchronization Methods Reference

### Peer Methods

| Method | Purpose | Thread Safety |
|--------|---------|---------------|
| `startBlockChainDownload()` | Initiates async block chain download | Thread-safe via lock |
| `blockChainDownloadLocked(Sha256Hash)` | Core download method, sends getblocks/getheaders | Requires lock held |
| `setBloomFilter(BloomFilter, boolean)` | Sets bloom filter and optionally queries mempool | Thread-safe |
| `setDownloadParameters(long, boolean)` | Configures fast catchup time and filtered blocks | Thread-safe via lock |
| `setDownloadData(boolean)` | Enables/disables data download from peer | Volatile variable |
| `checkForFilterExhaustion(FilteredBlock)` | Checks if filter needs recalculation | Called under lock |
| `maybeRestartChainDownload()` | Restarts download after filter update | Thread-safe via ping/pong |

### PeerGroup Methods

| Method | Purpose | Thread Safety |
|--------|---------|---------------|
| `startBlockChainDownload(PeerDataEventListener)` | Registers listener for chain download | Thread-safe via lock |
| `startBlockChainDownloadFromPeer(Peer)` | Selects peer and initiates sync | Requires lock held |
| `recalculateFastCatchupAndFilter(FilterRecalculateMode)` | Merges filters and broadcasts | Async via executor |
| `setBloomFilterFalsePositiveRate(double)` | Updates FP rate and recalculates | Thread-safe via lock |
| `addWallet(Wallet)` | Registers wallet as filter provider | Thread-safe via lock |
| `removeWallet(Wallet)` | Unregisters wallet | Thread-safe via lock |
| `handleNewPeer(Peer)` | Sets up new peer with current filter | Thread-safe via lock |

---

## Summary

The DashJ blockchain synchronization process implements a sophisticated multi-protocol approach combining:

### DIP-16 Headers-First Synchronization
1. **Multi-Stage Sync**: Six-stage process (OFFLINE → HEADERS → MNLIST → PREBLOCKS → BLOCKS → COMPLETE)
2. **Headers-First Download**: Quickly establish blockchain height with ~80 byte headers
3. **Masternode List Sync**: Download deterministic masternode lists and LLMQ quorums before blocks
4. **LLMQ Integration**: Synchronize Long-Living Masternode Quorums for InstantSend and ChainLock validation
5. **Checkpoint Security**: Hardcoded checkpoints protect against deep fork attacks during initial sync
6. **Event-Driven Transitions**: Future-based callbacks coordinate stage progression
7. **Progress Tracking**: Weighted progress calculation across all sync stages (30% headers, 5% mnlist, 5% preblocks, 60% blocks)

### BIP37 Bloom Filtering
1. **Filter Management**: PeerGroup merges bloom filters from all wallets via FilterMerger and distributes to peers
2. **Privacy-Preserving**: Configurable false positive rate maintains privacy while reducing bandwidth
3. **Dynamic Recalculation**: Automatic filter updates when keys added or false positive rate exceeds threshold
4. **Filter Exhaustion Handling**: Detects and recovers when new keys generated during sync
5. **Stair-Stepping**: Element count rounded up by 100 to reduce filter regeneration frequency

### Core Synchronization Features
1. **Download Coordination**: PeerGroup selects download peer and orchestrates multi-stage sync
2. **Protocol Execution**: Peer executes download using getblocks/getheaders with block locators
3. **Fast Catchup**: Headers-only download for old blocks, switching to filtered blocks after wallet creation time
4. **Dual Chain Support**: Separate headerChain and blockChain for efficient headers-first sync
5. **Thread Safety**: Comprehensive locking strategy and executor-based serialization

### Dash-Specific Features
- **Masternode List**: Incremental sync via SimplifiedMasternodeListDiff (mnlistdiff messages)
- **LLMQ Quorums**: Quorum commitments with BLS signature validation
- **ChainLock Validation**: Verify ChainLock signatures using synchronized quorum data
- **InstantSend Support**: Validate InstantSend locks against active quorum state
- **Governance Integration**: Support for masternode governance proposals and votes

This architecture provides bandwidth-efficient blockchain synchronization while maintaining privacy through bloom filters, supporting Dash-specific features (masternodes, quorums, InstantSend, ChainLocks), and enabling dynamic wallet key generation.

---

## References

### Specifications
- **BIP37**: [Connection Bloom filtering](https://github.com/bitcoin/bips/blob/master/bip-0037.mediawiki)
- **DIP-16**: [Headers-First Synchronization](https://github.com/dashpay/dips/blob/master/dip-0016.md)
- **DIP-3**: [Deterministic Masternode Lists](https://github.com/dashpay/dips/blob/master/dip-0003.md)
- **DIP-4**: [Simplified Verification of Deterministic Masternode Lists](https://github.com/dashpay/dips/blob/master/dip-0004.md)
- **DIP-6**: [Long-Living Masternode Quorums](https://github.com/dashpay/dips/blob/master/dip-0006.md)

### Core Source Files
- `core/src/main/java/org/bitcoinj/core/Peer.java` - Individual peer connection and sync execution
- `core/src/main/java/org/bitcoinj/core/PeerGroup.java` - Peer pool management and sync coordination
- `core/src/main/java/org/bitcoinj/net/FilterMerger.java` - Bloom filter merging
- `core/src/main/java/org/bitcoinj/core/BloomFilter.java` - BIP37 bloom filter implementation
- `core/src/main/java/org/bitcoinj/core/FilteredBlock.java` - Merkle block with partial merkle tree

### Dash-Specific Source Files
- `core/src/main/java/org/bitcoinj/evolution/SimplifiedMasternodeListDiff.java` - Masternode list diff
- `core/src/main/java/org/bitcoinj/evolution/QuorumState.java` - LLMQ quorum state management
- `core/src/main/java/org/bitcoinj/evolution/QuorumRotationState.java` - Quorum rotation handling
- `core/src/main/java/org/bitcoinj/quorums/LLMQUtils.java` - LLMQ utilities
- `core/src/main/java/org/bitcoinj/core/MasternodeSync.java` - Masternode sync state management
- `core/src/main/java/org/bitcoinj/core/listeners/DownloadProgressTracker.java` - Multi-stage progress tracking