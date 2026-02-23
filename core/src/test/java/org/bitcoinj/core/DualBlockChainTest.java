package org.bitcoinj.core;

import com.google.common.collect.Lists;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.store.MemoryBlockStore;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

import java.math.BigInteger;
import java.util.ArrayList;

public class DualBlockChainTest {

    AbstractBlockChain headersChain;
    BlockStore headersStore;

    AbstractBlockChain blockChain;
    BlockStore blockStore;

    TestNet3Params TESTNET = TestNet3Params.get();
    Context context = new Context(TESTNET);

    DualBlockChain dualBlockChain;

    static Sha256Hash block1Hash = Sha256Hash.wrap("0000047d24635e347be3aaaeb66c26be94901a2f962feccd4f95090191f208c1");

    @Before
    public void setUp() throws BlockStoreException {
        StoredBlock genesisBlock = new StoredBlock(TESTNET.getGenesisBlock(), BigInteger.ZERO, 0);
        blockStore = new MemoryBlockStore(TESTNET);
        blockStore.put(genesisBlock);
        blockChain = new BlockChain(TESTNET, blockStore);
        blockChain.setChainHead(genesisBlock);

        StoredBlock block1 = new StoredBlock(
                new Block(
                    TESTNET,
                    2,
                    TESTNET.getGenesisBlock().getHash(),
                    Sha256Hash.wrap("b4fd581bc4bfe51a5a66d8b823bd6ee2b492f0ddc44cf7e820550714cedc117f"),
                    1398712771,
                    0x1e0fffff,
                    31475,
                    new ArrayList<>()
                ),
                BigInteger.ONE,
                1
        );

        headersStore = new MemoryBlockStore(TESTNET);
        headersStore.put(new StoredBlock(TESTNET.getGenesisBlock(), BigInteger.ZERO, 0));
        headersStore.put(block1);
        headersChain = new BlockChain(TESTNET, headersStore);
        headersChain.setChainHead(block1);
        dualBlockChain = new DualBlockChain(headersChain, blockChain);
    }

    @Test
    public void testGetBlockChain() {
        assertEquals(blockChain, dualBlockChain.getBlockChain());
    }

    @Test
    public void testGetHeadersChain() {
        assertEquals(headersChain, dualBlockChain.getHeadersChain());
    }

    @Test
    public void testGetBlockHeight() {
        assertEquals(0, dualBlockChain.getBlockHeight(TESTNET.getGenesisBlock().getHash()));
        assertEquals(1, dualBlockChain.getBlockHeight(block1Hash));
    }

    @Test
    public void testGetBestChainHeight() {
        assertEquals(block1Hash, dualBlockChain.getBlock(1).getHeader().getHash());
    }

    @Test
    public void testGetBlockUsingHash() {
        assertEquals(TESTNET.getGenesisBlock().getHash(), dualBlockChain.getBlock(TESTNET.getGenesisBlock().getHash()).getHeader().getHash());
        assertEquals(block1Hash, dualBlockChain.getBlock(1).getHeader().getHash());
    }

    @Test
    public void testGetBlockAncestor() {
        StoredBlock block = dualBlockChain.getChainHead();
        assertEquals(TESTNET.getGenesisBlock().getHash(), dualBlockChain.getBlockAncestor(block, block.getHeight() - 1).getHeader().getHash());
    }

    @Test
    public void testGetBlockUsingHeight() {
        assertEquals(block1Hash, dualBlockChain.getBlock(1).getHeader().getHash());
    }

    @Test
    public void testGetChainHead() {
        assertEquals(block1Hash, dualBlockChain.getChainHead().getHeader().getHash());
    }
}

