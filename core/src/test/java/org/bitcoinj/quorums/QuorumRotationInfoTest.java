/*
 * Copyright 2021 Dash Core Group
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

package org.bitcoinj.quorums;

import com.google.common.collect.Lists;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.KeyId;
import org.bitcoinj.core.MasternodeAddress;
import org.bitcoinj.core.PartialMerkleTree;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.crypto.BLSLazyPublicKey;
import org.bitcoinj.crypto.BLSLazySignature;
import org.bitcoinj.evolution.MasternodeListDiffException;
import org.bitcoinj.evolution.SimplifiedMasternodeList;
import org.bitcoinj.evolution.SimplifiedMasternodeListDiff;
import org.bitcoinj.evolution.SimplifiedMasternodeListEntry;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.store.BlockStoreException;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import java.util.Arrays;
import java.util.Objects;

import static org.bitcoinj.core.Utils.HEX;
import static org.junit.Assert.assertArrayEquals;

public class QuorumRotationInfoTest {
    // qrinfo object
    byte [] payloadOne;
    TestNet3Params PARAMS = TestNet3Params.get();

    @Before
    public void startUp() throws IOException {
        InputStream inputStream = Objects.requireNonNull(getClass().getResourceAsStream("qrinfo--1-2096.dat"));
        payloadOne = new byte [inputStream.available()];
        inputStream.read(payloadOne);
    }

    @Test
    public void roundTripTest() {
        QuorumRotationInfo quorumRotationInfo = new QuorumRotationInfo(PARAMS, payloadOne);
        assertArrayEquals(payloadOne, quorumRotationInfo.bitcoinSerialize());
    }

    // this test may become obsolete
    @Test @Ignore
    public void quorumRotationInfoTest() throws FileNotFoundException, IOException, NullPointerException,
            BlockStoreException, MasternodeListDiffException {
        QuorumRotationInfo qrinfo = new QuorumRotationInfo(PARAMS);
        qrinfo.setQuorumSnapshotAtHMinusC(
                new QuorumSnapshot(Arrays.asList(true, true, true, true, true), 1, Arrays.asList(1,1))
        );
        qrinfo.setQuorumSnapshotAtHMinus2C(
                new QuorumSnapshot(Arrays.asList(false, true, true, true, true), 0, Lists.newArrayList())
        );
        qrinfo.setQuorumSnapshotAtHMinus3C(
                new QuorumSnapshot(Arrays.asList(false, false, false, true, true), 0, Lists.newArrayList())
        );
        qrinfo.setMnListDiffTip(new SimplifiedMasternodeListDiff(PARAMS,
                Sha256Hash.wrap("00000bafbc94add76cb75e2ec92894837288a481e5c005f6563d91623bf8bc2c"),
                Sha256Hash.wrap("512157e793d0cf790b6e6305efebcb2745dfc279ae9ef3b8cc0c8ff1c29d978d"),
                new PartialMerkleTree(PARAMS, HEX.decode("01000000010e45d82414995ed1c23f546c35afa316499c09b870dd0a3c15796c4ccfdc00c40101"), 0),
                new Transaction(PARAMS, HEX.decode("03000500010000000000000000000000000000000000000000000000000000000000000000ffffffff050205040101ffffffff038d0cb75b0300000023210285c760cb2fd04fc7ff217cfd1c66594ba47247c29eed85949f43e80f57b53727acd94e854a030000001976a91471d69c816b5ad8718c800607fef3a47221078d6088acb0bd3111000000001976a914a73955c08d561a22a399513e1c5d3983d110701d88ac000000004602000504000042696f1f2db709cde94efa6f8de3e5c5ffda082fcb3f9d81b5929849385667bd3394b4b77e40afd081094fb55b49a94f39bc6910146b5010b9d6af082f15545a"), 0),
                Arrays.asList(
                        new SimplifiedMasternodeListEntry(PARAMS,
                                Sha256Hash.wrap("663b2fb8bb620db387f7268ccdf261d0739b3b734080487736560536f9da67c0"),
                                Sha256Hash.wrap("5fcef4606b48e92b611df852b12974e549fc385a7097d2370a7bd0bad8cbb055"),
                                new MasternodeAddress("127.0.0.1", 13998),
                                new KeyId(Address.fromBase58(PARAMS, "yhedxEwiZ162jKCd3WpvWgWWocDiciJuKk").getHash()),
                                new BLSLazyPublicKey(PARAMS, HEX.decode("8c3a4249f6e1597ac13fce64b91361ebf6d0837d5a95736549b88826868c34c7c8ede2da665e2702708fc431c9eb231b"), 0),
                                true
                        ),
                        new SimplifiedMasternodeListEntry(PARAMS,
                                Sha256Hash.wrap("beba26ecf8a70d13898cdf463dc2b29b3111999d9ad7bb3b1bd270c38ec64a46"),
                                Sha256Hash.wrap("63f2bb5920d1a0c27c9689d62c6834a314584fcd7b2bf52388c1dcc2c987f2ec"),
                                new MasternodeAddress("127.0.0.1", 13999),
                                new KeyId(Address.fromBase58(PARAMS, "yZLegVnDt5t4KZAiXiH2M88LbvkHxRnXL5").getHash()),
                                new BLSLazyPublicKey(PARAMS, HEX.decode("1185482390215003acac18979f4090b5cb4f2a7abd54a0e25b676d681bb6b53853488a74a014863403eab95b47afa017"), 0),
                                true
                        ),
                        new SimplifiedMasternodeListEntry(PARAMS,
                                Sha256Hash.wrap("67eb2b533192cd354e9571cb361c5fb2f3a40847aed1b0983a18164f650542d4"),
                                Sha256Hash.wrap("17ac84060b137d5e2c19bc4f2fa030fd4c49aedc77c64380251d7c22bf78e560"),
                                new MasternodeAddress("127.0.0.1", 13995),
                                new KeyId(Address.fromBase58(PARAMS, "ygo4ZEACuXGWygecqexNvLR2ryPV6LJBXh").getHash()),
                                new BLSLazyPublicKey(PARAMS, HEX.decode("1904bd1b479ff9fb5d99996575e4bdad5fefad11adf58de8e75d1b6e4964adf8b55d9e4f9432d56df6bef67f2ad68cda"), 0),
                                true
                        ),
                        new SimplifiedMasternodeListEntry(PARAMS,
                                Sha256Hash.wrap("f4bb17991ade54751bae0d192102ffa1fe9428085f459acf22a34f061e5b2a18"),
                                Sha256Hash.wrap("50cc1bc3de661f923afd38e608a5b4fbd1b608cba85d050e7380cbbbe4fc41ff"),
                                new MasternodeAddress("127.0.0.1", 13997),
                                new KeyId(Address.fromBase58(PARAMS, "yiyRqpgyVXTw3SGyWMVNeWS5YMNC56MMnW").getHash()),
                                new BLSLazyPublicKey(PARAMS, HEX.decode("0579dccf1de4e4bbf5f69bd1ccf5df2dd327d32b49e3789ec823858bb4fc3fef32c214461ca16151e7c5176573291429"), 0),
                                true
                        )
                ),
                Arrays.asList(
                        new FinalCommitment(PARAMS, 2,100, Sha256Hash.wrap("0e66f9273a2de1905244de60faf55bb6ec795cc6c66607f2c0cf9264e224d02d"),
                                0, 4, HEX.decode("0f"), 4, HEX.decode("0f"),
                                HEX.decode("0f10444bd28d6a0993224baef8ad9b5c6fcdb246b268ef36928e9b9594e1e0e8679b867a16914c55d129007b07169767"),
                                Sha256Hash.wrap("a689bbe717b3e6ad6ea82d56dcd39cac0fc59b78849277519916be7422e2656c"),
                                new BLSLazySignature(PARAMS, HEX.decode("0ab9c10a55e71608c4adb77e7fcf3aa060d26ee243c8bee3fa0a5af04e79a628974444a68c3bdc289d5a4141c7e5ba5c15a3f0f40c4f16dd4cdb2cb42223ecd842a238c9937ce6c5b065dfebc35fd4a0c64aac0358a1095cfae7c3fedf6063eb"), 0),
                                new BLSLazySignature(PARAMS, HEX.decode("10a116918d6d5e44732178c68076cc640b884ea91a83072b4205056af2682c6ee65b32d7f49bd38e349cdbd5b2292f3904d1e1c4a17e45bd94d4d1d5c89a9eaade467dd208d8878cb4fed3afb8b2eada4b5cd10ba65c9c8356c5bc54fdd22834"), 0)
                        ),
                        new FinalCommitment(PARAMS, 2,100, Sha256Hash.wrap("6a8908985dac8faaf2edeeb5fc581ca8cfd13ab8a0d054283b10241d9cd74ee7"),
                                1, 4, HEX.decode("0f"), 4, HEX.decode("0f"),
                                HEX.decode("01debc67e536f44b62b0558b6c3a15e7b0ea96b31b284875a9ca8d29ed7b214a75586095bdaf587daac6db781341512c"),
                                Sha256Hash.wrap("1283ed47e65a4a6b8696baee4e58982db52f2cc8a2b9f49ae45fdad31f6d5e8f"),
                                new BLSLazySignature(PARAMS, HEX.decode("0b0f242f2c157df7a6b86d6de735088c230f654787cb45a23d299e9b9adb17b2382e14160f4181108198a3efe50ca3351298186b2be82c622ffda6c2cfde2007c080db44ef97e088b1127f3286f7220c1ed87993d6f186a5085764cc911a4c48"),0),
                                new BLSLazySignature(PARAMS, HEX.decode("82035bfe76e6ddf3a88d141512e5624ab3cd04596d7afaf928352e76d55bb819a5183a9aeb9a604bb23a40f371dc34f500c2738f4e3147c909d8ba384ce26c13b5695fca04e16e59064b2e53632fd8314a08bc81b49a7555eeda5cdc79f71a7c"), 0)
                                ),
                        new FinalCommitment(PARAMS, 1,102, Sha256Hash.wrap("0e66f9273a2de1905244de60faf55bb6ec795cc6c66607f2c0cf9264e224d02d"),
                                0, 3, HEX.decode("07"), 3, HEX.decode("07"),
                                HEX.decode("05a55e3fea0341d7f1985a1d5cefa20536bbe56df49c3bddff19ba8ec23ae20956cd1bf4718c6c33b8901583de3b5455"),
                                Sha256Hash.wrap("bb78ee19643d2bc92334e4d630edab7fde7be90549525121b6db97ecace5524a"),
                                new BLSLazySignature(PARAMS, HEX.decode("94a41ea4095cd7d4ca5db33018eb2df3abb9f51ea74653db1dc2832a45f683d96cd0cd209458ece30c6f6b908c53687a0e428b6763db9c65bcc753509fb919e4643d52c759ff8285724840677be35e7db3f9981bb93c36ba41f51afeb386a511"), 0),
                                new BLSLazySignature(PARAMS, HEX.decode("89befef3e3c509192d843c426df9c7316c6cb63ec4bc2c50744d0ac33a769283df7b645a965c3dba9e008b258bbe8bb7016774fc81d31e85ac34cb844a8c572dc6b2d6576b65df0fb09114c964c315fc66ea22b1911ed26fb82d39b9635b18b9"), 0)
                                ),
                        new FinalCommitment(PARAMS, 1, 102, Sha256Hash.wrap("0e66f9273a2de1905244de60faf55bb6ec795cc6c66607f2c0cf9264e224d02d"),
                                0, 3, HEX.decode("07"), 3, HEX.decode("07"),
                                HEX.decode("92470c6e0268b1cd8f326e673c952fcbb5b0dd8f7f28dd0bb4a7d14023f69bab475f2aff3930b00bc040c211e0363272"),
                                Sha256Hash.wrap("c82cbf83d68d2a9598ebd14798d7544485659884dad31d0305c9e01969031769"),
                                new BLSLazySignature(PARAMS, HEX.decode("94c3181a766833b06ce5b4d4c83534da6665974f81e2d9fe6e4eb644ea9c033fcaf62cdc244a82a8453261043cb55f4006d62f310dfe918c292663a2abb19df253ddf67db2c4df126edaa2b21a61dbc9af8fc0a36fce55b8bf14a57f7edb60ca"), 0),
                                new BLSLazySignature(PARAMS, HEX.decode("94be970450945d578263d08ab524d995e253db865d7195671f2c48a0baff7d2b6c9f17f79abf8247244459c60104d8da0efe78a709db8e85e1047fa620365bcf31f9f488aaf27a46cf1ba33d768cf6ea66ee02777ef0b42b2d92e16011517438"), 0)
                                )
                )
        ));
        qrinfo.setMnListDiffAtHMinusC(new SimplifiedMasternodeListDiff(PARAMS,
                Sha256Hash.wrap("00000bafbc94add76cb75e2ec92894837288a481e5c005f6563d91623bf8bc2c"),
                Sha256Hash.wrap("254b1bdd055a27cd8cb59572e9157ff204e6559ad31a2cb7184fab60134305ae"),
                new PartialMerkleTree(PARAMS, HEX.decode("01000000013add9c9f92caf1812de45d1a3bba7f31a3f5cb820927023d5cf052b3310beb360101"), 0),
                new Transaction(PARAMS, HEX.decode("03000500010000000000000000000000000000000000000000000000000000000000000000ffffffff0502d8030101ffffffff03652a3dbb0300000023210341e286feb673b48b7220321665363cfbbc137625c8dd5d83a3056303f60a43a7ac9a5722a8030000001976a91471d69c816b5ad8718c800607fef3a47221078d6088acc4d21a13000000001976a914a73955c08d561a22a399513e1c5d3983d110701d88ac00000000460200d803000042696f1f2db709cde94efa6f8de3e5c5ffda082fcb3f9d81b5929849385667bd0000000000000000000000000000000000000000000000000000000000000000"), 0),
                Arrays.asList(
                        new SimplifiedMasternodeListEntry(PARAMS,
                                Sha256Hash.wrap("663b2fb8bb620db387f7268ccdf261d0739b3b734080487736560536f9da67c0"),
                                Sha256Hash.wrap("5fcef4606b48e92b611df852b12974e549fc385a7097d2370a7bd0bad8cbb055"),
                                new MasternodeAddress("127.0.0.1", 13998),
                                new KeyId(Address.fromBase58(PARAMS, "yhedxEwiZ162jKCd3WpvWgWWocDiciJuKk").getHash()),
                                new BLSLazyPublicKey(PARAMS, HEX.decode("8c3a4249f6e1597ac13fce64b91361ebf6d0837d5a95736549b88826868c34c7c8ede2da665e2702708fc431c9eb231b"), 0),
                                true
                        ),
                        new SimplifiedMasternodeListEntry(PARAMS,
                                Sha256Hash.wrap("beba26ecf8a70d13898cdf463dc2b29b3111999d9ad7bb3b1bd270c38ec64a46"),
                                Sha256Hash.wrap("63f2bb5920d1a0c27c9689d62c6834a314584fcd7b2bf52388c1dcc2c987f2ec"),
                                new MasternodeAddress("127.0.0.1", 13999),
                                new KeyId(Address.fromBase58(PARAMS, "yZLegVnDt5t4KZAiXiH2M88LbvkHxRnXL5").getHash()),
                                new BLSLazyPublicKey(PARAMS, HEX.decode("1185482390215003acac18979f4090b5cb4f2a7abd54a0e25b676d681bb6b53853488a74a014863403eab95b47afa017"), 0),
                                true
                        ),
                        new SimplifiedMasternodeListEntry(PARAMS,
                                Sha256Hash.wrap("67eb2b533192cd354e9571cb361c5fb2f3a40847aed1b0983a18164f650542d4"),
                                Sha256Hash.wrap("17ac84060b137d5e2c19bc4f2fa030fd4c49aedc77c64380251d7c22bf78e560"),
                                new MasternodeAddress("127.0.0.1", 13995),
                                new KeyId(Address.fromBase58(PARAMS, "ygo4ZEACuXGWygecqexNvLR2ryPV6LJBXh").getHash()),
                                new BLSLazyPublicKey(PARAMS, HEX.decode("1904bd1b479ff9fb5d99996575e4bdad5fefad11adf58de8e75d1b6e4964adf8b55d9e4f9432d56df6bef67f2ad68cda"), 0),
                                true
                        ),
                        new SimplifiedMasternodeListEntry(PARAMS,
                                Sha256Hash.wrap("f4bb17991ade54751bae0d192102ffa1fe9428085f459acf22a34f061e5b2a18"),
                                Sha256Hash.wrap("50cc1bc3de661f923afd38e608a5b4fbd1b608cba85d050e7380cbbbe4fc41ff"),
                                new MasternodeAddress("127.0.0.1", 13997),
                                new KeyId(Address.fromBase58(PARAMS, "yiyRqpgyVXTw3SGyWMVNeWS5YMNC56MMnW").getHash()),
                                new BLSLazyPublicKey(PARAMS, HEX.decode("0579dccf1de4e4bbf5f69bd1ccf5df2dd327d32b49e3789ec823858bb4fc3fef32c214461ca16151e7c5176573291429"), 0),
                                true
                        )
                ),
                Lists.newArrayList()
        ));
        qrinfo.setMnListDiffAtHMinus2C(new SimplifiedMasternodeListDiff(PARAMS,
                Sha256Hash.wrap("00000bafbc94add76cb75e2ec92894837288a481e5c005f6563d91623bf8bc2c"),
                Sha256Hash.wrap("443ba06eb11d60f186ba3d91ec94fb6811461276b268ac1bf41862d5461a5977"),
                new PartialMerkleTree(PARAMS, HEX.decode("0100000001513d1ac0faec415f67b38b57e729fa5bdd9c018ec01496fcc1a37917c1a2cba00101"), 0),
                new Transaction(PARAMS, HEX.decode("03000500010000000000000000000000000000000000000000000000000000000000000000ffffffff0502c0030101ffffffff03652a3dbb030000002321020fbe2d1fa5dff237073e1bd07ad058ba6ebd10178fd4b5bbc7cc243288d424a3ac38ee949e030000001976a9140cbc15d5d0979efe0f5f384cd071ef167f64b7a388ac263ca81c000000001976a914ebed2fa555deaa7bc0e9e56fd3d1fb5e7d02914b88ac00000000460200c003000042696f1f2db709cde94efa6f8de3e5c5ffda082fcb3f9d81b5929849385667bd0000000000000000000000000000000000000000000000000000000000000000"), 0),
                Arrays.asList(
                        new SimplifiedMasternodeListEntry(PARAMS,
                                Sha256Hash.wrap("663b2fb8bb620db387f7268ccdf261d0739b3b734080487736560536f9da67c0"),
                                Sha256Hash.wrap("5fcef4606b48e92b611df852b12974e549fc385a7097d2370a7bd0bad8cbb055"),
                                new MasternodeAddress("127.0.0.1", 13998),
                                new KeyId(Address.fromBase58(PARAMS, "yhedxEwiZ162jKCd3WpvWgWWocDiciJuKk").getHash()),
                                new BLSLazyPublicKey(PARAMS, HEX.decode("8c3a4249f6e1597ac13fce64b91361ebf6d0837d5a95736549b88826868c34c7c8ede2da665e2702708fc431c9eb231b"), 0),
                                true
                        ),
                        new SimplifiedMasternodeListEntry(PARAMS,
                                Sha256Hash.wrap("beba26ecf8a70d13898cdf463dc2b29b3111999d9ad7bb3b1bd270c38ec64a46"),
                                Sha256Hash.wrap("63f2bb5920d1a0c27c9689d62c6834a314584fcd7b2bf52388c1dcc2c987f2ec"),
                                new MasternodeAddress("127.0.0.1", 13999),
                                new KeyId(Address.fromBase58(PARAMS, "yZLegVnDt5t4KZAiXiH2M88LbvkHxRnXL5").getHash()),
                                new BLSLazyPublicKey(PARAMS, HEX.decode("1185482390215003acac18979f4090b5cb4f2a7abd54a0e25b676d681bb6b53853488a74a014863403eab95b47afa017"), 0),
                                true
                        ),
                        new SimplifiedMasternodeListEntry(PARAMS,
                                Sha256Hash.wrap("67eb2b533192cd354e9571cb361c5fb2f3a40847aed1b0983a18164f650542d4"),
                                Sha256Hash.wrap("17ac84060b137d5e2c19bc4f2fa030fd4c49aedc77c64380251d7c22bf78e560"),
                                new MasternodeAddress("127.0.0.1", 13995),
                                new KeyId(Address.fromBase58(PARAMS, "ygo4ZEACuXGWygecqexNvLR2ryPV6LJBXh").getHash()),
                                new BLSLazyPublicKey(PARAMS, HEX.decode("1904bd1b479ff9fb5d99996575e4bdad5fefad11adf58de8e75d1b6e4964adf8b55d9e4f9432d56df6bef67f2ad68cda"), 0),
                                true
                        ),
                        new SimplifiedMasternodeListEntry(PARAMS,
                                Sha256Hash.wrap("f4bb17991ade54751bae0d192102ffa1fe9428085f459acf22a34f061e5b2a18"),
                                Sha256Hash.wrap("50cc1bc3de661f923afd38e608a5b4fbd1b608cba85d050e7380cbbbe4fc41ff"),
                                new MasternodeAddress("127.0.0.1", 13997),
                                new KeyId(Address.fromBase58(PARAMS, "yiyRqpgyVXTw3SGyWMVNeWS5YMNC56MMnW").getHash()),
                                new BLSLazyPublicKey(PARAMS, HEX.decode("0579dccf1de4e4bbf5f69bd1ccf5df2dd327d32b49e3789ec823858bb4fc3fef32c214461ca16151e7c5176573291429"), 0),
                                true
                        )
                ),
                Lists.newArrayList()
        ));
        qrinfo.setMnListDiffAtHMinus3C(new SimplifiedMasternodeListDiff(PARAMS,
                Sha256Hash.wrap("00000bafbc94add76cb75e2ec92894837288a481e5c005f6563d91623bf8bc2c"),
                Sha256Hash.wrap("6f5d13e68c1b2c780a4375cea00d026aba2e055e2b47f26d68ce22bdcec9cfcd"),
                new PartialMerkleTree(PARAMS, HEX.decode("0100000001750554d572d4ba7dcabead90947d1c57f9df011cba79a2b95666d73302df926a0101"), 0),
                new Transaction(PARAMS, HEX.decode("03000500010000000000000000000000000000000000000000000000000000000000000000ffffffff0502a8030101ffffffff03652a3dbb030000002321029405c90f863e8c9b4dfd0c5aded24f08ebcfd8e053d187a27f70ecd02192afaaacfcc0afb1030000001976a914c9560a0f3528e1c5e3eea83367d2647941961bb488ac62698d09000000001976a91427ce4c0695b45de4a4d6d51358804a6a78e9dda688ac00000000460200a803000042696f1f2db709cde94efa6f8de3e5c5ffda082fcb3f9d81b5929849385667bd0000000000000000000000000000000000000000000000000000000000000000"), 0),
                Arrays.asList(
                        new SimplifiedMasternodeListEntry(PARAMS,
                                Sha256Hash.wrap("663b2fb8bb620db387f7268ccdf261d0739b3b734080487736560536f9da67c0"),
                                Sha256Hash.wrap("5fcef4606b48e92b611df852b12974e549fc385a7097d2370a7bd0bad8cbb055"),
                                new MasternodeAddress("127.0.0.1", 13998),
                                new KeyId(Address.fromBase58(PARAMS, "yhedxEwiZ162jKCd3WpvWgWWocDiciJuKk").getHash()),
                                new BLSLazyPublicKey(PARAMS, HEX.decode("8c3a4249f6e1597ac13fce64b91361ebf6d0837d5a95736549b88826868c34c7c8ede2da665e2702708fc431c9eb231b"), 0),
                                true
                        ),
                        new SimplifiedMasternodeListEntry(PARAMS,
                                Sha256Hash.wrap("beba26ecf8a70d13898cdf463dc2b29b3111999d9ad7bb3b1bd270c38ec64a46"),
                                Sha256Hash.wrap("63f2bb5920d1a0c27c9689d62c6834a314584fcd7b2bf52388c1dcc2c987f2ec"),
                                new MasternodeAddress("127.0.0.1", 13999),
                                new KeyId(Address.fromBase58(PARAMS, "yZLegVnDt5t4KZAiXiH2M88LbvkHxRnXL5").getHash()),
                                new BLSLazyPublicKey(PARAMS, HEX.decode("1185482390215003acac18979f4090b5cb4f2a7abd54a0e25b676d681bb6b53853488a74a014863403eab95b47afa017"), 0),
                                true
                        ),
                        new SimplifiedMasternodeListEntry(PARAMS,
                                Sha256Hash.wrap("67eb2b533192cd354e9571cb361c5fb2f3a40847aed1b0983a18164f650542d4"),
                                Sha256Hash.wrap("17ac84060b137d5e2c19bc4f2fa030fd4c49aedc77c64380251d7c22bf78e560"),
                                new MasternodeAddress("127.0.0.1", 13995),
                                new KeyId(Address.fromBase58(PARAMS, "ygo4ZEACuXGWygecqexNvLR2ryPV6LJBXh").getHash()),
                                new BLSLazyPublicKey(PARAMS, HEX.decode("1904bd1b479ff9fb5d99996575e4bdad5fefad11adf58de8e75d1b6e4964adf8b55d9e4f9432d56df6bef67f2ad68cda"), 0),
                                true
                        ),
                        new SimplifiedMasternodeListEntry(PARAMS,
                                Sha256Hash.wrap("f4bb17991ade54751bae0d192102ffa1fe9428085f459acf22a34f061e5b2a18"),
                                Sha256Hash.wrap("50cc1bc3de661f923afd38e608a5b4fbd1b608cba85d050e7380cbbbe4fc41ff"),
                                new MasternodeAddress("127.0.0.1", 13997),
                                new KeyId(Address.fromBase58(PARAMS, "yiyRqpgyVXTw3SGyWMVNeWS5YMNC56MMnW").getHash()),
                                new BLSLazyPublicKey(PARAMS, HEX.decode("0579dccf1de4e4bbf5f69bd1ccf5df2dd327d32b49e3789ec823858bb4fc3fef32c214461ca16151e7c5176573291429"), 0),
                                true
                        )
                ),
                Lists.newArrayList()
        ));
// TODO: This part of the test has many bugs
//        Context context;
//        try {
//            context = Context.get();
//        } catch (IllegalStateException e) {
//            context = Context.getOrCreate(PARAMS);
//        }
//        BlockChain blockChain = new BlockChain(PARAMS, new MemoryBlockStore(PARAMS));
//        PeerGroup peerGroup = new PeerGroup(PARAMS, blockChain);
//        context.initDash(true, true);
//        context.masternodeListManager.setBlockChain(blockChain, null, peerGroup, context.quorumManager, context.quorumSnapshotManager);
//        context.masternodeListManager.processQuorumRotationInfo(null, qrinfo, true);
//        ArrayList<Masternode> list = context.masternodeListManager.getAllQuorumMembers(LLMQParameters.LLMQType.LLMQ_DEVNET, Sha256Hash.wrap("512157e793d0cf790b6e6305efebcb2745dfc279ae9ef3b8cc0c8ff1c29d978d"));
//        System.out.println(list);

        SimplifiedMasternodeList mnListTip = new SimplifiedMasternodeList(PARAMS);
        SimplifiedMasternodeList mnListAtHMinusC = new SimplifiedMasternodeList(PARAMS);
        SimplifiedMasternodeList mnListAtHMinus2C = new SimplifiedMasternodeList(PARAMS);
        SimplifiedMasternodeList mnListAtHMinus3C = new SimplifiedMasternodeList(PARAMS);

        SimplifiedMasternodeList newMNListTip = mnListTip.applyDiff(qrinfo.getMnListDiffTip());
        SimplifiedMasternodeList newNMListAtHMinusC = mnListAtHMinusC.applyDiff(qrinfo.getMnListDiffAtHMinusC());
        SimplifiedMasternodeList newNMListAtHMinus2C = mnListAtHMinus2C.applyDiff(qrinfo.getMnListDiffAtHMinus2C());
        SimplifiedMasternodeList newNMListAtHMinus3C = mnListAtHMinus3C.applyDiff(qrinfo.getMnListDiffAtHMinus3C());

        newMNListTip.verify(qrinfo.getMnListDiffTip().getCoinBaseTx(), qrinfo.getMnListDiffTip(), mnListTip);
        newNMListAtHMinusC.verify(qrinfo.getMnListDiffAtHMinusC().getCoinBaseTx(), qrinfo.getMnListDiffAtHMinusC(), mnListAtHMinusC);
        newNMListAtHMinus2C.verify(qrinfo.getMnListDiffAtHMinus2C().getCoinBaseTx(), qrinfo.getMnListDiffAtHMinus2C(), mnListAtHMinus2C);
        newNMListAtHMinus3C.verify(qrinfo.getMnListDiffAtHMinus3C().getCoinBaseTx(), qrinfo.getMnListDiffAtHMinus3C(), mnListAtHMinus3C);
    }
}
