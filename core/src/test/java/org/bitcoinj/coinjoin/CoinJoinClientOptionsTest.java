package org.bitcoinj.coinjoin;

import com.google.common.collect.Lists;
import org.bitcoinj.core.Coin;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;

import static org.bitcoinj.coinjoin.CoinJoinConstants.COINJOIN_RANDOM_ROUNDS;
import static org.bitcoinj.coinjoin.CoinJoinConstants.DEFAULT_COINJOIN_AMOUNT;
import static org.bitcoinj.coinjoin.CoinJoinConstants.DEFAULT_COINJOIN_DENOMS_GOAL;
import static org.bitcoinj.coinjoin.CoinJoinConstants.DEFAULT_COINJOIN_DENOMS_HARDCAP;
import static org.bitcoinj.coinjoin.CoinJoinConstants.DEFAULT_COINJOIN_MULTISESSION;
import static org.bitcoinj.coinjoin.CoinJoinConstants.DEFAULT_COINJOIN_ROUNDS;
import static org.bitcoinj.coinjoin.CoinJoinConstants.DEFAULT_COINJOIN_SESSIONS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CoinJoinClientOptionsTest {

    @Before
    public void startUp() {
        CoinJoinClientOptions.reset();
    }

    @After
    public void tearDown() {
        CoinJoinClientOptions.reset();
    }
    @Test
    public void getTest() {
        assertEquals(CoinJoinClientOptions.getSessions(), DEFAULT_COINJOIN_SESSIONS);
        assertEquals(CoinJoinClientOptions.getRounds(), DEFAULT_COINJOIN_ROUNDS);
        assertEquals(CoinJoinClientOptions.getRandomRounds(), COINJOIN_RANDOM_ROUNDS);
        assertEquals(CoinJoinClientOptions.getAmount(), DEFAULT_COINJOIN_AMOUNT);
        assertEquals(CoinJoinClientOptions.getDenomsGoal(), DEFAULT_COINJOIN_DENOMS_GOAL);
        assertEquals(CoinJoinClientOptions.getDenomsHardCap(), DEFAULT_COINJOIN_DENOMS_HARDCAP);

        assertFalse(CoinJoinClientOptions.isEnabled());
        assertEquals(CoinJoinClientOptions.isMultiSessionEnabled(), DEFAULT_COINJOIN_MULTISESSION);

        assertEquals(CoinJoinClientOptions.getDenominations(), CoinJoin.getStandardDenominations());

        assertEquals(CoinJoinClientOptions.getDenomsGoal(), DEFAULT_COINJOIN_DENOMS_GOAL);
        assertEquals(CoinJoinClientOptions.getDenomsHardCap(), DEFAULT_COINJOIN_DENOMS_HARDCAP);
    }

    @Test
    public void setTest() {

        CoinJoinClientOptions.setEnabled(true);
        assertTrue(CoinJoinClientOptions.isEnabled());
        CoinJoinClientOptions.setEnabled(false);
        assertFalse(CoinJoinClientOptions.isEnabled());

        CoinJoinClientOptions.setMultiSessionEnabled(!DEFAULT_COINJOIN_MULTISESSION);
        assertEquals(CoinJoinClientOptions.isMultiSessionEnabled(), !DEFAULT_COINJOIN_MULTISESSION);
        CoinJoinClientOptions.setMultiSessionEnabled(DEFAULT_COINJOIN_MULTISESSION);
        assertEquals(CoinJoinClientOptions.isMultiSessionEnabled(), DEFAULT_COINJOIN_MULTISESSION);

        CoinJoinClientOptions.setRounds(DEFAULT_COINJOIN_ROUNDS + 10);
        assertEquals(CoinJoinClientOptions.getRounds(), DEFAULT_COINJOIN_ROUNDS + 10);
        CoinJoinClientOptions.setAmount(DEFAULT_COINJOIN_AMOUNT.add(Coin.FIFTY_COINS));
        assertEquals(CoinJoinClientOptions.getAmount(), DEFAULT_COINJOIN_AMOUNT.add(Coin.FIFTY_COINS));

        ArrayList<Coin> denomsWithoutThousandths = Lists.newArrayList(CoinJoin.getStandardDenominations());
        denomsWithoutThousandths.remove(CoinJoin.getSmallestDenomination());
        CoinJoinClientOptions.removeDenomination(CoinJoin.getSmallestDenomination());
        assertEquals(denomsWithoutThousandths, CoinJoinClientOptions.getDenominations());

        CoinJoinClientOptions.setDenomsGoal(DEFAULT_COINJOIN_DENOMS_GOAL * 2);
        assertEquals(CoinJoinClientOptions.getDenomsGoal(), DEFAULT_COINJOIN_DENOMS_GOAL * 2);
        CoinJoinClientOptions.setDenomsHardCap(DEFAULT_COINJOIN_DENOMS_HARDCAP * 2);
        assertEquals(CoinJoinClientOptions.getDenomsHardCap(), DEFAULT_COINJOIN_DENOMS_HARDCAP * 2);

        CoinJoinClientOptions.removeDenomination(Denomination.SMALLEST.value);
        assertEquals(CoinJoin.getStandardDenominations().size() - 1, CoinJoinClientOptions.getDenominations().size());
        CoinJoinClientOptions.resetDenominations();
        assertEquals(CoinJoin.getStandardDenominations(), CoinJoinClientOptions.getDenominations());
    }
}
