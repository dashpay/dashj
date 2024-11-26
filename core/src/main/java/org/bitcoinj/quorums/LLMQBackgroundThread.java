package org.bitcoinj.quorums;

import org.bitcoinj.core.Context;
import org.bitcoinj.evolution.SimplifiedMasternodeListManager;
import org.bitcoinj.store.BlockStoreException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 * Created by hashengineering on 5/1/19.
 */

/**
 * This class is a background thread that will process {@link InstantSendLock}s.  Processing locks
 * includes verifying {@link InstantSendLock} signatures and notifying {@link org.bitcoinj.core.TransactionConfidence}
 * listeners.
 *
 * ChainLocks are handled by the {@link ChainLocksHandler} class immediately after they are received.
 */

public class LLMQBackgroundThread extends Thread {
    private static final Logger log = LoggerFactory.getLogger(LLMQBackgroundThread.class);

    Context context;
    protected InstantSendManager instantSendManager;
    protected ChainLocksHandler chainLocksHandler;
    protected SigningManager signingManager;
    protected SimplifiedMasternodeListManager masternodeListManager;
    public LLMQBackgroundThread(Context context, InstantSendManager instantSendManager,
                                ChainLocksHandler chainLocksHandler, SigningManager signingManager,
                                SimplifiedMasternodeListManager masternodeListManager) {
        this.context = context;
        this.instantSendManager = instantSendManager;
        this.chainLocksHandler = chainLocksHandler;
        this.signingManager = signingManager;
        this.masternodeListManager = masternodeListManager;
    }

    int debugTimer = 0;

    @Override
    public void run() {
        Context.propagate(context);
        log.info("starting LLMQBackgroundThread.run");
        if (signingManager == null) {
            // stop this thread if there is no signing manager
            return;
        }
        try {
            signingManager.addRecoveredSignatureListener(instantSendManager);
            signingManager.addRecoveredSignatureListener(chainLocksHandler);
            while (!isInterrupted()) {
                boolean didWork = false;

                // only the DIP24 lists need to be synced for this to work
                if (masternodeListManager.isSyncedForInstantSend()) {
                    didWork = instantSendManager.processPendingInstantSendLocks();

                    didWork |= signingManager.processPendingRecoveredSigs();

                    signingManager.cleanup();
                }

                if (!didWork) {
                    Thread.sleep(100);
                }

                debugTimer++;
                if(debugTimer % 400 == 0) {
                    log.info("{}", instantSendManager);
                    if(debugTimer == 2000)
                        debugTimer = 0;
                }

            }
        } catch (BlockStoreException x) {
            log.info("stopping LLMQBackgroundThread via BlockStoreException");
        } catch (InterruptedException x) {
            log.info("stopping LLMQBackgroundThread via InterruptedException");
            //let the thread stop
        } finally {
            signingManager.removeRecoveredSignatureListener(instantSendManager);
            signingManager.removeRecoveredSignatureListener(chainLocksHandler);
        }
    }
}
