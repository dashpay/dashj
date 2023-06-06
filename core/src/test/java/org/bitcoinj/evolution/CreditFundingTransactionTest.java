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
import org.bitcoinj.wallet.AuthenticationKeyChain;
import org.bitcoinj.wallet.AuthenticationKeyChainGroup;
import org.bitcoinj.wallet.DerivationPathFactory;
import org.bitcoinj.wallet.DeterministicSeed;
import org.bitcoinj.wallet.UnreadableWalletException;
import org.bouncycastle.util.encoders.Base64;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;


public class CreditFundingTransactionTest {

    Context context;
    UnitTestParams PARAMS;

    byte[] transactionData = Utils.HEX.decode("0300000002b74030bbda6edd804d4bfb2bdbbb7c207a122f3af2f6283de17074a42c6a5417020000006b483045022100815b175ab1a8fde7d651d78541ba73d2e9b297e6190f5244e1957004aa89d3c902207e1b164499569c1f282fe5533154495186484f7db22dc3dc1ccbdc9b47d997250121027f69794d6c4c942392b1416566aef9eaade43fbf07b63323c721b4518127baadffffffffb74030bbda6edd804d4bfb2bdbbb7c207a122f3af2f6283de17074a42c6a5417010000006b483045022100a7c94fe1bb6ffb66d2bb90fd8786f5bd7a0177b0f3af20342523e64291f51b3e02201f0308f1034c0f6024e368ca18949be42a896dda434520fa95b5651dc5ad3072012102009e3f2eb633ee12c0143f009bf773155a6c1d0f14271d30809b1dc06766aff0ffffffff031027000000000000166a1414ec6c36e6c39a9181f3a261a08a5171425ac5e210270000000000001976a91414ec6c36e6c39a9181f3a261a08a5171425ac5e288acc443953b000000001976a9140d1775b9ed85abeb19fd4a7d8cc88b08a29fe6de88ac00000000");


    @Before
    public void startup() {
        PARAMS = UnitTestParams.get();
        context = new Context(PARAMS);
    }

    /**
     * taken from dashsync-iOS
     */
    @Test
    public void testBlockchainIdentityFundingTransactionUniqueId() {
        CreditFundingTransaction fundingTransaction = new CreditFundingTransaction(PARAMS, transactionData);

        String lockedOutpoint = fundingTransaction.lockedOutpoint.toStringBase64();
        assertEquals("Locked outpoint is incorrect", "pRtcx0tE0ydkGODlBEfWNIivD2w6whvSkvYunB5+hCUAAAAA", lockedOutpoint);
        String identityIdentifier = fundingTransaction.creditBurnIdentityIdentifier.toStringBase58();
        assertEquals("Identity Identifier is incorrect", "Cka1ELdpfrZhFFvKRurvPtTHurDXXnnezafNPJkxCYjc", identityIdentifier);

        ECKey publicKey = ECKey.fromPublicOnly(Base64.decode("AsPvyyh6pkxss/Fespa7HCJIY8IA6ElAf6VKuqVcnPze"));
        ECKey privateKey = ECKey.fromPrivate(Utils.HEX.decode("fdbca0cd2be4375f04fcaee5a61c5d170a2a46b1c0c7531f58c430734a668f32"),true);
        assertArrayEquals("The private key doesn't match the funding transaction", privateKey.getPubKeyHash(), fundingTransaction.creditBurnPublicKeyId.getBytes());

    }

    @Test
    public void constructorTest() {
        CreditFundingTransaction cftx = new CreditFundingTransaction(PARAMS, transactionData);
        Transaction tx = new Transaction(PARAMS, transactionData);
        assertEquals(true, CreditFundingTransaction.isCreditFundingTransaction(tx));
        CreditFundingTransaction cftxCopy = new CreditFundingTransaction(tx);

        assertEquals(cftx.getFundingAmount(), cftxCopy.getFundingAmount());
        assertEquals(cftx.getCreditBurnIdentityIdentifier(), cftxCopy.getCreditBurnIdentityIdentifier());

        ECKey publicKey = ECKey.fromPublicOnly(Base64.decode("AsPvyyh6pkxss/Fespa7HCJIY8IA6ElAf6VKuqVcnPze"));
        cftx = new CreditFundingTransaction(PARAMS, publicKey, Coin.valueOf(10000));
        cftx.toString(); //make sure it doesn't crash
    }


    /*
    from dashj:
    -----------
    -0.00040224 DASH total value (sends 0.01 DASH and receives 0.00959776 DASH)
      a8abd4337d4906041324f1c9f713342112c0d24655fd295a57fe3563ac969896
      updated: 2020-02-26T02:13:00Z
      type TRANSACTION_NORMAL(0)
      purpose: USER_PAYMENT
         in   PUSHDATA(71)[304402207db8b157eeb8988556a03e817fd44f24e56c2c9341c5a0e512a89607481b8cb002204e7c3831e1b522e0ef740cf31926fa0e4e6cdd6496cde5005ff29f6b4499205801] PUSHDATA(33)[023bd027cf07a4eab516e22db16c7d77bb615bcd25a57bee6ee69dfaef23a61dac]  0.01 DASH
              P2PKH addr:yWJ58NBiirQjuWQPe9aCK1XwMYbya9kRCm  outpoint:2a93fefe869367a479496484421934ed81d0e9544b42a12d579fcab9f672e7fd:0
         out  RETURN PUSHDATA(20)[d0949bd75de50fbba5081d932fea0a0ee7407fa5]  0.0004 DASH
              CREDITBURN addr:yfLKRcydTC8naznVHGVwAExyewAsayLcst
         out  DUP HASH160 PUSHDATA(20)[29f4856f82e55313c8e7bf791ac868924ec9c59b] EQUALVERIFY CHECKSIG  0.00959776 DASH
              P2PKH addr:yQ9HUhd7DvVwUAXqRKWsJmgnVsVmjAq8V7
         fee  0.00001009 DASH/kB, 0.00000224 DASH for 222 bytes


    From the Evonet blockchain:
    ---------------------------
    getrawtransaction a8abd4337d4906041324f1c9f713342112c0d24655fd295a57fe3563ac969896 true
    {
      "hex": "0100000001fde772f6b9ca9f572da1424b54e9d081ed34194284644979a4679386fefe932a000000006a47304402207db8b157eeb8988556a03e817fd44f24e56c2c9341c5a0e512a89607481b8cb002204e7c3831e1b522e0ef740cf31926fa0e4e6cdd6496cde5005ff29f6b449920580121023bd027cf07a4eab516e22db16c7d77bb615bcd25a57bee6ee69dfaef23a61dacffffffff02409c000000000000166a14d0949bd75de50fbba5081d932fea0a0ee7407fa520a50e00000000001976a91429f4856f82e55313c8e7bf791ac868924ec9c59b88ac00000000",
      "txid": "a8abd4337d4906041324f1c9f713342112c0d24655fd295a57fe3563ac969896",
      "size": 222,
      "version": 1,
      "type": 0,
      "locktime": 0,
      "vin": [
        {
          "txid": "2a93fefe869367a479496484421934ed81d0e9544b42a12d579fcab9f672e7fd",
          "vout": 0,
          "scriptSig": {
            "asm": "304402207db8b157eeb8988556a03e817fd44f24e56c2c9341c5a0e512a89607481b8cb002204e7c3831e1b522e0ef740cf31926fa0e4e6cdd6496cde5005ff29f6b44992058[ALL] 023bd027cf07a4eab516e22db16c7d77bb615bcd25a57bee6ee69dfaef23a61dac",
            "hex": "47304402207db8b157eeb8988556a03e817fd44f24e56c2c9341c5a0e512a89607481b8cb002204e7c3831e1b522e0ef740cf31926fa0e4e6cdd6496cde5005ff29f6b449920580121023bd027cf07a4eab516e22db16c7d77bb615bcd25a57bee6ee69dfaef23a61dac"
          },
          "sequence": 4294967295
        }
      ],
      "vout": [
        {
          "value": 0.00040000,
          "valueSat": 40000,
          "n": 0,
          "scriptPubKey": {
            "asm": "OP_RETURN d0949bd75de50fbba5081d932fea0a0ee7407fa5",
            "hex": "6a14d0949bd75de50fbba5081d932fea0a0ee7407fa5",
            "type": "nulldata"
          }
        },
        {
          "value": 0.00959776,
          "valueSat": 959776,
          "n": 1,
          "scriptPubKey": {
            "asm": "OP_DUP OP_HASH160 29f4856f82e55313c8e7bf791ac868924ec9c59b OP_EQUALVERIFY OP_CHECKSIG",
            "hex": "76a91429f4856f82e55313c8e7bf791ac868924ec9c59b88ac",
            "reqSigs": 1,
            "type": "pubkeyhash",
            "addresses": [
              "yQ9HUhd7DvVwUAXqRKWsJmgnVsVmjAq8V7"
            ]
          }
        }
      ],
      "blockhash": "00000118003b266ede27cf361c3a1528f84133c42e94ab8899eba9e1398797d8",
      "height": 42714,
      "confirmations": 16,
      "time": 1582759932,
      "blocktime": 1582759932,
      "instantlock": true,
      "instantlock_internal": false,
      "chainlock": true
    }

     */

    @Test
    public void creditFundingFromWalletTest() throws UnreadableWalletException {
        // recovery phrase from a wallet that was used to generate a credit funding transaction
        // this is generated using the CreateWallet example in android-dashpay
        String mnemonic = "odor hammer panda sunset strong fee keep demise start eagle wagon avocado";
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

        // Get the first key from the blockchain identity funding keychain
        IDeterministicKey firstKey = group.currentKey(AuthenticationKeyChain.KeyChainType.BLOCKCHAIN_IDENTITY_FUNDING);

        /* tx data for 9788d647c020db783bd58f354eb56f049ea5f471b6a536ceb94d98b88bae4c31 */
        byte [] txData = Utils.HEX.decode("0100000001f19f93e98caea51ab67560a52b237dad7ca54546d0985aa11a1a639e9354fdba000000006a473044022074cd6796416991302d89954e458b996203428482a2d6650419ef0bba3f2a4791022054dead5e4a4eb2797628e4aecbd4be7679680293c668ae5008dc8174ec9dcd560121022535f1ee20879f1189656abd502a64c6dcbcbe5b732d4c2f780a4f6580a9f20affffffff0240420f0000000000166a14b98ac3c815ec340d9543cdc78ca2d24db1951aec20083d00000000001976a914afe755e29b119efa77f1b08c5ceacd885b53e5d088ac00000000");
        CreditFundingTransaction cftx = new CreditFundingTransaction(PARAMS, txData);

        // compare the credit burn public key id to the public key hash of the first key
        assertArrayEquals(cftx.getCreditBurnPublicKeyId().getBytes(), firstKey.getPubKeyHash());
    }
}
