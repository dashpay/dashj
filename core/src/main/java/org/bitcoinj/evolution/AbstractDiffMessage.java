/*
 * Copyright 2022 Dash Core Group
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


package org.bitcoinj.evolution;

import org.bitcoinj.core.Context;
import org.bitcoinj.core.Message;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Base class for all masternode/quorum list difference messages
 *
 * Used by {@link AbstractQuorumState}
 */

public abstract class AbstractDiffMessage extends Message {
    private static final Logger log = LoggerFactory.getLogger(AbstractDiffMessage.class);

    public AbstractDiffMessage(NetworkParameters params) {
        super(params);
    }

    public AbstractDiffMessage(NetworkParameters params, byte [] payload, int offset) {
        super(params, payload, offset);
    }

    protected abstract String getShortName();

    public void dump(long startHeight, long endHeight) {
        if (!Utils.isAndroidRuntime() && Context.get().isDebugMode()) {
            try {
                File dumpFile = new File(getShortName() + "-" + params.getNetworkName() + "-" + startHeight + "-" + endHeight + ".dat");
                OutputStream stream = new FileOutputStream(dumpFile);
                stream.write(bitcoinSerialize());
                stream.close();
                log.info("dump successful");
            } catch (FileNotFoundException x) {
                log.warn("could not dump {} - file not found.", getShortName(), x);
            } catch (IOException x) {
                log.warn("could not dump {} - I/O error", getShortName(), x);
            }
        }
    }
}
