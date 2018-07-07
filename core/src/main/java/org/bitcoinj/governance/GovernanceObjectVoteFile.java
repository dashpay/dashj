package org.bitcoinj.governance;

import org.bitcoinj.core.*;

import java.io.IOException;
import java.io.OutputStream;
import java.util.*;

/**
 * Represents the collection of votes associated with a given CGovernanceObject
 * Recently received votes are held in memory until a maximum size is reached after
 * which older votes a flushed to a disk file.
 *
 * Note: This is a stub implementation that doesn't limit the number of votes held
 * in memory and doesn't flush to disk.
 */
public class GovernanceObjectVoteFile extends Message {

	private static final int MAX_MEMORY_VOTES = -1;

	private int nMemoryVotes;

	private LinkedList<GovernanceVote> listVotes;

	private HashMap<Sha256Hash, GovernanceVote> mapVoteIndex;

	public GovernanceObjectVoteFile() {
		this.nMemoryVotes = 0;
		this.listVotes =  new LinkedList<GovernanceVote>();
		this.mapVoteIndex = new HashMap<Sha256Hash, GovernanceVote>();
	}

	public GovernanceObjectVoteFile(GovernanceObjectVoteFile other) {
		this.nMemoryVotes = other.nMemoryVotes;
		this.listVotes = other.listVotes;
		this.mapVoteIndex = new HashMap<Sha256Hash, GovernanceVote>();
		rebuildIndex();
	}

	public GovernanceObjectVoteFile(NetworkParameters params, byte [] payload, int offset) {
		super(params, payload, offset);
		rebuildIndex();
	}

	/**
	 * Add a vote to the file
	 */
	public void addVote(GovernanceVote vote) {
		listVotes.addFirst(vote);
		mapVoteIndex.put(vote.getHash(), vote);
		++nMemoryVotes;
	}

	/**
	 * Return true if the vote with this hash is currently cached in memory
	 */
//C++ TO JAVA CONVERTER WARNING: 'const' methods are not available in Java:
//ORIGINAL LINE: boolean HasVote(const Sha256Hash& nHash) const;
	public boolean hasVote(Sha256Hash nHash) {
		GovernanceVote it = mapVoteIndex.get(nHash);
		if (it == null) {
			return false;
		}
		return true;
	}

	/**
	 * Retrieve a vote cached in memory
	 */
	public GovernanceVote getVote(Sha256Hash nHash) {
		GovernanceVote it = mapVoteIndex.get(nHash);
		if (it == null) {
			return null;
		}
		return it;  //TODO:  This may return a bad result or next will advance the iterator to something else.
	}

	public final int getVoteCount() {
		return nMemoryVotes;
	}

	public ArrayList<GovernanceVote> getVotes() {
		ArrayList<GovernanceVote> vecResult = new ArrayList<GovernanceVote>();
		for (GovernanceVote vote : listVotes) {
			vecResult.add(vote);
		}
		return vecResult;
	}

	public void removeVotesFromMasternode(TransactionOutPoint outpointMasternode) {
		Iterator<GovernanceVote> it = listVotes.iterator();
		while (it.hasNext()) {
			GovernanceVote vote = it.next();
			if (vote.getMasternodeOutpoint().equals(outpointMasternode)) {
				--nMemoryVotes;
				mapVoteIndex.remove(vote.getHash());
				it.remove();
			}
		}
	}

	public void rebuildIndex() {
		mapVoteIndex.clear();
		nMemoryVotes = 0;
		ListIterator<GovernanceVote> it = listVotes.listIterator();
		while (it.hasNext()) {
			GovernanceVote vote = it.next();
			Sha256Hash nHash = vote.getHash();
			if (mapVoteIndex.get(nHash) == null) {
				mapVoteIndex.put(nHash, vote);
				++nMemoryVotes;
			} else {
				it.remove();  //TODO:  this looks like a bug
			}
		}
	}

	@Override
	protected void parse() throws ProtocolException {
		nMemoryVotes = (int)readUint32();
		int size = (int)readVarInt();
		listVotes = new LinkedList<GovernanceVote>();
		for(int i = 0; i < size; ++i) {
			GovernanceVote vote = new GovernanceVote(params, payload, cursor);
			cursor += vote.getMessageSize();
		}

		length = cursor - offset;
		mapVoteIndex = new HashMap<Sha256Hash, GovernanceVote>();
		rebuildIndex();
	}

	@Override
	protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {
		Utils.uint32ToByteStreamLE(nMemoryVotes, stream);
		stream.write(new VarInt(listVotes.size()).encode());
		for(GovernanceVote vote: listVotes) {
			vote.bitcoinSerialize(stream);
		}
	}

}

