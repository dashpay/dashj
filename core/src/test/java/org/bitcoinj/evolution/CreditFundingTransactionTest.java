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
import org.bitcoinj.params.UnitTestParams;
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
}
