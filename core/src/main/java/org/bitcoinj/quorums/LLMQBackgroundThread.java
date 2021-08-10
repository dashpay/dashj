package org.bitcoinj.quorums;

import org.bitcoinj.core.Context;
import org.bitcoinj.store.BlockStoreException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by hashengineering on 5/1/19.
 */
public class LLMQBackgroundThread extends Thread {
    private static final Logger log = LoggerFactory.getLogger(LLMQBackgroundThread.class);

    Context context;
    public LLMQBackgroundThread(Context context) {
        this.context = context;
    }

    int debugTimer = 0;

    @Override
    public void run() {
        Context.propagate(context);
        log.info("starting LLMQBackgroundThread.run");
        if (context.signingManager == null) {
            // stop this thread if there is no signingManager
            return;
        }
        try {
            context.signingManager.addRecoveredSignatureListener(context.instantSendManager);
            context.signingManager.addRecoveredSignatureListener(context.chainLockHandler);
            while (!isInterrupted()) {
                boolean didWork = false;

                if(context.masternodeListManager.isSynced()) {
                    didWork |= context.instantSendManager.processPendingInstantSendLocks();

                    didWork |= context.signingManager.processPendingRecoveredSigs();

                    context.signingManager.cleanup();
                }

                if (!didWork) {
                    Thread.sleep(100);
                }

                debugTimer++;
                if(debugTimer % 400 == 0) {
                    log.info(context.instantSendManager.toString());
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
            context.signingManager.removeRecoveredSignatureListener(context.instantSendManager);
            context.signingManager.removeRecoveredSignatureListener(context.chainLockHandler);
        }
    }
}
