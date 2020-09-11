/*
 * Copyright 2011 Google Inc.
 * Copyright 2014 Andreas Schildbach
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.bitcoinj.core.listeners;

import org.bitcoinj.core.Block;
import org.bitcoinj.core.FilteredBlock;
import org.bitcoinj.core.Peer;
import org.bitcoinj.core.Utils;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.*;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

/**
 * <p>An implementation of {@link AbstractPeerDataEventListener} that listens to chain download events and tracks progress
 * as a percentage. The default implementation prints progress to stdout, but you can subclass it and override the
 * progress method to update a GUI instead.</p>
 */
public class DownloadProgressTracker extends AbstractPeerDataEventListener {
    private static final Logger log = LoggerFactory.getLogger(DownloadProgressTracker.class);
    private int originalBlocksLeft = -1;
    private int lastPercent = 0;
    private int originalHeadersLeft = -1;
    private int lastHeadersPercent = 0;
    private SettableFuture<Long> future = SettableFuture.create();
    private boolean caughtUp = false;
    private boolean requiresHeaders = false;
    private boolean headersCaughtUp = false;
    private static final double SYNC_HEADERS = 10.0;
    private static final double SYNC_PREDOWNLOAD = 10.0;

    @Override
    public void onChainDownloadStarted(Peer peer, int blocksLeft) {
        if (blocksLeft > 0 && originalBlocksLeft == -1)
            startDownload(blocksLeft);
        // Only mark this the first time, because this method can be called more than once during a chain download
        // if we switch peers during it.
        if (originalBlocksLeft == -1)
            originalBlocksLeft = blocksLeft;
        else
            log.info("Chain download switched to {}", peer);
        if (blocksLeft == 0) {
            doneDownload();
            future.set(peer.getBestHeight());
        }
    }

    @Override
    public void onBlocksDownloaded(Peer peer, Block block, @Nullable FilteredBlock filteredBlock, int blocksLeft) {
        if (caughtUp)
            return;

        if (blocksLeft == 0) {
            caughtUp = true;
            if (lastPercent != 100) {
                lastPercent = 100;
                progress(calculatePercentage(lastHeadersPercent, lastPercent), blocksLeft, new Date(block.getTimeSeconds() * 1000));
            }
            doneDownload();
            future.set(peer.getBestHeight());
            return;
        }

        if (blocksLeft < 0 || originalBlocksLeft <= 0)
            return;

        double pct = 100.0 - (100.0 * (blocksLeft / (double) originalBlocksLeft));
        if ((int) pct != lastPercent) {
            progress(calculatePercentage(lastHeadersPercent, pct), blocksLeft, new Date(block.getTimeSeconds() * 1000));
            lastPercent = (int) pct;
        }
    }

    /**
     * Called when download progress is made.
     *
     * @param pct  the percentage of chain downloaded, estimated
     * @param date the date of the last block downloaded
     */
    protected void progress(double pct, int blocksSoFar, Date date) {
        if (!requiresHeaders || headersCaughtUp) {
            log.info(String.format(Locale.US, "Chain download %d%% done with %d blocks to go, block date %s", (int) pct, blocksSoFar,
                    Utils.dateTimeFormat(date)));
        } else {
            log.info(String.format(Locale.US, "Header download %d%% done with %d blocks to go, block date %s", (int) pct, blocksSoFar,
                    Utils.dateTimeFormat(date)));
        }
    }

    /**
     * Called when download is initiated.
     *
     * @param blocks the number of blocks to download, estimated
     */
    protected void startDownload(int blocks) {
        log.info("Downloading block chain of size " + blocks + ". " +
                (blocks > 1000 ? "This may take a while." : ""));
    }

    /**
     * Called when we are done downloading the block chain.
     */
    protected void doneDownload() {
    }

    /**
     * Wait for the chain to be downloaded.
     */
    public void await() throws InterruptedException {
        try {
            future.get();
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns a listenable future that completes with the height of the best chain (as reported by the peer) once chain
     * download seems to be finished.
     */
    public ListenableFuture<Long> getFuture() {
        return future;
    }

    @Override
    public void onHeadersDownloadStarted(Peer peer, int blocksLeft) {
        requiresHeaders = true;
        if (blocksLeft > 0 && originalHeadersLeft == -1)
            startDownload(blocksLeft);
        // Only mark this the first time, because this method can be called more than once during a chain download
        // if we switch peers during it.
        if (originalHeadersLeft == -1)
            originalHeadersLeft = blocksLeft;
        else
            log.info("Chain download switched to {}", peer);
        if (blocksLeft == 0) {
            doneHeaderDownload();
        }
    }

    @Override
    public void onHeadersDownloaded(Peer peer, Block lastBlock, int blocksLeft) {
        if (caughtUp || headersCaughtUp)
            return;

        if (blocksLeft == 0) {
            headersCaughtUp = true;
            if (lastHeadersPercent != 100) {
                lastHeadersPercent = 100;
                progress(calculatePercentage(lastHeadersPercent, lastPercent), blocksLeft, new Date(lastBlock.getTimeSeconds() * 1000));
            }
            doneHeaderDownload();
            future.set(peer.getBestHeight());
            return;
        }

        if (blocksLeft < 0 || originalHeadersLeft <= 0)
            return;

        double pct = 100.0 - (100.0 * (blocksLeft / (double) originalHeadersLeft));
        if ((int) pct != lastHeadersPercent) {
            progress(calculatePercentage(pct, lastPercent), blocksLeft, new Date(lastBlock.getTimeSeconds() * 1000));
            lastHeadersPercent = (int) pct;
        }
    }

    public void doneHeaderDownload() {

    }

    private double calculatePercentage(double percentHeaders, double percentBlocks) {
        double headersWeight = 0.3;
        double blocksWeight = 1.0 - headersWeight;
        if (!requiresHeaders) {
            headersWeight = 0;
            blocksWeight = 1.0;
        }
        return headersWeight * percentHeaders + blocksWeight * percentBlocks;
    }
}
