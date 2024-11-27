package org.bitcoinj.governance;

import org.bitcoinj.core.Context;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static org.bitcoinj.governance.GovernanceObject.*;

public class GovernanceTriggerManager {


    private static final Logger log = LoggerFactory.getLogger(GovernanceTriggerManager.class);
    Context context;

    private HashMap<Sha256Hash, Superblock> mapTrigger;
    private GovernanceManager governanceManager;

    /**
     *   Add Governance Object
     */

    public boolean addNewTrigger(Sha256Hash nHash) {
        log.info("CGovernanceTriggerManager::AddNewTrigger: Start");
        governanceManager.lock.lock();
        try {

            // IF WE ALREADY HAVE THIS HASH, RETURN
            if (mapTrigger.containsKey(nHash)) {
                log.info("CGovernanceTriggerManager::AddNewTrigger: Already have hash" + ", nHash = " + Utils.HEX.encode(nHash.getBytes()) +
                        ", count = " + (mapTrigger.containsKey(nHash) ? 1 : 0) + ", mapTrigger.size() = " + mapTrigger.size())
                ;
                return false;
            }

            Superblock pSuperblock = new Superblock(context.getParams());
            try {
                Superblock pSuperblockTmp = new Superblock(context.getParams(), nHash);
            } catch (SuperblockException e) {
                log.info("CGovernanceTriggerManager::AddNewTrigger -- Error creating Superblock: {}", e.getMessage());
                return false;
            } catch (Exception e) {
                log.info("CGovernanceTriggerManager::AddNewTrigger: Unknown Error creating Superblock");
                return false;
            }

            pSuperblock.setStatus(SEEN_OBJECT_IS_VALID);

            log.info("CGovernanceTriggerManager::AddNewTrigger: Inserting trigger");
            mapTrigger.put(nHash, pSuperblock);

            log.info("CGovernanceTriggerManager::AddNewTrigger: End");

            return true;
        } finally {
            governanceManager.lock.unlock();
        }
    }

    public GovernanceTriggerManager(Context context, GovernanceManager governanceManager) {
        this.mapTrigger = new HashMap<Sha256Hash, Superblock>();
        this.context = context;
    }

    /**
     *
     *   Clean And Remove
     *
     */

    public void cleanAndRemove() {
        log.info("gobject--CGovernanceTriggerManager::CleanAndRemove -- Start");
        governanceManager.lock.lock();
        try {

            // LOOK AT THESE OBJECTS AND COMPILE A VALID LIST OF TRIGGERS
            for (Map.Entry<Sha256Hash, Superblock> it : mapTrigger.entrySet()) {
                //int nNewStatus = -1;
                GovernanceObject pObj = governanceManager.findGovernanceObject(it.getKey());
                if (pObj == null) {
                    continue;
                }
                Superblock pSuperblock = it.getValue();
                if (pSuperblock == null) {
                    continue;
                }
                // IF THIS ISN'T A TRIGGER, WHY ARE WE HERE?
                if (pObj.getObjectType() != GOVERNANCE_OBJECT_TRIGGER) {
                    pSuperblock.setStatus(SEEN_OBJECT_ERROR_INVALID);
                }
            }

            // Remove triggers that are invalid or already executed
            log.info("gobject--CGovernanceTriggerManager::CleanAndRemove -- mapTrigger.size() = {}", mapTrigger.size());
            Iterator<Map.Entry<Sha256Hash, Superblock>> it = mapTrigger.entrySet().iterator();
            while (it.hasNext()) {
                boolean remove = false;
                Map.Entry<Sha256Hash, Superblock> entry = it.next();
                Superblock pSuperblock = entry.getValue();
                if (pSuperblock == null) {
                    log.info("gobject--CGovernanceTriggerManager::CleanAndRemove -- NULL Superblock marked for removal");
                    remove = true;
                } else {
                    log.info("gobject--CGovernanceTriggerManager::CleanAndRemove -- Superblock status = {}", pSuperblock.getStatus());
                    switch (pSuperblock.getStatus()) {
                        case SEEN_OBJECT_ERROR_INVALID:
                        case SEEN_OBJECT_UNKNOWN:
                            log.info("gobject--CGovernanceTriggerManager::CleanAndRemove -- Unknown or invalid trigger found");
                            remove = true;
                            break;
                        case SEEN_OBJECT_IS_VALID:
                        case SEEN_OBJECT_EXECUTED: {
                            int nTriggerBlock = pSuperblock.getBlockStart();
                            // Rough approximation: a cycle of Superblock ++
                            int nExpirationBlock = nTriggerBlock + GOVERNANCE_TRIGGER_EXPIRATION_BLOCKS;
                            log.info("gobject--CGovernanceTriggerManager::CleanAndRemove -- nTriggerBlock = {}, nExpirationBlock = {}", nTriggerBlock, nExpirationBlock);
                            if (governanceManager.getCachedBlockHeight() > nExpirationBlock) {
                                log.info("gobject--CGovernanceTriggerManager::CleanAndRemove -- Outdated trigger found");
                                remove = true;
                                GovernanceObject pgovobj = pSuperblock.getGovernanceObject();
                                if (pgovobj != null) {
                                    log.info("gobject--CGovernanceTriggerManager::CleanAndRemove -- Expiring outdated object: {}", pgovobj.getHash().toString());
                                    pgovobj.setExpired(true);
                                    pgovobj.setDeletionTime(Utils.currentTimeSeconds());
                                }
                            }
                        }
                        break;
                        default:
                            break;
                    }
                }

                if (remove) {
                    String strdata = "NULL";
                    GovernanceObject pgovobj = pSuperblock.getGovernanceObject();
                    if (pgovobj != null) {
                        strdata = pgovobj.getDataAsPlainString();
                    }
                    log.info("CGovernanceTriggerManager::CleanAndRemove: Removing object: " + strdata);
                    log.info("gobject--CGovernanceTriggerManager::CleanAndRemove -- Removing trigger object");
                    it.remove();
                }
            }
        } finally {
            governanceManager.lock.unlock();
        }

        log.info("CGovernanceTriggerManager::CleanAndRemove: End");
    }

    /**
     *   Get Active Triggers
     *
     *   - Look through triggers and scan for active ones
     *   - Return the triggers in a list
     */

    public ArrayList<Superblock> getActiveTriggers() {
        governanceManager.lock.lock();
        try {
            ArrayList<Superblock> vecResults = new ArrayList<Superblock>();

            log.info("GetActiveTriggers: mapTrigger.size() = " + mapTrigger.size());

            // LOOK AT THESE OBJECTS AND COMPILE A VALID LIST OF TRIGGERS
            Iterator<Map.Entry<Sha256Hash, Superblock>> it = mapTrigger.entrySet().iterator();
            while (it.hasNext()) {

                Map.Entry<Sha256Hash, Superblock> entry = it.next();

                GovernanceObject pObj = governanceManager.findGovernanceObject(entry.getKey());

                if (pObj != null) {
                    log.info("GetActiveTriggers: pObj->GetDataAsString() = " + pObj.getDataAsPlainString());
                    vecResults.add(entry.getValue());
                }
            }

            log.info("GetActiveTriggers: vecResults.size() = " + vecResults.size());

            return vecResults;
        } finally {
            governanceManager.lock.unlock();
        }
    }


}

