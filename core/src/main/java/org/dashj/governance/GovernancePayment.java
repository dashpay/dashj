package org.dashj.governance;

import org.dashj.core.Address;
import org.dashj.core.Coin;
import org.dashj.script.Script;
import org.dashj.script.ScriptBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *   Governance Object Payment
 *
 */

public class GovernancePayment {

    private static final Logger log = LoggerFactory.getLogger(GovernancePayment.class);

    private boolean fValid;

    public Script script;
    public Coin nAmount;

    public GovernancePayment() {
        this.fValid = false;
        this.script = null;
        this.nAmount = Coin.ZERO;
    }

    public GovernancePayment(Address addrIn, Coin nAmountIn) {
        this.fValid = false;
        this.script = null;
        this.nAmount = Coin.ZERO;
        try {
            script = ScriptBuilder.createOutputScript(addrIn);
            nAmount = Coin.valueOf(nAmountIn.getValue());
            fValid = true;
        } catch (Exception e) {
            log.info("CGovernancePayment Payment not valid: addrIn = {}, nAmountIn = {}, what = {}", addrIn.toString(), nAmountIn, e.getMessage());
        }
    }

    public final boolean isValid() {
        return fValid;
    }
}