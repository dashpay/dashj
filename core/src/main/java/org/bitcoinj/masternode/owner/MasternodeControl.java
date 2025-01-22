package org.bitcoinj.masternode.owner;

import org.bitcoinj.core.*;
import org.bitcoinj.evolution.Masternode;
import org.bitcoinj.evolution.SimplifiedMasternodeListManager;
import org.bitcoinj.governance.GovernanceException;
import org.bitcoinj.governance.GovernanceManager;
import org.bitcoinj.governance.GovernanceVote;
import org.bitcoinj.governance.GovernanceVoteBroadcast;
import org.bitcoinj.governance.GovernanceVoteBroadcaster;
import org.bitcoinj.governance.GovernanceVoting;

import java.io.File;

import static org.bitcoinj.governance.GovernanceVote.VoteOutcome.VOTE_OUTCOME_NONE;
import static org.bitcoinj.governance.GovernanceVote.VoteSignal.VOTE_SIGNAL_NONE;

/**
 * Created by Eric on 7/8/2018.
 */
public class MasternodeControl {
    /**
     * The Config.
     */
    MasternodeConfig masternodeConfig;
    /**
     * The Context.
     */
    Context context;
    private final SimplifiedMasternodeListManager masternodeListManager;
    private final GovernanceManager governanceManager;
    private final GovernanceVoteBroadcaster broadcaster;

    /**
     * Instantiates a new Masternode control.
     *
     * @param context              the context
     * @param masternodeConfigFile the masternode config file
     */
    public MasternodeControl(Context context, File masternodeConfigFile,
                             GovernanceVoteBroadcaster broadcaster,
                             SimplifiedMasternodeListManager masternodeListManager,
                             GovernanceManager governanceManager) {
        this.context = context;
        masternodeConfig = new MasternodeConfig(masternodeConfigFile);
        this.masternodeListManager = masternodeListManager;
        this.governanceManager = governanceManager;
        this.broadcaster = broadcaster;
    }

    /**
     * Instantiates a new Masternode control.
     *
     * @param context              the context
     * @param masternodeConfigFile the masternode config file name
     */
    public MasternodeControl(Context context, String masternodeConfigFile,
                             GovernanceVoteBroadcaster broadcaster,
                             SimplifiedMasternodeListManager masternodeListManager,
                             GovernanceManager governanceManager) {
        this(context, new File(masternodeConfigFile), broadcaster, masternodeListManager, governanceManager);
    }

    public void addConfig(String alias, String ip, String privKey, String txHash, String outputIndex) {
        masternodeConfig.add(alias, ip, privKey, txHash, outputIndex);
    }

    public boolean load() {
        StringBuilder errorMessage = new StringBuilder();
        return masternodeConfig.read(errorMessage);
    }

    /**
     * Vote with a single masternode identified by alias.  Returns true if successful.
     *
     * @param alias          the alias
     * @param governanceHash the hash of the governance object
     * @param voteSignal     the vote signal [funding|valid|delete]
     * @param voteOutcome    the vote outcome [yes|no|abstain]
     * @param errorMessage   the error message with details on failure
     * @return true if successful
     */
    public GovernanceVoteBroadcast voteAlias(String alias, String governanceHash, String voteSignal, String voteOutcome, StringBuilder errorMessage) {
        Sha256Hash hash = Sha256Hash.wrap(Utils.HEX.decode(governanceHash));

        // CONVERT NAMED SIGNAL/ACTION AND CONVERT

        GovernanceVote.VoteSignal eVoteSignal = GovernanceVoting.convertVoteSignal(voteSignal);
        if(eVoteSignal == VOTE_SIGNAL_NONE) {
            errorMessage.append("Invalid vote signal ("+voteSignal+"). Please using one of the following: " +
                    "(funding|valid|delete|endorsed)");
            return null;
        }

        GovernanceVote.VoteOutcome eVoteOutcome = GovernanceVoting.convertVoteOutcome(voteOutcome);
        if(eVoteOutcome == VOTE_OUTCOME_NONE) {
            errorMessage.append("Invalid vote outcome("+voteOutcome+"). Please use one of the following: 'yes', 'no' or 'abstain'");
            return null;
        }

        // EXECUTE VOTE FOR EACH MASTERNODE, COUNT SUCCESSES VS FAILURES

        int nSuccessful = 0;
        int nFailed = 0;
        GovernanceVoteBroadcast broadcast = null;


        for (MasternodeConfig.MasternodeEntry mne : masternodeConfig.getEntries()) {
            // IF WE HAVE A SPECIFIC NODE REQUESTED TO VOTE, DO THAT
            if (!alias.equals(mne.getAlias())) continue;

            // INIT OUR NEEDED VARIABLES TO EXECUTE THE VOTE
            StringBuilder strError = new StringBuilder();
            MasternodeSignature vchMasterNodeSignature;
            String strMasterNodeSignMessage;

            PublicKey pubKeyCollateralAddress;
            ECKey keyCollateralAddress;
            PublicKey pubKeyMasternode;
            ECKey keyMasternode;

            // SETUP THE SIGNING KEY FROM MASTERNODE.CONF ENTRY

            keyMasternode = MessageSigner.getKeysFromSecret(mne.getPrivKey(), strError);
            if (keyMasternode == null) {
                nFailed++;
                errorMessage.append(alias + String.format("-- failure --Invalid masternode key %s.\n", mne.getPrivKey()));
                continue;
            }
            pubKeyMasternode = new PublicKey(keyMasternode.getPubKey());

            // SEARCH FOR THIS MASTERNODE ON THE NETWORK, THE NODE MUST BE ACTIVE TO VOTE

            Sha256Hash nTxHash = Sha256Hash.wrap(Utils.HEX.decode(mne.getTxHash()));

            int nOutputIndex = 0;
            try {
                nOutputIndex = Integer.parseInt(mne.getOutputIndex());
            } catch (NumberFormatException x) {
                continue;
            }

            TransactionOutPoint outpoint = new TransactionOutPoint(context.getParams(), nOutputIndex, nTxHash);

            Masternode mn = masternodeListManager.getListAtChainTip().getMNByCollateral(outpoint);

            if(mn == null) {
                nFailed++;
                errorMessage.append(alias + "-- failure --Masternode must be publicly available on network to vote. Masternode not found.\n");
                continue;
            }

            // CREATE NEW GOVERNANCE OBJECT VOTE WITH OUTCOME/SIGNAL

            GovernanceVote vote = new GovernanceVote(context.getParams(), outpoint, hash, eVoteSignal, eVoteOutcome);
            if(!vote.sign(keyMasternode, pubKeyMasternode)) {
                nFailed++;
                errorMessage.append(alias + "-- failure --Failure to sign\n");
                continue;
            }

            // UPDATE LOCAL DATABASE WITH NEW OBJECT SETTINGS

            GovernanceException exception = new GovernanceException();
            if(governanceManager.processVoteAndRelay(vote, exception)) {
                nSuccessful++;
                errorMessage.append(alias).append("voting successful\n");
                broadcast = broadcaster.broadcastGovernanceVote(vote);
            } else {
                nFailed++;
                errorMessage.append(alias).append("-- failure --").append(exception.getMessage()).append("\n");
            }
        }

        // REPORT STATS TO THE USER

        errorMessage.append("overall result : ").append(String.format("Voted successfully %d time(s) and failed %d time(s).", nSuccessful, nFailed));

        return broadcast;
    }

    /**
     * Vote many boolean.
     *
     * @param alias          the alias
     * @param governanceHash the governance hash
     * @param voteSignal     the vote signal
     * @param voteOutcome    the vote outcome
     * @param errorMessage   the error message with details on failure
     * @return true if all voting is successful
     */
    public boolean voteMany(String alias, String governanceHash, String voteSignal, String voteOutcome, StringBuilder errorMessage) {
        Sha256Hash hash = Sha256Hash.wrap(Utils.HEX.decode(governanceHash));

        // CONVERT NAMED SIGNAL/ACTION AND CONVERT

        GovernanceVote.VoteSignal eVoteSignal = GovernanceVoting.convertVoteSignal(voteSignal);
        if(eVoteSignal == VOTE_SIGNAL_NONE) {
            errorMessage.append("Invalid vote signal ("+voteSignal+"). Please using one of the following: " +
                    "(funding|valid|delete|endorsed)");
            return false;
        }

        GovernanceVote.VoteOutcome eVoteOutcome = GovernanceVoting.convertVoteOutcome(voteOutcome);
        if(eVoteOutcome == VOTE_OUTCOME_NONE) {
            errorMessage.append("Invalid vote outcome("+voteOutcome+"). Please use one of the following: 'yes', 'no' or 'abstain'");
            return false;
        }

        int nSuccessful = 0;
        int nFailed = 0;


        for (MasternodeConfig.MasternodeEntry mne : masternodeConfig.getEntries()) {
            StringBuilder strError = new StringBuilder();
            MasternodeSignature vchMasterNodeSignature;
            String strMasterNodeSignMessage;

            PublicKey pubKeyCollateralAddress;
            ECKey keyCollateralAddress;
            PublicKey pubKeyMasternode;
            ECKey keyMasternode;

            keyMasternode = MessageSigner.getKeysFromSecret(mne.getPrivKey(), strError);
            if (keyMasternode == null) {
                nFailed++;
                errorMessage.append(alias + String.format("-- failure --Invalid masternode key %s.\n", mne.getPrivKey()));
                continue;
            }
            pubKeyMasternode = new PublicKey(keyMasternode.getPubKey());

            Sha256Hash nTxHash = Sha256Hash.wrap(Utils.HEX.decode(mne.getTxHash()));

            int nOutputIndex = 0;
            try {
                nOutputIndex = Integer.parseInt(mne.getOutputIndex());
            } catch (NumberFormatException x) {
                continue;
            }

            TransactionOutPoint outpoint = new TransactionOutPoint(context.getParams(), nOutputIndex, nTxHash);

            Masternode mn = masternodeListManager.getListAtChainTip().getMNByCollateral(outpoint);

            if(mn == null) {
                nFailed++;
                errorMessage.append(alias + "-- failure --Masternode must be publicly available on network to vote. Masternode not found.\n");
                continue;
            }

            // CREATE NEW GOVERNANCE OBJECT VOTE WITH OUTCOME/SIGNAL

            GovernanceVote vote = new GovernanceVote(context.getParams(), outpoint, hash, eVoteSignal, eVoteOutcome);
            if(!vote.sign(keyMasternode, pubKeyMasternode)) {
                nFailed++;
                errorMessage.append(alias + "-- failure --Failure to sign\n");
                continue;
            }

            // UPDATE LOCAL DATABASE WITH NEW OBJECT SETTINGS

            GovernanceException exception = new GovernanceException();
            if(governanceManager.processVoteAndRelay(vote, exception)) {
                nSuccessful++;
                errorMessage.append(alias + "voting successful\n");
            } else {
                nFailed++;
                errorMessage.append(alias + "-- failure --" + exception.getMessage() + "\n");
            }
        }

        // REPORT STATS TO THE USER

        errorMessage.append("overall result : " + String.format("Voted successfully %d time(s) and failed %d time(s).", nSuccessful, nFailed));

        return nFailed == 0;
    }

}
