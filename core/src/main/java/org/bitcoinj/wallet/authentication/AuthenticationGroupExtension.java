/*
 * Copyright (c) 2023 Dash Core Group
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

package org.bitcoinj.wallet.authentication;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.protobuf.ByteString;
import org.bitcoinj.core.AbstractBlockChain;
import org.bitcoinj.core.BlockChain;
import org.bitcoinj.core.BloomFilter;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.KeyId;
import org.bitcoinj.core.MasternodeAddress;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.Utils;
import org.bitcoinj.core.VersionMessage;
import org.bitcoinj.crypto.BLSPublicKey;
import org.bitcoinj.crypto.ChildNumber;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.IDeterministicKey;
import org.bitcoinj.crypto.IKey;
import org.bitcoinj.crypto.KeyCrypter;
import org.bitcoinj.crypto.factory.BLSKeyFactory;
import org.bitcoinj.crypto.factory.ECKeyFactory;
import org.bitcoinj.crypto.factory.Ed25519KeyFactory;
import org.bitcoinj.crypto.factory.KeyFactory;
import org.bitcoinj.evolution.CreditFundingTransaction;
import org.bitcoinj.evolution.ProviderRegisterTx;
import org.bitcoinj.evolution.ProviderUpdateRegistarTx;
import org.bitcoinj.evolution.ProviderUpdateRevocationTx;
import org.bitcoinj.evolution.ProviderUpdateServiceTx;
import org.bitcoinj.evolution.listeners.CreditFundingTransactionEventListener;
import org.bitcoinj.script.Script;
import org.bitcoinj.utils.ListenerRegistration;
import org.bitcoinj.utils.Threading;
import org.bitcoinj.wallet.AbstractKeyChainGroupExtension;
import org.bitcoinj.wallet.AnyDeterministicKeyChain;
import org.bitcoinj.wallet.AnyKeyChainGroup;
import org.bitcoinj.wallet.AuthenticationKeyChain;
import org.bitcoinj.wallet.AuthenticationKeyChainFactory;
import org.bitcoinj.wallet.AuthenticationKeyChainGroup;
import org.bitcoinj.wallet.DerivationPathFactory;
import org.bitcoinj.wallet.DeterministicSeed;
import org.bitcoinj.wallet.Protos;
import org.bitcoinj.wallet.UnreadableWalletException;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.WalletTransaction;
import org.bitcoinj.wallet.listeners.AuthenticationKeyUsageEventListener;
import org.bouncycastle.crypto.params.KeyParameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.bitcoinj.evolution.ProviderRegisterTx.LEGACY_BLS_VERSION;

public class AuthenticationGroupExtension extends AbstractKeyChainGroupExtension {
    public static String EXTENSION_ID = "org.dashj.wallet.authentication";

    private static final Logger log = LoggerFactory.getLogger(AuthenticationGroupExtension.class);
    private AuthenticationKeyChainGroup keyChainGroup;
    private final HashMap<IKey, AuthenticationKeyUsage> keyUsage = Maps.newHashMap();

    private final CopyOnWriteArrayList<ListenerRegistration<CreditFundingTransactionEventListener>> creditFundingListeners
            = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<ListenerRegistration<AuthenticationKeyUsageEventListener>> usageListeners
            = new CopyOnWriteArrayList<>();

    public AuthenticationGroupExtension(Wallet wallet) {
        super(wallet);
        keyChainGroup = AuthenticationKeyChainGroup.authenticationBuilder(wallet.getParams()).build();
    }

    public AuthenticationGroupExtension(NetworkParameters params) {
        super(null);
        keyChainGroup = AuthenticationKeyChainGroup.authenticationBuilder(params).build();
    }

    @Override
    protected AnyKeyChainGroup getKeyChainGroup() {
        return keyChainGroup;
    }

    public boolean hasKeyChain(ImmutableList<ChildNumber> path) {
        if (keyChainGroup == null)
            return false;
        boolean hasPath = false;
        for (AnyDeterministicKeyChain chain : keyChainGroup.getDeterministicKeyChains()) {
            if (chain.getAccountPath().equals(path)) {
                hasPath = true;
                break;
            }
        }
        return hasPath;
    }

    public boolean missingAnyKeyChainTypes(EnumSet<AuthenticationKeyChain.KeyChainType> types) {
        for (AuthenticationKeyChain.KeyChainType type : types) {
            if (getKeyChain(type) == null) {
                return true;
            }
        }
        return false;
    }

    public void addKeyChain(DeterministicSeed seed, ImmutableList<ChildNumber> path, AuthenticationKeyChain.KeyChainType type) {
        checkState(!seed.isEncrypted());
        if (!hasKeyChain(path)) {
            keyChainGroup.addAndActivateHDChain(AuthenticationKeyChain.authenticationBuilder()
                        .seed(seed)
                        .type(type)
                        .accountPath(path)
                        .build());
        }
    }

    public void addKeyChains(NetworkParameters params, DeterministicSeed seed, EnumSet<AuthenticationKeyChain.KeyChainType> types) {
        checkState(!seed.isEncrypted());
        for (AuthenticationKeyChain.KeyChainType type : types) {
            if (getKeyChain(type) == null) {
                addKeyChain(seed, getDefaultPath(params, type), type);
            }
        }
    }

    public void addEncryptedKeyChain(DeterministicSeed seed, ImmutableList<ChildNumber> path, @Nonnull KeyParameter keyParameter, AuthenticationKeyChain.KeyChainType type) {
        checkNotNull(keyParameter);
        checkState(seed.isEncrypted());
        if (!hasKeyChain(path)) {
            if (seed.isEncrypted()) {
                seed = seed.decrypt(wallet.getKeyCrypter(), "", keyParameter);
            }
            AuthenticationKeyChain chain = AuthenticationKeyChain.authenticationBuilder()
                    .seed(seed)
                    .type(type)
                    .accountPath(path)
                    .build();
            AuthenticationKeyChain encryptedChain = chain.toEncrypted(wallet.getKeyCrypter(), keyParameter);
            keyChainGroup.addAndActivateHDChain(encryptedChain);
        }
    }

    public void addEncryptedKeyChains(NetworkParameters params, DeterministicSeed seed, @Nonnull KeyParameter keyParameter, EnumSet<AuthenticationKeyChain.KeyChainType> types) {
        checkState(seed.isEncrypted());
        checkNotNull(keyParameter);
        for (AuthenticationKeyChain.KeyChainType type : types) {
            if (getKeyChain(type) == null) {
                addEncryptedKeyChain(seed, getDefaultPath(params, type), keyParameter, type);
            }
        }
    }

    public void addNewKey(AuthenticationKeyChain.KeyChainType type, IDeterministicKey currentKey) {

        keyChainGroupLock.lock();
        try {
            keyChainGroup.addNewKey(type, currentKey);
            saveWallet();
        } finally {
            keyChainGroupLock.unlock();
        }
    }

    public IDeterministicKey currentKey(AuthenticationKeyChain.KeyChainType type) {
        return keyChainGroup.currentKey(type);
    }

    public HashMap<IKey, AuthenticationKeyUsage> getKeyUsage() {
        return keyUsage;
    }

    @Override
    public boolean supportsBloomFilters() {
        return true;
    }

    @Override
    public boolean supportsEncryption() {
        return true;
    }

    /**
     * Returns a Java package/class style name used to disambiguate this extension from others.
     */
    @Override
    public String getWalletExtensionID() {
        return EXTENSION_ID;
    }

    /**
     * If this returns true, the mandatory flag is set when the wallet is serialized and attempts to load it without
     * the extension being in the wallet will throw an exception. This method should not change its result during
     * the objects lifetime.
     */
    @Override
    public boolean isWalletExtensionMandatory() {
        return false;
    }

    /**
     * Returns bytes that will be saved in the wallet.
     */
    @Override
    public byte[] serializeWalletExtension() {
        Protos.AuthenticationGroupExtension.Builder builder = Protos.AuthenticationGroupExtension.newBuilder();

        for (AuthenticationKeyChain.KeyChainType type : AuthenticationKeyChain.KeyChainType.values()) {
            AuthenticationKeyChain chain = keyChainGroup.getKeyChain(type);
            if (chain != null) {
                Protos.ExtendedKeyChain.Builder extendedKeyChain = Protos.ExtendedKeyChain.newBuilder();
                extendedKeyChain.addAllKey(chain.serializeToProtobuf());
                Protos.ExtendedKeyChain.ExtendedKeyChainType extendedKeyChainType = getExtendedKeyChainType(type);
                extendedKeyChain.setType(extendedKeyChainType);
                extendedKeyChain.setKeyType(getExtendedKeyType(type));
                builder.addAuthenticationKeyChains(extendedKeyChain);
            }
        }

        for (AuthenticationKeyUsage usage : keyUsage.values()) {
            Protos.AuthenticationKeyUsage.Builder usageBuilder = Protos.AuthenticationKeyUsage.newBuilder();
            if (usage.getType() == AuthenticationKeyChain.KeyChainType.MASTERNODE_OPERATOR) {
                usageBuilder.setKeyOrKeyId(ByteString.copyFrom(usage.getKey().getSerializedPublicKey()));
            } else {
                usageBuilder.setKeyOrKeyId(ByteString.copyFrom(usage.getKey().getPubKeyHash()));
            }
            usageBuilder.setWhereUsed(ByteString.copyFrom(usage.getWhereUsed().getReversedBytes()));
            Protos.AuthenticationKeyUsage.AuthenticationKeyStatus status;
            switch (usage.getStatus()) {
                case CURRENT: status = Protos.AuthenticationKeyUsage.AuthenticationKeyStatus.CURRENT; break;
                case PREVIOUS: status = Protos.AuthenticationKeyUsage.AuthenticationKeyStatus.PREVIOUS; break;
                case REVOKED: status = Protos.AuthenticationKeyUsage.AuthenticationKeyStatus.REVOKED; break;
                case NEVER: status = Protos.AuthenticationKeyUsage.AuthenticationKeyStatus.NEVER; break;
                case UNKNOWN: default: status = Protos.AuthenticationKeyUsage.AuthenticationKeyStatus.UNKNOWN; break;
            }
            Protos.ExtendedKeyChain.ExtendedKeyChainType type = getExtendedKeyChainType(usage.getType());
            usageBuilder.setStatus(status);
            usageBuilder.setKeyType(type);
            if (usage.getAddress() != null) {
                Protos.PeerAddress.Builder peerAddressBuilder = Protos.PeerAddress.newBuilder();
                peerAddressBuilder.setIpAddress(ByteString.copyFrom(usage.getAddress().getAddr().getAddress()));
                peerAddressBuilder.setPort(usage.getAddress().getPort());
                peerAddressBuilder.setServices(VersionMessage.NODE_NETWORK);
                usageBuilder.setAddress(peerAddressBuilder);
            }
            if (usage.getType() == AuthenticationKeyChain.KeyChainType.MASTERNODE_OPERATOR) {
                usageBuilder.setLegacy(usage.isLegacy());
            }
            builder.addAuthenticationKeyUsage(usageBuilder);
        }
        return builder.build().toByteArray();
    }

    /**
     * Loads the contents of this object from the wallet.
     */
    @Override
    public void deserializeWalletExtension(Wallet containingWallet, byte[] data) throws Exception {
        wallet = containingWallet;
        KeyCrypter keyCrypter = wallet.getKeyCrypter();
        AuthenticationKeyChainFactory factory = new AuthenticationKeyChainFactory();
        Protos.AuthenticationGroupExtension walletExtension = Protos.AuthenticationGroupExtension.parseFrom(data);
        keyChainGroup = AuthenticationKeyChainGroup.authenticationBuilder(wallet.getParams()).keyCrypter(keyCrypter).build();

        // extended chains
        for (Protos.ExtendedKeyChain extendedKeyChain : walletExtension.getAuthenticationKeyChainsList()) {
            AuthenticationKeyChain.KeyChainType keyChainType = getKeyChainType(extendedKeyChain.getType());
            if (extendedKeyChain.getKeyCount() > 0) {
                KeyFactory keyFactory;
                switch (extendedKeyChain.getKeyType()) {
                    case ECDSA:
                        keyFactory = ECKeyFactory.get();
                        break;
                    case BLS:
                        keyFactory = BLSKeyFactory.get();
                        break;
                    case EDDSA:
                        keyFactory = Ed25519KeyFactory.get();
                        break;
                    default:
                        throw new UnreadableWalletException("Unknown extended key type found:" + extendedKeyChain.getKeyType());
                }
                List<AnyDeterministicKeyChain> chains = AuthenticationKeyChain.fromProtobuf(
                        extendedKeyChain.getKeyList(), keyCrypter, factory, keyFactory, AuthenticationKeyChain.requiresHardenedKeys(keyChainType));
                if (!chains.isEmpty()) {
                    AuthenticationKeyChain chain = (AuthenticationKeyChain)chains.get(0);
                    chain.setType(keyChainType);
                    addAndActivateHDChain(chain);
                }
            }
        }

        // usage
        for (Protos.AuthenticationKeyUsage usageProto : walletExtension.getAuthenticationKeyUsageList()) {
            byte[] keyOrKeyId = usageProto.getKeyOrKeyId().toByteArray();
            AuthenticationKeyChain.KeyChainType keyChainType = getKeyChainType(usageProto.getKeyType());
            AuthenticationKeyStatus status;
            switch (usageProto.getStatus()) {
                case CURRENT: status = AuthenticationKeyStatus.CURRENT; break;
                case REVOKED: status = AuthenticationKeyStatus.REVOKED; break;
                case PREVIOUS: status = AuthenticationKeyStatus.PREVIOUS; break;
                case NEVER: status = AuthenticationKeyStatus.NEVER; break;
                default: status = AuthenticationKeyStatus.UNKNOWN; break;
            }
            Sha256Hash whereUsed = Sha256Hash.wrapReversed(usageProto.getWhereUsed().toByteArray());
            IKey key;
            if (keyChainType == AuthenticationKeyChain.KeyChainType.MASTERNODE_OPERATOR) {
                // remove first byte
                keyOrKeyId = Arrays.copyOfRange(keyOrKeyId, 1, BLSPublicKey.BLS_CURVE_PUBKEY_SIZE + 1);
                key = findKeyFromPubKey(keyOrKeyId);
            } else {
                key = findKeyFromPubKeyHash(keyOrKeyId, Script.ScriptType.P2PKH);
            }
            MasternodeAddress address = null;
            if (usageProto.hasAddress()) {
                InetAddress inetAddress = InetAddress.getByAddress(usageProto.getAddress().getIpAddress().toByteArray());
                InetSocketAddress inetSocketAddress = new InetSocketAddress(inetAddress, usageProto.getAddress().getPort());
                address = new MasternodeAddress(inetSocketAddress);
            }
            boolean legacy = usageProto.hasLegacy() && usageProto.getLegacy();
            AuthenticationKeyUsage usage = new AuthenticationKeyUsage(key, keyChainType, status, whereUsed, address, legacy);
            keyUsage.put(key, usage);
        }
    }

    @Override
    public void processTransaction(Transaction tx, StoredBlock block, BlockChain.NewBlockType blockType) {
        if (tx.getVersion() >= Transaction.SPECIAL_VERSION) {
            switch (tx.getType()) {
                case TRANSACTION_PROVIDER_REGISTER:
                    processRegistration(tx, (ProviderRegisterTx) tx.getExtraPayloadObject());
                    break;
                case TRANSACTION_PROVIDER_UPDATE_SERVICE:
                    processUpdateService((ProviderUpdateServiceTx) tx.getExtraPayloadObject());
                    break;
                case TRANSACTION_PROVIDER_UPDATE_REGISTRAR:
                    processUpdateRegistrar(tx, (ProviderUpdateRegistarTx) tx.getExtraPayloadObject());
                    break;
                case TRANSACTION_PROVIDER_UPDATE_REVOKE:
                    processRevoke((ProviderUpdateRevocationTx) tx.getExtraPayloadObject());
                default:
                    break;
            }
        } else {
            if(CreditFundingTransaction.isCreditFundingTransaction(tx)) {
                CreditFundingTransaction cftx = getCreditFundingTransaction(tx);
                if (cftx != null)
                    queueOnCreditFundingEvent(cftx, block, blockType);
            }
        }
    }

    private void processRevoke(ProviderUpdateRevocationTx providerUpdateRevocationTx) {
        // used to revoke the operator key
        // 1. find the operator key for the proRegTxHash
        // 2. mark as revoked
        Sha256Hash proTxHash = providerUpdateRevocationTx.getProTxHash();

        AuthenticationKeyUsage operatorKeyUsage = getCurrentOperatorKeyUsage(proTxHash);
        if (operatorKeyUsage != null) {
            log.info("protx revoke: operator key {}", operatorKeyUsage.getKey());
            operatorKeyUsage.setStatus(AuthenticationKeyStatus.REVOKED);
            queueOnUsageEvent(Lists.newArrayList(operatorKeyUsage));
        }
    }

    private void processUpdateService(ProviderUpdateServiceTx providerUpdateServiceTx) {
        for (Map.Entry<IKey, AuthenticationKeyUsage> entry : keyUsage.entrySet()) {
            if (entry.getValue().getWhereUsed().equals(providerUpdateServiceTx.getProTxHash())) {
                entry.getValue().setAddress(providerUpdateServiceTx.getAddress());
            }
        }
    }

    private AuthenticationKeyUsage getCurrentOperatorKeyUsage(Sha256Hash proTxHash) {
        for (AuthenticationKeyUsage usage : keyUsage.values()) {
            if (usage.getType() == AuthenticationKeyChain.KeyChainType.MASTERNODE_OPERATOR) {
                if (usage.getWhereUsed().equals(proTxHash)) {
                    return usage;
                }
            }
        }
        return null;
    }

    public IDeterministicKey freshKey(AuthenticationKeyChain.KeyChainType type) {
        return freshKeys(type, 1).get(0);
    }

    public List<IDeterministicKey> freshKeys(AuthenticationKeyChain.KeyChainType type, int numberOfKeys) {
        List<IDeterministicKey> keys;
        keyChainGroupLock.lock();
        try {
            keys = keyChainGroup.freshKeys(type, numberOfKeys);
        } finally {
            keyChainGroupLock.unlock();
        }
        // Do we really need an immediate hard save? Arguably all this is doing is saving the 'current' key
        // and that's not quite so important, so we could coalesce for more performance.
        saveWallet();
        return keys;
    }


    private void processRegistration(Transaction tx, ProviderRegisterTx providerRegisterTx) {
        KeyId voting = providerRegisterTx.getKeyIDVoting();
        KeyId owner = providerRegisterTx.getKeyIDOwner();
        BLSPublicKey operator = providerRegisterTx.getPubkeyOperator();
        KeyId platformNodeId = providerRegisterTx.getPlatformNodeID();

        IKey votingKey = findKeyFromPubKeyHash(voting.getBytes(), Script.ScriptType.P2PKH);
        IKey ownerKey = findKeyFromPubKeyHash(owner.getBytes(), Script.ScriptType.P2PKH);
        IKey operatorKey = findKeyFromPubKey(operator.serialize(true));
        if (operatorKey == null)
            operatorKey = findKeyFromPubKey(operator.serialize(false));

        IKey platformKey = platformNodeId != null ? findKeyFromPubKeyHash(platformNodeId.getBytes(), Script.ScriptType.P2PKH) : null;
        if (platformKey == null) {
            platformKey = platformNodeId != null ? findKeyFromPubKeyHash(Utils.reverseBytes(platformNodeId.getBytes()), Script.ScriptType.P2PKH) : null;
        }
        List<AuthenticationKeyUsage> updates = Lists.newArrayList();

        // voting
        if (votingKey != null) {
            log.info("protx register: found voting key {}", votingKey);
            AuthenticationKeyUsage votingkeyUsage = AuthenticationKeyUsage.createVoting(votingKey, tx.getTxId(), providerRegisterTx.getAddress());
            keyUsage.put(votingKey, votingkeyUsage);
            keyChainGroup.markPubKeyHashAsUsed(voting.getBytes());
            updates.add(votingkeyUsage);
        }

        if (ownerKey != null) {
            log.info("protx register: found owner key {}", votingKey);
            AuthenticationKeyUsage ownerKeyUsage = AuthenticationKeyUsage.createOwner(ownerKey, tx.getTxId(), providerRegisterTx.getAddress());
            keyUsage.put(ownerKey, ownerKeyUsage);
            keyChainGroup.markPubKeyHashAsUsed(owner.getBytes());
            updates.add(ownerKeyUsage);
        }

        if (operatorKey != null) {
            log.info("protx register: found operator key {}", votingKey);
            boolean legacy = providerRegisterTx.getVersion() == LEGACY_BLS_VERSION;
            AuthenticationKeyUsage operatorKeyUsage = AuthenticationKeyUsage.createOperator(operatorKey, legacy, tx.getTxId(), providerRegisterTx.getAddress());
            keyUsage.put(operatorKey, operatorKeyUsage);
            keyChainGroup.markPubKeyAsUsed(operatorKey.getPubKey());
            updates.add(operatorKeyUsage);
        }

        if (platformKey != null) {
            log.info("protx register: found platform node key {}", votingKey);
            AuthenticationKeyUsage platformKeyUsage = AuthenticationKeyUsage.createPlatformNodeId(platformKey, tx.getTxId(), providerRegisterTx.getAddress());
            keyUsage.put(platformKey, platformKeyUsage);
            keyChainGroup.markPubKeyHashAsUsed(platformKey.getPubKey());
            updates.add(platformKeyUsage);
        }
        queueOnUsageEvent(updates);
    }

    private void processUpdateRegistrar(Transaction tx, ProviderUpdateRegistarTx providerUpdateRegistarTx) {
        KeyId voting = providerUpdateRegistarTx.getKeyIDVoting();
        BLSPublicKey operator = providerUpdateRegistarTx.getPubkeyOperator();

        IKey votingKey = findKeyFromPubKeyHash(voting.getBytes(), Script.ScriptType.P2PKH);
        IKey operatorKey = findKeyFromPubKey(operator.serialize(true));
        if (operatorKey == null)
            operatorKey = findKeyFromPubKey(operator.serialize(false));
        // TODO: find BLS

        // there could be a previous usage of voting and operator keys
        AuthenticationKeyUsage previousVotingKeyUsage = keyUsage.get(votingKey);
        MasternodeAddress address = null;
        if (previousVotingKeyUsage != null) {
            previousVotingKeyUsage.setStatus(AuthenticationKeyStatus.PREVIOUS);
            address = previousVotingKeyUsage.getAddress();
        }
        AuthenticationKeyUsage previousOperatorKeyUsage = keyUsage.get(operatorKey);
        if (previousOperatorKeyUsage != null) {
            previousOperatorKeyUsage.setStatus(AuthenticationKeyStatus.PREVIOUS);
            if (address == null)
                address = previousOperatorKeyUsage.getAddress();
        }
        List<AuthenticationKeyUsage> updates = Lists.newArrayList();

        // voting
        if (votingKey != null) {
            log.info("protx update register: found voting key {}", votingKey);
            AuthenticationKeyUsage votingkeyUsage = AuthenticationKeyUsage.createVoting(votingKey, tx.getTxId(), address);
            keyUsage.put(votingKey, votingkeyUsage);
            keyChainGroup.markPubKeyHashAsUsed(voting.getBytes());
            updates.add(votingkeyUsage);
        }

        // operator
        if (operatorKey != null) {
            log.info("protx update register: found operator key {}", votingKey);
            boolean legacy = providerUpdateRegistarTx.getCurrentVersion() == LEGACY_BLS_VERSION;
            AuthenticationKeyUsage operatorKeyUsage = AuthenticationKeyUsage.createOperator(operatorKey, legacy, tx.getTxId(), address);
            keyUsage.put(operatorKey, operatorKeyUsage);
            keyChainGroup.markPubKeyAsUsed(operatorKey.getPubKey());
            updates.add(operatorKeyUsage);
        }
        queueOnUsageEvent(updates);
    }

    @Override
    public String toString(boolean includeLookahead, boolean includePrivateKeys, @Nullable KeyParameter aesKey) {
        StringBuilder builder = new StringBuilder();
        builder.append(super.toString(includeLookahead, includePrivateKeys, aesKey));
        builder.append("\n").append("Authentication Key Usage").append("\n");
        NetworkParameters params;
        if (wallet != null)
            params = wallet.getParams();
        else params = Context.get().getParams();
        for (AuthenticationKeyUsage usage : keyUsage.values()) {
            builder.append(usage.toString(params)).append("\n");
        }
        return builder.toString();
    }

    @Override
    public boolean hasSpendableKeys() {
        return false;
    }

    private static Protos.ExtendedKeyChain.ExtendedKeyChainType getExtendedKeyChainType(AuthenticationKeyChain.KeyChainType keyChainType) {
        switch (keyChainType) {
            case MASTERNODE_OWNER:
                return Protos.ExtendedKeyChain.ExtendedKeyChainType.MASTERNODE_OWNER;
            case MASTERNODE_VOTING:
                return Protos.ExtendedKeyChain.ExtendedKeyChainType.MASTERNODE_VOTING;
            case MASTERNODE_OPERATOR:
                return Protos.ExtendedKeyChain.ExtendedKeyChainType.MASTERNODE_OPERATOR;
            case MASTERNODE_PLATFORM_OPERATOR:
                return Protos.ExtendedKeyChain.ExtendedKeyChainType.MASTERNODE_PLATFORM_OPERATOR;
            case BLOCKCHAIN_IDENTITY:
                return Protos.ExtendedKeyChain.ExtendedKeyChainType.BLOCKCHAIN_IDENTITY;
            case BLOCKCHAIN_IDENTITY_FUNDING:
                return Protos.ExtendedKeyChain.ExtendedKeyChainType.BLOCKCHAIN_IDENTITY_FUNDING;
            case BLOCKCHAIN_IDENTITY_TOPUP:
                return Protos.ExtendedKeyChain.ExtendedKeyChainType.BLOCKCHAIN_IDENTITY_TOPUP;
            case INVITATION_FUNDING:
                return Protos.ExtendedKeyChain.ExtendedKeyChainType.INVITATION_FUNDING;
            case MASTERNODE_HOLDINGS:
                return Protos.ExtendedKeyChain.ExtendedKeyChainType.MASTERNODE_HOLDINGS;
            default:
                return Protos.ExtendedKeyChain.ExtendedKeyChainType.INVALID;
        }
    }

    private static AuthenticationKeyChain.KeyChainType getKeyChainType(Protos.ExtendedKeyChain.ExtendedKeyChainType type) {
        switch(type) {
            case BLOCKCHAIN_IDENTITY:
                return AuthenticationKeyChain.KeyChainType.BLOCKCHAIN_IDENTITY;
            case BLOCKCHAIN_IDENTITY_FUNDING:
                return AuthenticationKeyChain.KeyChainType.BLOCKCHAIN_IDENTITY_FUNDING;
            case BLOCKCHAIN_IDENTITY_TOPUP:
                return AuthenticationKeyChain.KeyChainType.BLOCKCHAIN_IDENTITY_TOPUP;
            case MASTERNODE_OWNER:
                return AuthenticationKeyChain.KeyChainType.MASTERNODE_OWNER;
            case MASTERNODE_VOTING:
                return AuthenticationKeyChain.KeyChainType.MASTERNODE_VOTING;
            case MASTERNODE_OPERATOR:
                return AuthenticationKeyChain.KeyChainType.MASTERNODE_OPERATOR;
            case MASTERNODE_HOLDINGS:
                return AuthenticationKeyChain.KeyChainType.MASTERNODE_HOLDINGS;
            case INVITATION_FUNDING:
                return AuthenticationKeyChain.KeyChainType.INVITATION_FUNDING;
            case MASTERNODE_PLATFORM_OPERATOR:
                return AuthenticationKeyChain.KeyChainType.MASTERNODE_PLATFORM_OPERATOR;
            default:
                return AuthenticationKeyChain.KeyChainType.INVALID_KEY_CHAIN;
        }
    }

    private static Protos.ExtendedKeyChain.KeyType getExtendedKeyType(AuthenticationKeyChain.KeyChainType keyChainType) {
        switch (keyChainType) {
            case MASTERNODE_OWNER:
            case MASTERNODE_VOTING:
            case BLOCKCHAIN_IDENTITY:
            case BLOCKCHAIN_IDENTITY_FUNDING:
            case BLOCKCHAIN_IDENTITY_TOPUP:
            case INVITATION_FUNDING:
            case MASTERNODE_HOLDINGS:
            default:
                return Protos.ExtendedKeyChain.KeyType.ECDSA;
            case MASTERNODE_OPERATOR:
                return Protos.ExtendedKeyChain.KeyType.BLS;
            case MASTERNODE_PLATFORM_OPERATOR:
                return Protos.ExtendedKeyChain.KeyType.EDDSA;
        }
    }

    private static ImmutableList<ChildNumber> getDefaultPath(NetworkParameters params, AuthenticationKeyChain.KeyChainType keyChainType) {
        DerivationPathFactory factory = DerivationPathFactory.get(params);
        switch (keyChainType) {
            case MASTERNODE_OWNER:
                return factory.masternodeOwnerDerivationPath();
            case MASTERNODE_VOTING:
                return factory.masternodeVotingDerivationPath();
            case BLOCKCHAIN_IDENTITY:
                return factory.blockchainIdentityECDSADerivationPath();
            case BLOCKCHAIN_IDENTITY_FUNDING:
                return factory.blockchainIdentityRegistrationFundingDerivationPath();
            case BLOCKCHAIN_IDENTITY_TOPUP:
                return factory.blockchainIdentityTopupFundingDerivationPath();
            case INVITATION_FUNDING:
                return factory.identityInvitationFundingDerivationPath();
            case MASTERNODE_HOLDINGS:
                return factory.masternodeHoldingsDerivationPath();
            case MASTERNODE_OPERATOR:
                return factory.masternodeOperatorDerivationPath();
            case MASTERNODE_PLATFORM_OPERATOR:
                return factory.masternodePlatformDerivationPath();
            default:
                throw new IllegalArgumentException();
        }
    }

    HashMap<Sha256Hash, CreditFundingTransaction> mapCreditFundingTxs = new HashMap<>();

    /**
     * @return list of credit funding transactions found in the wallet.
     */

    public List<CreditFundingTransaction> getCreditFundingTransactions() {
        mapCreditFundingTxs.clear();
        ArrayList<CreditFundingTransaction> txs = new ArrayList<>(1);
        for(WalletTransaction wtx : wallet.getWalletTransactions()) {
            Transaction tx = wtx.getTransaction();
            if(CreditFundingTransaction.isCreditFundingTransaction(tx)) {
                CreditFundingTransaction cftx = getCreditFundingTransaction(tx);
                if (cftx != null) {
                    txs.add(cftx);
                    mapCreditFundingTxs.put(cftx.getTxId(), cftx);
                }
            }
        }
        return txs;
    }

    public AuthenticationKeyChain getKeyChain(AuthenticationKeyChain.KeyChainType type) {
        return keyChainGroup.getKeyChain(type);
    }

    public AuthenticationKeyChain getIdentityKeyChain() {
        return keyChainGroup.getKeyChain(AuthenticationKeyChain.KeyChainType.BLOCKCHAIN_IDENTITY);
    }

    public AuthenticationKeyChain getIdentityTopupKeyChain() {
        return keyChainGroup.getKeyChain(AuthenticationKeyChain.KeyChainType.BLOCKCHAIN_IDENTITY_TOPUP);
    }

    public AuthenticationKeyChain getIdentityFundingKeyChain() {
        return keyChainGroup.getKeyChain(AuthenticationKeyChain.KeyChainType.BLOCKCHAIN_IDENTITY_TOPUP);
    }

    public AuthenticationKeyChain getInvitationFundingKeyChain() {
        return keyChainGroup.getKeyChain(AuthenticationKeyChain.KeyChainType.INVITATION_FUNDING);
    }

    public List<CreditFundingTransaction> getIdentityFundingTransactions() {
        return getFundingTransactions(getIdentityFundingKeyChain());
    }

    public List<CreditFundingTransaction> getTopupFundingTransactions() {
        return getFundingTransactions(getIdentityTopupKeyChain());
    }

    public List<CreditFundingTransaction> getInvitationFundingTransactions() {
        return getFundingTransactions(getInvitationFundingKeyChain());
    }

    private List<CreditFundingTransaction> getFundingTransactions(AuthenticationKeyChain chain) {
        ArrayList<CreditFundingTransaction> txs = new ArrayList<>(1);
        List<CreditFundingTransaction> allTxs = getCreditFundingTransactions();

        for (CreditFundingTransaction cftx : allTxs) {
            if(chain.findKeyFromPubHash(cftx.getCreditBurnPublicKeyId().getBytes()) != null) {
                txs.add(cftx);
            }
        }
        return txs;
    }

    /**
     * Get a CreditFundingTransaction object for a specific transaction
     */
    public CreditFundingTransaction getCreditFundingTransaction(Transaction tx) {
        if (getIdentityFundingKeyChain() == null)
            return null;
        if (mapCreditFundingTxs.containsKey(tx.getTxId()))
            return mapCreditFundingTxs.get(tx.getTxId());

        CreditFundingTransaction cftx = new CreditFundingTransaction(tx);

        // set some internal data for the transaction
        DeterministicKey publicKey = (DeterministicKey) getIdentityFundingKeyChain().getKeyByPubKeyHash(cftx.getCreditBurnPublicKeyId().getBytes());

        if (publicKey == null)
            publicKey = (DeterministicKey) getIdentityTopupKeyChain().getKeyByPubKeyHash(cftx.getCreditBurnPublicKeyId().getBytes());

        if (publicKey == null)
            publicKey = (DeterministicKey) getInvitationFundingKeyChain().getKeyByPubKeyHash(cftx.getCreditBurnPublicKeyId().getBytes());

        if(publicKey != null)
            cftx.setCreditBurnPublicKeyAndIndex(publicKey, publicKey.getChildNumber().num());
        else log.error("Cannot find " + KeyId.fromBytes(cftx.getCreditBurnPublicKeyId().getBytes()) + " in the wallet");

        mapCreditFundingTxs.put(cftx.getTxId(), cftx);
        return cftx;
    }

    protected void queueOnCreditFundingEvent(final CreditFundingTransaction tx, StoredBlock block,
                                             BlockChain.NewBlockType blockType) {
        for (final ListenerRegistration<CreditFundingTransactionEventListener> registration : creditFundingListeners) {
            registration.executor.execute(new Runnable() {
                @Override
                public void run() {
                    registration.listener.onTransactionReceived(tx, block, blockType);
                }
            });
        }
    }

    /**
     * Adds an credit funding event listener object. Methods on this object are called when
     * a credit funding transaction is created or received.
     */
    public void addCreditFundingEventListener(CreditFundingTransactionEventListener listener) {
        addCreditFundingEventListener(Threading.USER_THREAD, listener);
    }

    /**
     * Adds an credit funding event listener object. Methods on this object are called when
     * a credit funding transaction is created or received.
     */
    public void addCreditFundingEventListener(Executor executor, CreditFundingTransactionEventListener listener) {
        // This is thread safe, so we don't need to take the lock.
        creditFundingListeners.add(new ListenerRegistration<>(listener, executor));
    }

    /**
     * Removes the given event listener object. Returns true if the listener was removed, false if that listener
     * was never added.
     */
    public boolean removeCreditFundingEventListener(CreditFundingTransactionEventListener listener) {
        return ListenerRegistration.removeFromList(listener, creditFundingListeners);
    }

    private HashSet<Sha256Hash> getProTxSet() {
        HashSet<Sha256Hash> proTxSet = new HashSet<>();
        for (AuthenticationKeyUsage usage : keyUsage.values()) {
            proTxSet.add(usage.getWhereUsed());
        }
        return proTxSet;
    }

    @Override
    public BloomFilter getBloomFilter(int size, double falsePositiveRate, long nTweak) {
        BloomFilter filter = super.getBloomFilter(size, falsePositiveRate, nTweak);
        HashSet<Sha256Hash> proTxSet = getProTxSet();
        for (Sha256Hash proTxHash : proTxSet) {
            filter.insert(proTxHash.getReversedBytes());
        }
        return filter;
    }

    @Override
    public int getBloomFilterElementCount() {
        int count = super.getBloomFilterElementCount();
        count += getProTxSet().size();
        return count;
    }

    @Override
    public boolean isTransactionRevelant(Transaction tx) {
        switch (tx.getType()) {
            case TRANSACTION_PROVIDER_REGISTER:
                return isProTxRegisterRevelant((ProviderRegisterTx)tx.getExtraPayloadObject());
            case TRANSACTION_PROVIDER_UPDATE_REGISTRAR:
                return isProTxUpdateRegistrarRevelant((ProviderUpdateRegistarTx) tx.getExtraPayloadObject());
            case TRANSACTION_PROVIDER_UPDATE_REVOKE:
                return isProTxRevokeRevelant((ProviderUpdateRevocationTx) tx.getExtraPayloadObject());
            case TRANSACTION_PROVIDER_UPDATE_SERVICE:
                return isProTxUpdateServiceRevelant((ProviderUpdateServiceTx) tx.getExtraPayloadObject());
        }
        return false;
    }

    private boolean isProTxHashRevelant(Sha256Hash proTxHash) {
        for (AuthenticationKeyUsage usage : keyUsage.values()) {
            if (usage.getWhereUsed().equals(proTxHash))
                return true;
        }
        return false;
    }

    private boolean isProTxRevokeRevelant(ProviderUpdateRevocationTx revocationTx) {
        return isProTxHashRevelant(revocationTx.getProTxHash());
    }

    private boolean isProTxUpdateRegistrarRevelant(ProviderUpdateRegistarTx updateRegistarTx) {
        return findKeyFromPubKeyHash(updateRegistarTx.getKeyIDVoting().getBytes(), Script.ScriptType.P2PKH) != null ||
                findKeyFromPubKey(updateRegistarTx.getPubkeyOperator().serialize(false)) != null ||
                findKeyFromPubKey(updateRegistarTx.getPubkeyOperator().serialize(true)) != null;
    }

    private boolean isProTxUpdateServiceRevelant(ProviderUpdateServiceTx updateServiceTx) {
        return isProTxHashRevelant(updateServiceTx.getProTxHash());
    }

    private boolean isProTxRegisterRevelant(ProviderRegisterTx providerRegisterTx) {
        return findKeyFromPubKeyHash(providerRegisterTx.getKeyIDOwner().getBytes(), Script.ScriptType.P2PKH) != null ||
                findKeyFromPubKeyHash(providerRegisterTx.getKeyIDVoting().getBytes(), Script.ScriptType.P2PKH) != null ||
                findKeyFromPubKey(providerRegisterTx.getPubkeyOperator().serialize(false)) != null ||
                findKeyFromPubKey(providerRegisterTx.getPubkeyOperator().serialize(true)) != null;
    }

    protected void queueOnUsageEvent(final List<AuthenticationKeyUsage> usage) {
        for (final ListenerRegistration<AuthenticationKeyUsageEventListener> registration : usageListeners) {
            registration.executor.execute(new Runnable() {
                @Override
                public void run() {
                    registration.listener.onAuthenticationKeyUsageEvent(usage);
                }
            });
        }
    }

    /**
     * Adds an credit funding event listener object. Methods on this object are called when
     * a credit funding transaction is created or received.
     */
    public void addAuthenticationKeyUsageEventListener(AuthenticationKeyUsageEventListener listener) {
        addAuthenticationKeyUsageEventListener(Threading.USER_THREAD, listener);
    }

    /**
     * Adds an credit funding event listener object. Methods on this object are called when
     * a credit funding transaction is created or received.
     */
    public void addAuthenticationKeyUsageEventListener(Executor executor, AuthenticationKeyUsageEventListener listener) {
        // This is thread safe, so we don't need to take the lock.
        usageListeners.add(new ListenerRegistration<>(listener, executor));
    }

    /**
     * Removes the given event listener object. Returns true if the listener was removed, false if that listener
     * was never added.
     */
    public boolean removeAuthenticationKeyUsageEventListener(AuthenticationKeyUsageEventListener listener) {
        return ListenerRegistration.removeFromList(listener, usageListeners);
    }

    public void reset() {
        keyUsage.clear();
        if (wallet != null) {
           Set<Transaction> transactionSet = wallet.getTransactions(false);
           List<Transaction> transactionList = Lists.newArrayList(transactionSet);
           transactionList.sort((transaction1, transaction2) -> (int) (transaction1.getUpdateTime().getTime() - transaction2.getUpdateTime().getTime()));

           for (Transaction tx : transactionList) {
               processTransaction(tx, null, AbstractBlockChain.NewBlockType.BEST_CHAIN);
           }
        }
    }
}
