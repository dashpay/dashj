package org.bitcoinj.wallet;

import com.google.common.collect.Lists;
import org.bitcoinj.coinjoin.CoinJoinClientOptions;
import org.bitcoinj.coinjoin.CoinJoinClientSession;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.Utils;
import org.bitcoinj.evolution.SimplifiedMasternodeListManager;
import org.bitcoinj.params.OuzoDevNetParams;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.script.Script;
import org.bitcoinj.store.FlatDB;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.InputStream;
import java.util.ArrayList;

import static org.bitcoinj.core.Utils.HEX;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

public class CoinJoinWalletTest {

    Wallet wallet;
    NetworkParameters TESTNET = TestNet3Params.get();
    NetworkParameters DEVNET = OuzoDevNetParams.get();
    byte[] inputTx = HEX.decode("0200000002e6915fff974f5dbe72d808b70532da3a3ce6266be0981e385ca9b1135207fa2f000000006a47304402207f10a53ae96a6fe48272f8b19f3143eee0ec9c703c3b1ac91afb2f40c838e8ff022039ec84247f4c0b3cea77a9f7d649b384ab419a72f680469a793aa4078c7e0ceb01210263c1694142038da1183cb366475c02d4cbf407ba6e03554b628464b165900fdefeffffff3d086ed6cecd1559ef1536ba0fdd4f26705fac18585013cdf893ec55d455cbdb000000006a4730440220570a0cf3afffb31defbf77682c8ee1a8f66de0b7e18f409b87ea57cb8af970cf022032d765413176196147e13b3e9c7c402a2f102c1fa7113f3771ab9f2f8ca5a8ab012103ae493762b6909af5ffee2377041aa2341a90cf208ec33c8e28054002a019e337feffffff026490eb02000000001976a914bf33ff1e9ee40f041b71fb97593e0c28df4ab11988ac00ca9a3b000000001976a914de4b757aabf173cc3daffe4315d0784d0173dd8888acfc6b0000");
    byte[][] unspentTxs = new byte[][] {
            HEX.decode("0100000001bd042c105df0608102f24b69ad384cb908ca4e7a04cc1b8ea9d6688c7302933c040000006b483045022100ccd8c54fac0a065def8cba6220e8c2d54a9f2c2124ac56dd2d75d00e3c68c9f3022057d4a88a1aec1c1b737abd8c209efc99bcb6ee7ed22800d09628dd95e5afd128012102a6152c0c8c06dba62280009f51b4407accd9b635f7b1fd17ea0618c495d3282dffffffff01915f0100000000001976a9148b6bbddaffa9ccf0897974a47f9e8b5619cb521488ac00000000"),
            HEX.decode("01000000010bb88963fc35630ba73e6e803aaba9b4df85d2f98b6be52bac1278287d6a78d6010000006b483045022100ef5ce16bedcb2ada774f2fffd37aad68ff0e357355aa45b3d00a7881267bea310220220de0c1cdd655457d1ac4624337329f7428c99ec424b645ac8926142803db14012102159ba6ca3d7d8cd2eeee9c3a8238c4b4cc1303c4e3156a792b01dc7ed85e00baffffffff1a409c0000000000001976a91408a6a271013a33adb6fc2282287542e07ed2db1f88aca1860100000000001976a9140059adea07d210ee69ce89f43038b5f07f7b106988aca1860100000000001976a91413bd42c2e065eb8ff5c7ac6734466de09fe4c63b88aca1860100000000001976a914494b9787d6306a8cba8940ccd478ef8361a629ac88aca1860100000000001976a9146a09282c96fbf6af0ef35b343125808cb339eb5f88aca1860100000000001976a91478c63b2a63bdfb550cff5c88895d6035885cb85988aca1860100000000001976a9147ce07055544b02a4798cbdd62cd020f36fff329088aca1860100000000001976a914883fd9b31fdac5c1be201a70411f1136d5ea6cca88aca1860100000000001976a914933c797fabb927932b45c67cd18882e623baf6f788aca1860100000000001976a914c25d91ed32079cfbf6f566a7b744ac2ea257a8f288aca1860100000000001976a914e660c2d1fd1a1ac4f47d4d46babce0eaace8a3dc88aca1860100000000001976a914ea83b63853b848c680856c75cc75bb08898adb9788ac4a420f00000000001976a914003a8ac1c856a1dbc8c603d2d10f2713d5a0cb9888ac4a420f00000000001976a9140c37fe3ff378ccba819942611ae8fe8307ef8a3388ac4a420f00000000001976a914142345b7d115ebb93997e262b7209665716d17cb88ac4a420f00000000001976a914305c3b92ca508d1587a2f48c107644cff401a93188ac4a420f00000000001976a914322400d21dfcc81fdf1a7c9e5253e14f9eead58a88ac4a420f00000000001976a9145bb949b72504d66d296cd0dc4411503ec2ba279088ac4a420f00000000001976a914824461f740c7fded69c59f8e4b4a5ef14556155b88ac4a420f00000000001976a9148b21b6797ca0b6a16f63d981776b89414ce22bfc88ac4a420f00000000001976a914f313ad1f5f175fad691c03b7ad660715ac50fc0888ac4a420f00000000001976a914f4a5a4656c2a06bf1f6d81ed2557bf17177fe27288ac4a420f00000000001976a914f94c942cd639027789d2f4112ba5b14953f9d43588ace4969800000000001976a91430240ba6875f7dec0ccc94149c2a901de65a195f88ace4969800000000001976a91446cf1d7bfa9b8de34212fa6137021d35e097370088accc59b039000000001976a9145708e9c8ca8d81d4c6e6f362fc228f55bb7cbffa88ac00000000"),
            HEX.decode("0100000001bd042c105df0608102f24b69ad384cb908ca4e7a04cc1b8ea9d6688c7302933c030000006a47304402202cbf38b5f07ec53dfae3a35fe02e585f8fd0ec36d8000a16ed2a5f2ac0485c71022011b4e7eda7cd903293f42e13b29d3b775afdabe00be160f5788ee07c34eb463d0121022dc88852198b96ee3bdbd68b347219ffb80ab4661dda44d79dad335e26571761ffffffff01915f0100000000001976a9143faae5a911c05cb3c1cf3c950cbd937e6c24894488ac00000000"),
            HEX.decode("0100000001bd042c105df0608102f24b69ad384cb908ca4e7a04cc1b8ea9d6688c7302933c050000006a473044022041bbb7b4876d9f93fea05529ebd023cc5deff4f7c36b9feffae5695088bda6f502204a0a6f541c347566ee7e7e213437aab3cf0c4ec6df2d995b6f2847aaeab4710b0121033292cbe0e8104bbeca48901a757603695badf39024290dd7e1b8a9270e0523f8ffffffff01915f0100000000001976a9143615cf6f655ab4b159dce260a2c2acc71c7b06a888ac00000000"),
            HEX.decode("0100000001bd042c105df0608102f24b69ad384cb908ca4e7a04cc1b8ea9d6688c7302933c0f0000006a473044022009a69cdf57300e40c6e13f1b4cd5893a05a1fcff45434996a0b9398ef5cbda7702207a983fa8f0a1d0b93a0cf42ba912171dced4a58297c1f932ea19ce6a3abbee5a012102c5e26ea197e3c33730a7eca8d6293dee8dafc1aa07c7e8d79209f084f6a14f88ffffffff013a1b0f00000000001976a914f8ca5c55b5194b584a8aa8ed9ef8fbcdd39da14588ac00000000"),
            HEX.decode("0100000001bd042c105df0608102f24b69ad384cb908ca4e7a04cc1b8ea9d6688c7302933c120000006b483045022100dc81d97c4ef4b302a424a4e0245ae85812b045e6a0f04bdf53954409933322e602202f830c17db9afdfbece9f183d244c096e821815ebfabf2689638afeb398b3880012102ce36d0470169fba24395b54d28dc1d39a8dd9ae601201027e5d3ed20e3138363ffffffff013a1b0f00000000001976a914f6863a7856dbc547d26f03e6ebb8c3db3eb12f9d88ac00000000"),
            HEX.decode("0100000001bd042c105df0608102f24b69ad384cb908ca4e7a04cc1b8ea9d6688c7302933c0a0000006b48304502210093ee419aac45950fa1b903539ea4df260e45b515dbf39b2a7d52658b30f7422f02204f2bc54b6805fadd49d021c0429de1ce3d8c32d76f33e9e49630fd378afe595f012102b2c482f23257bf701c173930b39650e5e8829c5193e5e4013337407b99c34878ffffffff01915f0100000000001976a914c3bf7bdfadc574df141435d61ce27af08cf8434288ac00000000"),
            HEX.decode("0100000001bd042c105df0608102f24b69ad384cb908ca4e7a04cc1b8ea9d6688c7302933c180000006a47304402205b9d6e722649a03ddd7f36645c138f41f383390b3b9bb4fce97531c855cdf45e022076cd7abaa5584b81b426ae0e8527ef0f17a7ba25b71ffa8fc7849803a28d6bd30121035c5e40620f5ffb4256cc00838009d8582af1e9c927aa8087e1e6e49ad2f50fc6ffffffff01d46f9800000000001976a9141cb9fcd4643821c0727ce71022c253ca9a6e924f88ac00000000")
    };

    Context context = new Context(DEVNET);

    @Before
    public void setUp() throws UnreadableWalletException {
        DeterministicSeed seed = new DeterministicSeed(
                "behind zoo develop elder book canyon host opera gun nominee lady novel",
                null, "", Utils.currentTimeSeconds());
        wallet = Wallet.fromSeed(
                context.getParams(),
                seed, Script.ScriptType.P2PKH);
        DeterministicKeyChain bip44 = DeterministicKeyChain.builder()
                .seed(seed)
                .accountPath(DerivationPathFactory.get(TESTNET).bip44DerivationPath(0))
                .build();
        //coinJoin.getKeys(KeyChain.KeyPurpose.RECEIVE_FUNDS, 500);
        wallet.addAndActivateHDChain(bip44);
        wallet.addCoinJoinKeyChain(DerivationPathFactory.get(TESTNET).coinJoinDerivationPath());
        wallet.coinJoinKeyChainGroup.getActiveKeyChain().getKeys(KeyChain.KeyPurpose.RECEIVE_FUNDS, 3000);
        wallet.addWalletTransaction(new WalletTransaction(WalletTransaction.Pool.SPENT, new Transaction(TESTNET, inputTx)));
        ArrayList<Transaction> unspentTxList = Lists.newArrayList();

        for (byte[] unspentTxData : unspentTxs) {
            Transaction unspentTx = new Transaction(TESTNET, unspentTxData);
            unspentTxList.add(unspentTx);
        }

        ArrayList<TransactionInput> currentInputs = Lists.newArrayList();

        for (Transaction unspentTx : unspentTxList) {
            currentInputs.clear();
            currentInputs.addAll(unspentTx.getInputs());
            unspentTx.clearInputs();

            for (TransactionInput input : currentInputs) {
                Transaction connected = wallet.getTransaction(input.getOutpoint().getHash());
                if (connected == null) {
                    for (Transaction tx : unspentTxList) {
                        if (tx.getTxId().equals(input.getOutpoint().getHash())) {
                            connected = tx;
                            break;
                        }
                    }
                }
                if (connected != null) {
                    TransactionInput newInput = new TransactionInput(TESTNET, unspentTx, input.getScriptBytes(), new TransactionOutPoint(TESTNET, input.getOutpoint().getIndex(), connected));
                    unspentTx.addInput(newInput);
                } else {
                    System.out.println("unknown input:" + input);
                    unspentTx.addInput(input);
                }
            }

            TransactionConfidence confidence = unspentTx.getConfidence();
            confidence.setConfidenceType(TransactionConfidence.ConfidenceType.BUILDING);
            wallet.addWalletTransaction(new WalletTransaction(WalletTransaction.Pool.UNSPENT, unspentTx));
        }

        // connect inputs
        /*for (WalletTransaction wtx : wallet.getWalletTransactions()) {
            Transaction tx = wtx.getTransaction();
            for (TransactionInput input : tx.getInputs()) {
                Transaction connected = wallet.getTransaction(input.getOutpoint().getHash());
                if (connected != null) {
                    input.getOutpoint().getConnectedOutput()
                }
            }
        }*/
        //context.initDash(true, true);
        //FlatDB<SimplifiedMasternodeListManager> smlm = new FlatDB<>(context, getClass().getResource("coinjoin.mnlist").getPath(), true);
        //smlm.load(context.masternodeListManager);
    }

    @Test
    public void keyTest() {
        ECKey key661 = ECKey.fromPublicOnly(HEX.decode("034382c0cd973ee81e6bf63f9f52bf892e451a4b67ca966de4d9974bc295244f77"));
        ECKey key = wallet.coinJoinKeyChainGroup.findKeyFromPubKey(key661.getPubKey());
        assertNotNull(key);

        ECKey key2317 = wallet.findKeyFromPubKeyHash(HEX.decode("f6863a7856dbc547d26f03e6ebb8c3db3eb12f9d"), Script.ScriptType.P2PKH);
        assertNotNull(key2317);
    }

    @Test
    @Ignore
    public void balanceTest() throws UnreadableWalletException {
        InputStream stream = getClass().getResourceAsStream("coinjoin.wallet");
        Wallet coinJoinWallet = new WalletProtobufSerializer().readWallet(stream);

        Coin balance = coinJoinWallet.getBalance();
        Balance balanceInfo = coinJoinWallet.getBalanceInfo();
        Coin nBalanceAnonimizableNonDenom = coinJoinWallet.getAnonymizableBalance(true);

        System.out.println(balance.toFriendlyString());
        System.out.println(balanceInfo);
        System.out.println(nBalanceAnonimizableNonDenom.toFriendlyString());

        CoinJoinClientSession session = new CoinJoinClientSession(coinJoinWallet);
        CoinJoinClientOptions.setRounds(4);
        CoinJoinClientOptions.setEnabled(true);
        CoinJoinClientOptions.setAmount(Coin.valueOf(1, 25));
        session.doAutomaticDenominating(true);
        System.out.println(wallet.toString(false, true, false, null));

        assertNotEquals(Coin.ZERO, balance);
    }
}
