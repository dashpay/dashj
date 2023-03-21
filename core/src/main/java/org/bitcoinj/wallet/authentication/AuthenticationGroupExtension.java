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
import com.google.common.collect.Maps;
import com.google.protobuf.ByteString;
import org.bitcoinj.core.KeyId;
import org.bitcoinj.core.MasternodeAddress;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.VersionMessage;
import org.bitcoinj.crypto.BLSPublicKey;
import org.bitcoinj.crypto.ChildNumber;
import org.bitcoinj.crypto.IDeterministicKey;
import org.bitcoinj.crypto.IKey;
import org.bitcoinj.crypto.KeyCrypter;
import org.bitcoinj.crypto.factory.BLSKeyFactory;
import org.bitcoinj.crypto.factory.ECKeyFactory;
import org.bitcoinj.crypto.factory.Ed25519KeyFactory;
import org.bitcoinj.crypto.factory.KeyFactory;
import org.bitcoinj.evolution.ProviderRegisterTx;
import org.bitcoinj.evolution.ProviderUpdateRegistarTx;
import org.bitcoinj.evolution.ProviderUpdateRevocationTx;
import org.bitcoinj.script.Script;
import org.bitcoinj.wallet.AbstractKeyChainGroupExtension;
import org.bitcoinj.wallet.AnyDeterministicKeyChain;
import org.bitcoinj.wallet.AnyKeyChainGroup;
import org.bitcoinj.wallet.AuthenticationKeyChain;
import org.bitcoinj.wallet.AuthenticationKeyChainFactory;
import org.bitcoinj.wallet.AuthenticationKeyChainGroup;
import org.bitcoinj.wallet.DeterministicSeed;
import org.bitcoinj.wallet.Protos;
import org.bitcoinj.wallet.UnreadableWalletException;
import org.bitcoinj.wallet.Wallet;
import org.bouncycastle.crypto.params.KeyParameter;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.bitcoinj.evolution.ProviderRegisterTx.LEGACY_BLS_VERSION;

public class AuthenticationGroupExtension extends AbstractKeyChainGroupExtension {
    private final AuthenticationKeyChainGroup keyChainGroup;
    private final HashMap<IKey, AuthenticationKeyUsage> keyUsage = Maps.newHashMap();

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
        return "org.dashj.wallet.authentication";
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
                usageBuilder.setKeyOrKeyId(ByteString.copyFrom(usage.getKey().getPubKey()));
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
    public void processTransaction(Transaction tx) {
        if (tx.getVersion() >= Transaction.SPECIAL_VERSION) {
            switch (tx.getType()) {
                case TRANSACTION_PROVIDER_REGISTER:
                    processRegistration(tx, (ProviderRegisterTx) tx.getExtraPayloadObject());
                    break;
                case TRANSACTION_PROVIDER_UPDATE_SERVICE:
                    // no keys are updated in this transaction
                    // TODO -- add IP address to usage!
                    break;
                case TRANSACTION_PROVIDER_UPDATE_REGISTRAR:
                    processUpdateRegistrar(tx, (ProviderUpdateRegistarTx) tx.getExtraPayloadObject());
                    break;
                case TRANSACTION_PROVIDER_UPDATE_REVOKE:
                    processRevoke((ProviderUpdateRevocationTx) tx.getExtraPayloadObject());
                default:
                    break;
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
            operatorKeyUsage.setStatus(AuthenticationKeyStatus.REVOKED);
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
        return keyChainGroup.freshKey(type);
    }

    private void processRegistration(Transaction tx, ProviderRegisterTx providerRegisterTx) {
        KeyId voting = providerRegisterTx.getKeyIDVoting();
        KeyId owner = providerRegisterTx.getKeyIDOwner();
        BLSPublicKey operator = providerRegisterTx.getPubkeyOperator();
        KeyId platformNodeId = providerRegisterTx.getPlatformNodeID();

        IKey votingKey = findKeyFromPubKeyHash(voting.getBytes(), Script.ScriptType.P2PKH);
        IKey ownerKey = findKeyFromPubKeyHash(owner.getBytes(), Script.ScriptType.P2PKH);
        IKey operatorKey = findKeyFromPubKey(operator.bitcoinSerialize());
        IKey platformKey = platformNodeId != null ? findKeyFromPubKeyHash(platformNodeId.getBytes(), Script.ScriptType.P2PKH) : null;

        // voting
        if (votingKey != null) {
            AuthenticationKeyUsage votingkeyUsage = AuthenticationKeyUsage.createVoting(votingKey, tx.getTxId(), providerRegisterTx.getAddress());
            keyUsage.put(votingKey, votingkeyUsage);
            keyChainGroup.markPubKeyHashAsUsed(voting.getBytes());
        }

        if (ownerKey != null) {
            AuthenticationKeyUsage ownerKeyUsage = AuthenticationKeyUsage.createOwner(ownerKey, tx.getTxId(), providerRegisterTx.getAddress());
            keyUsage.put(ownerKey, ownerKeyUsage);
            keyChainGroup.markPubKeyHashAsUsed(owner.getBytes());
        }

        if (operatorKey != null) {
            boolean legacy = providerRegisterTx.getVersion() == LEGACY_BLS_VERSION;
            AuthenticationKeyUsage operatorKeyUsage = AuthenticationKeyUsage.createOperator(operatorKey, legacy, tx.getTxId(), providerRegisterTx.getAddress());
            keyUsage.put(operatorKey, operatorKeyUsage);
            keyChainGroup.markPubKeyHashAsUsed(operatorKey.getPubKey());
        }

        if (platformKey != null) {
            AuthenticationKeyUsage platformKeyUsage = AuthenticationKeyUsage.createPlatformNodeId(platformKey, tx.getTxId(), providerRegisterTx.getAddress());
            keyUsage.put(platformKey, platformKeyUsage);
            keyChainGroup.markPubKeyHashAsUsed(platformKey.getPubKey());
        }
    }

    private void processUpdateRegistrar(Transaction tx, ProviderUpdateRegistarTx providerUpdateRegistarTx) {
        KeyId voting = providerUpdateRegistarTx.getKeyIDVoting();
        BLSPublicKey operator = providerUpdateRegistarTx.getPubkeyOperator();

        IKey votingKey = findKeyFromPubKeyHash(voting.getBytes(), Script.ScriptType.P2PKH);
        IKey operatorKey = findKeyFromPubKey(operator.bitcoinSerialize());
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

        // voting
        if (votingKey != null) {
            AuthenticationKeyUsage votingkeyUsage = AuthenticationKeyUsage.createVoting(votingKey, tx.getTxId(), address);
            keyUsage.put(votingKey, votingkeyUsage);
            keyChainGroup.markPubKeyHashAsUsed(voting.getBytes());
        }

        // operator
        if (operatorKey != null) {
            AuthenticationKeyUsage operatorKeyUsage = AuthenticationKeyUsage.createOperator(operatorKey, tx.getTxId(), address);
            keyUsage.put(operatorKey, operatorKeyUsage);
            keyChainGroup.markPubKeyHashAsUsed(operatorKey.getPubKey());
        }
    }

    @Override
    public String toString(boolean includeLookahead, boolean includePrivateKeys, @Nullable KeyParameter aesKey) {
        StringBuilder builder = new StringBuilder();
        builder.append(super.toString(includeLookahead, includePrivateKeys, aesKey));
        builder.append("\n").append("Authentication Key Usage").append("\n");
        for (AuthenticationKeyUsage usage : keyUsage.values()) {
            builder.append(usage).append("\n");
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
}
