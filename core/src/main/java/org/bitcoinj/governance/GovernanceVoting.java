package org.bitcoinj.governance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.bitcoinj.governance.GovernanceVote.VoteOutcome.*;
import static org.bitcoinj.governance.GovernanceVote.VoteSignal.*;

public class GovernanceVoting {
    private static final Logger log = LoggerFactory.getLogger(GovernanceVoting.class);

    public static String convertOutcomeToString(GovernanceVote.VoteOutcome nOutcome) {

        switch(nOutcome)
        {
            case VOTE_OUTCOME_NONE:
                return "NONE";
            case VOTE_OUTCOME_YES:
                return "YES";
            case VOTE_OUTCOME_NO:
                return "NO";
            case VOTE_OUTCOME_ABSTAIN:
                return "ABSTAIN";
        }
        return "error";
    }
    public static String convertSignalToString(GovernanceVote.VoteSignal nSignal) {

        String strReturn = "NONE";
        switch(nSignal)
        {
            case VOTE_SIGNAL_NONE:
                strReturn = "NONE";
                break;
            case VOTE_SIGNAL_FUNDING:
                strReturn = "FUNDING";
                break;
            case VOTE_SIGNAL_VALID:
                strReturn = "VALID";
                break;
            case VOTE_SIGNAL_DELETE:
                strReturn = "DELETE";
                break;
            case VOTE_SIGNAL_ENDORSED:
                strReturn = "ENDORSED";
                break;
            case VOTE_SIGNAL_NOOP1:
                strReturn = "NOOP1";
                break;
            case VOTE_SIGNAL_NOOP2:
                strReturn = "NOOP2";
                break;
            case VOTE_SIGNAL_NOOP3:
                strReturn = "NOOP3";
                break;
            case VOTE_SIGNAL_NOOP4:
                strReturn = "NOOP4";
                break;
            case VOTE_SIGNAL_NOOP5:
                strReturn = "NOOP5";
                break;
            case VOTE_SIGNAL_NOOP6:
                strReturn = "NOOP6";
                break;
            case VOTE_SIGNAL_NOOP7:
                strReturn = "NOOP7";
                break;
            case VOTE_SIGNAL_NOOP8:
                strReturn = "NOOP8";
                break;
            case VOTE_SIGNAL_NOOP9:
                strReturn = "NOOP9";
                break;
            case VOTE_SIGNAL_NOOP10:
                strReturn = "NOOP10";
                break;
            case VOTE_SIGNAL_NOOP11:
                strReturn = "NOOP11";
                break;
            case VOTE_SIGNAL_CUSTOM1:
                strReturn = "CUSTOM1";
                break;
            case VOTE_SIGNAL_CUSTOM2:
                strReturn = "CUSTOM2";
                break;
            case VOTE_SIGNAL_CUSTOM3:
                strReturn = "CUSTOM3";
                break;
            case VOTE_SIGNAL_CUSTOM4:
                strReturn = "CUSTOM4";
                break;
            case VOTE_SIGNAL_CUSTOM5:
                strReturn = "CUSTOM5";
                break;
            case VOTE_SIGNAL_CUSTOM6:
                strReturn = "CUSTOM6";
                break;
            case VOTE_SIGNAL_CUSTOM7:
                strReturn = "CUSTOM7";
                break;
            case VOTE_SIGNAL_CUSTOM8:
                strReturn = "CUSTOM8";
                break;
            case VOTE_SIGNAL_CUSTOM9:
                strReturn = "CUSTOM9";
                break;
            case VOTE_SIGNAL_CUSTOM10:
                strReturn = "CUSTOM10";
                break;
            case VOTE_SIGNAL_CUSTOM11:
                strReturn = "CUSTOM11";
                break;
            case VOTE_SIGNAL_CUSTOM12:
                strReturn = "CUSTOM12";
                break;
            case VOTE_SIGNAL_CUSTOM13:
                strReturn = "CUSTOM13";
                break;
            case VOTE_SIGNAL_CUSTOM14:
                strReturn = "CUSTOM14";
                break;
            case VOTE_SIGNAL_CUSTOM15:
                strReturn = "CUSTOM15";
                break;
            case VOTE_SIGNAL_CUSTOM16:
                strReturn = "CUSTOM16";
                break;
            case VOTE_SIGNAL_CUSTOM17:
                strReturn = "CUSTOM17";
                break;
            case VOTE_SIGNAL_CUSTOM18:
                strReturn = "CUSTOM18";
                break;
            case VOTE_SIGNAL_CUSTOM19:
                strReturn = "CUSTOM19";
                break;
            case VOTE_SIGNAL_CUSTOM20:
                strReturn = "CUSTOM20";
                break;
        }
        return strReturn;
    }
    public static GovernanceVote.VoteOutcome convertVoteOutcome(String strVoteOutcome) {
        GovernanceVote.VoteOutcome eVote = VOTE_OUTCOME_NONE;
        if (strVoteOutcome.equals("yes")) {
            eVote = VOTE_OUTCOME_YES;
        } else if (strVoteOutcome.equals("no")) {
            eVote = VOTE_OUTCOME_NO;
        } else if (strVoteOutcome.equals("abstain")) {
            eVote = VOTE_OUTCOME_ABSTAIN;
        }
        return eVote;
    }
    public static GovernanceVote.VoteSignal convertVoteSignal(String strVoteSignal) {
        GovernanceVote.VoteSignal eSignal = VOTE_SIGNAL_NONE;
        if (strVoteSignal.equals("funding")) {
            eSignal = VOTE_SIGNAL_FUNDING;
        } else if (strVoteSignal.equals("valid")) {
            eSignal = VOTE_SIGNAL_VALID;
        }
        if (strVoteSignal.equals("delete")) {
            eSignal = VOTE_SIGNAL_DELETE;
        }
        if (strVoteSignal.equals("endorsed")) {
            eSignal = VOTE_SIGNAL_ENDORSED;
        }

        if (eSignal != VOTE_SIGNAL_NONE) {
            return eSignal;
        }

        // ID FIVE THROUGH CUSTOM_START ARE TO BE USED BY GOVERNANCE ENGINE / TRIGGER SYSTEM

        // convert custom sentinel outcomes to integer and store
        try {
            int i = Integer.parseInt(strVoteSignal);
            if (i < VOTE_SIGNAL_CUSTOM1.getValue() || i > VOTE_SIGNAL_CUSTOM20.getValue()) {
                eSignal = VOTE_SIGNAL_NONE;
            } else {
                eSignal = GovernanceVote.VoteSignal.fromValue(i);
            }
        } catch (Exception e) {
            log.info("CGovernanceVote::ConvertVoteSignal: error : " + e.getMessage());
        }

        return eSignal;
    }
}

