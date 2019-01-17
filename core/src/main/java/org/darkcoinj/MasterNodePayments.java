package org.darkcoinj;

import org.bitcoinj.core.MasterNodePaymentWinner;

import java.util.ArrayList;

/**
 * Created by Eric on 2/8/2015.
 */

//
// Masternode Payments Class
// Keeps track of who should get paid for which blocks
//
@Deprecated
public class MasterNodePayments {
    ArrayList<MasterNodePaymentWinner> vWinning;
    int nSyncedFromPeer;
    String strMasterPrivKey;
    String strTestPubKey;
    String strMainPubKey;
    boolean enabled;

    MasterNodePayments() {
        strMainPubKey = "04549ac134f694c0243f503e8c8a9a986f5de6610049c40b07816809b0d1d06a21b07be27b9bb555931773f62ba6cf35a25fd52f694d4e1106ccd237a7bb899fdd";
        strTestPubKey = "046f78dcf911fbd61910136f7f0f8d90578f68d0b3ac973b5040fb7afb501b5939f39b108b0569dca71488f5bbf498d92e4d1194f6f941307ffd95f75e76869f0e";
        enabled = false;
    }
    /*
    bool SetPrivKey(std::string strPrivKey);
    bool CheckSignature(CMasternodePaymentWinner& winner);
    bool Sign(CMasternodePaymentWinner& winner);

    // Deterministically calculate a given "score" for a masternode depending on how close it's hash is
    // to the blockHeight. The further away they are the better, the furthest will win the election
    // and get paid this block
    //

    uint64_t CalculateScore(Sha256Hash blockHash, CTxIn& vin);
    bool GetWinningMasternode(int nBlockHeight, CTxIn& vinOut);
    bool AddWinningMasternode(CMasternodePaymentWinner& winner);
    bool ProcessBlock(int nBlockHeight);
    void Relay(CMasternodePaymentWinner& winner);
    void Sync(CNode* node);
    void CleanPaymentList();
    int LastPayment(CMasterNode& mn);

    //slow
    bool GetBlockPayee(int nBlockHeight, CScript& payee);
    */
}
