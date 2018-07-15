/*
 * Copyright 2013 Google Inc.
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

package org.bitcoinj.governance;

import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionBroadcast;

/**
 * A general interface which declares the ability to broadcast votes. This is implemented
 * by {@link org.bitcoinj.core.PeerGroup}.
 */
public interface GovernanceVoteBroadcaster {
    /** Broadcast the given transaction on the network */
    GovernanceVoteBroadcast broadcastGovernanceVote(final GovernanceVote vote);
}
