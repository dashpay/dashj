package org.bitcoinj.quorums;

import org.bitcoinj.core.Context;
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
        try {
            context.signingManager.addRecoveredSignatureListener(context.instantSendManager);
            context.signingManager.addRecoveredSignatureListener(context.chainLockHandler);
            while (!isInterrupted()) {
                boolean didWork = false;

                didWork |= context.instantSendManager.processPendingInstantSendLocks();

                didWork |= context.signingManager.processPendingRecoveredSigs();

                context.signingManager.cleanup();



                if (!didWork) {
                    Thread.sleep(100);
                }

                debugTimer++;
                if(debugTimer % 200 == 0) {
                    log.info(context.instantSendManager.toString());
                    if(debugTimer == 1000)
                        debugTimer = 0;
                }

            }
        } catch (InterruptedException x) {
            //let the thread stop
            context.signingManager.removeRecoveredSignatureListener(context.instantSendManager);
            context.signingManager.removeRecoveredSignatureListener(context.chainLockHandler);
        }
    }
}
