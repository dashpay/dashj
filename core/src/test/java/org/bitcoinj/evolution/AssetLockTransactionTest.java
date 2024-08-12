/*
 * Copyright 2020 Dash Core Group
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

import org.bitcoinj.core.*;
import org.bitcoinj.crypto.IDeterministicKey;
import org.bitcoinj.params.UnitTestParams;
import org.bitcoinj.script.Script;
import org.bitcoinj.wallet.AuthenticationKeyChain;
import org.bitcoinj.wallet.AuthenticationKeyChainGroup;
import org.bitcoinj.wallet.DerivationPathFactory;
import org.bitcoinj.wallet.DeterministicSeed;
import org.bitcoinj.wallet.UnreadableWalletException;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.authentication.AuthenticationGroupExtension;
import org.bouncycastle.util.encoders.Base64;
import org.junit.Before;
import org.junit.Test;

import java.util.EnumSet;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;


public class AssetLockTransactionTest {

    Context context;
    UnitTestParams PARAMS;

    byte[] transactionData = Utils.HEX.decode("030008000142b021d1ee5752ec0bdd0f231fdafa7d59494985c32be5fc66cba890c5da3a8c010000006b483045022100e193274c1ea61cac9dd6ce9b93c29aa2037fdec8ae85a0355d419879bae3c2fa02201e8351cd09e9a4f5f416b362714cfb636513da6cd29fc01d183adb6f85ce22f00121020efe2eed0792b52f2f08c3fe558350e56f87ebd6a6e1d18e3671030bd59797a1ffffffff02c0e1e40000000000026a006fc92302000000001976a914fb481507faa32b529ff526871eb4a0feddd2cbdc88ac00000000240101c0e1e400000000001976a91495b02bf4a33ff0a95b18e8c54ac0a4f131d1ba7488ac");


    @Before
    public void startup() {
        PARAMS = UnitTestParams.get();
        context = new Context(PARAMS);
    }

    @Test
    public void testIdentityAssetLockTransactionUniqueId() {
        AssetLockTransaction fundingTransaction = new AssetLockTransaction(PARAMS, transactionData);

        String lockedOutpoint = fundingTransaction.getLockedOutpoint().toStringBase64();
        assertEquals("Locked outpoint is incorrect", "8mzV9rxAyscfmAH/kU2uEoSka+9kMFd6ZsAieVlmDJ0AAAAA", lockedOutpoint);
        String identityIdentifier = fundingTransaction.getIdentityId().toStringBase58();
        assertEquals("Identity Identifier is incorrect", "JEHdJRNQEcDeKDjNSNhYKp7zzL7dQmcAJxuoDEbHRjCH", identityIdentifier);

        ECKey privateKey = ECKey.fromPrivate(Utils.HEX.decode("8cfb151eceb7540e42da177cfbe3a1580e97edf96a699e40d100cea669abb002"),true);
        assertArrayEquals("The private key doesn't match the funding transaction", privateKey.getPubKeyHash(), fundingTransaction.getAssetLockPublicKeyId().getBytes());

    }

    @Test
    public void constructorTest() {
        AssetLockTransaction cftx = new AssetLockTransaction(PARAMS, transactionData);
        Transaction tx = new Transaction(PARAMS, transactionData);
        assertEquals(true, AssetLockTransaction.isAssetLockTransaction(tx));
        AssetLockTransaction cftxCopy = new AssetLockTransaction(tx);

        assertEquals(cftx.getFundingAmount(), cftxCopy.getFundingAmount());
        assertEquals(cftx.getIdentityId(), cftxCopy.getIdentityId());

        ECKey publicKey = ECKey.fromPublicOnly(Base64.decode("AsPvyyh6pkxss/Fespa7HCJIY8IA6ElAf6VKuqVcnPze"));
        cftx = new AssetLockTransaction(PARAMS, publicKey, Coin.valueOf(10000));
        cftx.toString(); //make sure it doesn't crash
    }


    /*
    from dashj:
    -----------
    AssetLockTransaction{9d0c66597922c0667a573064ef6ba48412ae4d91ff01981fc7ca40bcf6d56cf2
    version: 3
       type: TRANSACTION_ASSET_LOCK(8)
    purpose: UNKNOWN
       in   PUSHDATA(72)[3045022100e193274c1ea61cac9dd6ce9b93c29aa2037fdec8ae85a0355d419879bae3c2fa02201e8351cd09e9a4f5f416b362714cfb636513da6cd29fc01d183adb6f85ce22f001] PUSHDATA(33)[020efe2eed0792b52f2f08c3fe558350e56f87ebd6a6e1d18e3671030bd59797a1]
            unconnected  outpoint:8c3adac590a8cb66fce52bc3854949597dfada1f230fdd0bec5257eed121b042:1
       out  RETURN 0[]  0.15 DASH
            ASSETLOCK
       out  DUP HASH160 PUSHDATA(20)[fb481507faa32b529ff526871eb4a0feddd2cbdc] EQUALVERIFY CHECKSIG  0.35899759 DASH
            P2PKH addr:yjE6oi2vV48uTiqR82RnaH5NDvFG13L9vh
    payload: AssetLockPayload
       out  DUP HASH160 PUSHDATA(20)[95b02bf4a33ff0a95b18e8c54ac0a4f131d1ba74] EQUALVERIFY CHECKSIG  0.15 DASH
            P2PKH addr:yZxvaQkCdZKZtLwtETcHhQ7JXaaEJShfho
    }


    From the Testnet blockchain:
    ---------------------------
    getrawtransaction 9d0c66597922c0667a573064ef6ba48412ae4d91ff01981fc7ca40bcf6d56cf2 true
    {
      "txid": "9d0c66597922c0667a573064ef6ba48412ae4d91ff01981fc7ca40bcf6d56cf2",
      "version": 3,
      "type": 8,
      "size": 240,
      "locktime": 0,
      "vin": [
        {
          "txid": "8c3adac590a8cb66fce52bc3854949597dfada1f230fdd0bec5257eed121b042",
          "vout": 1,
          "scriptSig": {
            "asm": "3045022100e193274c1ea61cac9dd6ce9b93c29aa2037fdec8ae85a0355d419879bae3c2fa02201e8351cd09e9a4f5f416b362714cfb636513da6cd29fc01d183adb6f85ce22f0[ALL] 020efe2eed0792b52f2f08c3fe558350e56f87ebd6a6e1d18e3671030bd59797a1",
            "hex": "483045022100e193274c1ea61cac9dd6ce9b93c29aa2037fdec8ae85a0355d419879bae3c2fa02201e8351cd09e9a4f5f416b362714cfb636513da6cd29fc01d183adb6f85ce22f00121020efe2eed0792b52f2f08c3fe558350e56f87ebd6a6e1d18e3671030bd59797a1"
          },
          "sequence": 4294967295
        }
      ],
      "vout": [
        {
          "value": 0.15000000,
          "valueSat": 15000000,
          "n": 0,
          "scriptPubKey": {
            "asm": "OP_RETURN 0",
            "hex": "6a00",
            "type": "nulldata"
          }
        },
        {
          "value": 0.35899759,
          "valueSat": 35899759,
          "n": 1,
          "scriptPubKey": {
            "asm": "OP_DUP OP_HASH160 fb481507faa32b529ff526871eb4a0feddd2cbdc OP_EQUALVERIFY OP_CHECKSIG",
            "hex": "76a914fb481507faa32b529ff526871eb4a0feddd2cbdc88ac",
            "reqSigs": 1,
            "type": "pubkeyhash",
            "addresses": [
              "yjE6oi2vV48uTiqR82RnaH5NDvFG13L9vh"
            ]
          }
        }
      ],
      "extraPayloadSize": 36,
      "extraPayload": "0101c0e1e400000000001976a91495b02bf4a33ff0a95b18e8c54ac0a4f131d1ba7488ac",
      "assetLockTx": {
        "version": 1,
        "creditOutputs": [
          "CTxOut(nValue=0.15000000, scriptPubKey=76a91495b02bf4a33ff0a95b18e8c5)"
        ]
      },
      "hex": "030008000142b021d1ee5752ec0bdd0f231fdafa7d59494985c32be5fc66cba890c5da3a8c010000006b483045022100e193274c1ea61cac9dd6ce9b93c29aa2037fdec8ae85a0355d419879bae3c2fa02201e8351cd09e9a4f5f416b362714cfb636513da6cd29fc01d183adb6f85ce22f00121020efe2eed0792b52f2f08c3fe558350e56f87ebd6a6e1d18e3671030bd59797a1ffffffff02c0e1e40000000000026a006fc92302000000001976a914fb481507faa32b529ff526871eb4a0feddd2cbdc88ac00000000240101c0e1e400000000001976a91495b02bf4a33ff0a95b18e8c54ac0a4f131d1ba7488ac",
      "instantlock": true,
      "instantlock_internal": true,
      "chainlock": false
    }

     */

    @Test
    public void creditFundingFromWalletTest() throws UnreadableWalletException {
        // recovery phrase from a wallet that was used to generate a credit funding transaction
        // this is generated using the CreateWallet example in android-dashpay
        String mnemonic = "language turn degree dignity census faculty usual special claim sausage staff faint";
        DerivationPathFactory factory = new DerivationPathFactory(PARAMS);

        // Create the keychain for blockchain identity funding
        AuthenticationKeyChain blockchainIdentityFundingChain = AuthenticationKeyChain.authenticationBuilder()
                .accountPath(factory.blockchainIdentityRegistrationFundingDerivationPath())
                .seed(new DeterministicSeed(mnemonic, null, "", Utils.currentTimeSeconds()))
                .type(AuthenticationKeyChain.KeyChainType.BLOCKCHAIN_IDENTITY_FUNDING)
                .build();

        // create the authentication key chain group and add the blockchain identity funding key chain
        AuthenticationKeyChainGroup group = AuthenticationKeyChainGroup.authenticationBuilder(PARAMS)
                .addChain(blockchainIdentityFundingChain)
                .build();

        Wallet wallet = Wallet.createDeterministic(context, Script.ScriptType.P2PKH);
        AuthenticationGroupExtension authenticationGroupExtension = new AuthenticationGroupExtension(wallet);
        wallet.addExtension(authenticationGroupExtension);
        authenticationGroupExtension.addKeyChains(context.getParams(), new DeterministicSeed(mnemonic, null, "", Utils.currentTimeSeconds()), EnumSet.of(AuthenticationKeyChain.KeyChainType.BLOCKCHAIN_IDENTITY_FUNDING));


        // Get the first key from the blockchain identity funding keychain
        IDeterministicKey firstKey = authenticationGroupExtension.getKeyChain(AuthenticationKeyChain.KeyChainType.BLOCKCHAIN_IDENTITY_FUNDING).getKey(1);

        /* tx data for 9d0c66597922c0667a573064ef6ba48412ae4d91ff01981fc7ca40bcf6d56cf2 */
        AssetLockTransaction cftx = new AssetLockTransaction(PARAMS, transactionData);

        // compare the credit burn public key id to the public key hash of the first key
        assertArrayEquals(cftx.getAssetLockPublicKeyId().getBytes(), firstKey.getPubKeyHash());
    }
}
