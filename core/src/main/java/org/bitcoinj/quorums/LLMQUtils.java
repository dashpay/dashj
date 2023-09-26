package org.bitcoinj.quorums;

import org.bitcoinj.core.AbstractBlockChain;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.core.UnsafeByteArrayOutputStream;
import org.bitcoinj.core.Utils;
import org.bitcoinj.crypto.BLSPublicKey;
import org.bitcoinj.crypto.BLSSignature;

import java.io.IOException;
import java.util.ArrayList;

import static java.lang.Math.max;
import static org.bitcoinj.core.SporkId.SPORK_21_QUORUM_ALL_CONNECTED;
import static org.bitcoinj.core.SporkId.SPORK_23_QUORUM_POSE;
import static org.bitcoinj.quorums.LLMQParameters.LLMQType.LLMQ_100_67;
import static org.bitcoinj.quorums.LLMQParameters.LLMQType.LLMQ_400_60;
import static org.bitcoinj.quorums.LLMQParameters.LLMQType.LLMQ_400_85;

public class LLMQUtils {
    static public Sha256Hash buildCommitmentHash(LLMQParameters.LLMQType llmqType, Sha256Hash blockHash, ArrayList<Boolean> validMembers, BLSPublicKey pubKey, Sha256Hash vvecHash)
    {
        try {
            UnsafeByteArrayOutputStream bos = new UnsafeByteArrayOutputStream();
            bos.write(llmqType.getValue());
            bos.write(blockHash.getReversedBytes());
            Utils.booleanArrayListToStream(validMembers, bos);
            pubKey.bitcoinSerialize(bos);
            bos.write(vvecHash.getReversedBytes());
            return Sha256Hash.wrap(Sha256Hash.hashTwice(bos.toByteArray()));
        } catch (IOException x) {
            throw new RuntimeException(x);
        }
    }


    static public Sha256Hash buildSignHash(int llmqType, Sha256Hash quorumHash, Sha256Hash id, Sha256Hash msgHash)
    {
        try {
            UnsafeByteArrayOutputStream bos = new UnsafeByteArrayOutputStream();
            bos.write(llmqType);
            bos.write(quorumHash.getReversedBytes());
            bos.write(id.getBytes());
            bos.write(msgHash.getReversedBytes());
            return Sha256Hash.wrap(Sha256Hash.hashTwice(bos.toByteArray()));
        } catch (IOException x) {
            throw new RuntimeException(x);
        }
    }

    static public Sha256Hash buildSignHash(LLMQParameters.LLMQType llmqType, Sha256Hash quorumHash, Sha256Hash id, Sha256Hash msgHash)
    {
        return buildSignHash(llmqType.getValue(), quorumHash, id, msgHash);
    }

    public static Sha256Hash buildSignHash(RecoveredSignature recoveredSignature) {
        return buildSignHash(recoveredSignature.llmqType, recoveredSignature.quorumHash, recoveredSignature.id,
                recoveredSignature.msgHash);
    }

    public static Sha256Hash buildLLMQBlockHash(LLMQParameters.LLMQType llmqType, Sha256Hash blockHash)
    {
        try {
            UnsafeByteArrayOutputStream bos = new UnsafeByteArrayOutputStream();
            bos.write(llmqType.getValue());
            bos.write(blockHash.getReversedBytes());
            return Sha256Hash.wrapReversed(Sha256Hash.hashTwice(bos.toByteArray()));
        } catch (IOException x) {
            throw new RuntimeException(x);
        }
    }

    public static Sha256Hash buildLLMQBlockHash(LLMQParameters.LLMQType type, int height, BLSSignature signature) {
        try {
            UnsafeByteArrayOutputStream stream = new UnsafeByteArrayOutputStream(5 + signature.getMessageSize());
            stream.write(type.getValue());
            Utils.uint32ToByteStreamLE(height, stream);
            signature.bitcoinSerialize(stream);
            return Sha256Hash.wrapReversed(Sha256Hash.hashTwice(stream.toByteArray()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static boolean evalSpork(LLMQParameters.LLMQType llmqType, long sporkValue) {
        if (sporkValue == 0) {
            return true;
        }
        return sporkValue == 1 && llmqType != LLMQ_100_67 && llmqType != LLMQ_400_60 && llmqType != LLMQ_400_85;
    }

    boolean isAllMembersConnectedEnabled(LLMQParameters.LLMQType llmqType) {
        return evalSpork(llmqType, Context.get().sporkManager.getSporkValue(SPORK_21_QUORUM_ALL_CONNECTED));
    }

    boolean isQuorumPoseEnabled(LLMQParameters.LLMQType llmqType) {
        return evalSpork(llmqType, Context.get().sporkManager.getSporkValue(SPORK_23_QUORUM_POSE));
    }

    public static boolean isQuorumRotationEnabled(Context context, NetworkParameters params, LLMQParameters.LLMQType type) {
        if (type != params.getLlmqDIP0024InstantSend()) {
            return false;
        }

        int bestHeight = max(context.headerChain != null ? context.headerChain.getBestChainHeight() : 0, context.blockChain.getBestChainHeight());
        boolean quorumRotationActive = bestHeight >= params.getDIP0024BlockHeight();
        return params.getLlmqDIP0024InstantSend() == type && quorumRotationActive;
    }

    public static boolean isQuorumRotationEnabled(AbstractBlockChain blockChain, NetworkParameters params, LLMQParameters.LLMQType type) {
        if (type != params.getLlmqDIP0024InstantSend()) {
            return false;
        }
        boolean quorumRotationActive = blockChain.getBestChainHeight() >= params.getDIP0024BlockHeight();
        return params.getLlmqDIP0024InstantSend() == type && quorumRotationActive;
    }

    public static boolean isQuorumRotationEnabled(StoredBlock block, NetworkParameters params, LLMQParameters.LLMQType type) {
        if (type != params.getLlmqDIP0024InstantSend()) {
            return false;
        }
        boolean quorumRotationActive = block.getHeight() >= params.getDIP0024BlockHeight();
        return params.getLlmqDIP0024InstantSend() == type && quorumRotationActive;
    }

    @Deprecated
    public static Sha256Hash calculateModifier(LLMQParameters llmqParameters, StoredBlock quorumBaseBlock) {
        UnsafeByteArrayOutputStream stream = new UnsafeByteArrayOutputStream(33);
        try {
            stream.write(llmqParameters.getType().getValue());
            stream.write(quorumBaseBlock.getHeader().getHash().getReversedBytes());
        } catch (IOException x) {
            throw new RuntimeException(x);
        }
        return Sha256Hash.twiceOf(stream.toByteArray());
    }

    static public Sha256Hash buildProTxDkgBlockHash(Sha256Hash proTxHash, Sha256Hash blockHash)
    {
        try {
            UnsafeByteArrayOutputStream bos = new UnsafeByteArrayOutputStream();
            bos.write(proTxHash.getReversedBytes());
            bos.write(blockHash.getReversedBytes());
            return Sha256Hash.wrapReversed(Sha256Hash.hashTwice(bos.toByteArray()));
        } catch (IOException x) {
            throw new RuntimeException(x);
        }
    }
}
