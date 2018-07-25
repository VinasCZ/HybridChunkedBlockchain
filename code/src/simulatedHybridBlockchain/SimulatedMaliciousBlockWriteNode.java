package simulatedHybridBlockchain;

import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;

public class SimulatedMaliciousBlockWriteNode extends SimulatedNode {

	private int lastBatch;
	
	private int lastChunkHeight = 0;
	
	public SimulatedMaliciousBlockWriteNode(SimulatedNetwork network, String id) {
		super(network, id);
		
		this.lastBatch = network.nodes.size();
	}
	
	public SimulatedMaliciousBlockWriteNode() {}

	@Override
	protected int executeSpecificLogic(int networkTick) {
		if(this.activeOutgoingConnections.size() > 0) {
			Block block = this.createValidBlock();
			
			if(block != null) {
				switch(this.network.rng.nextInt(5)) {
					case 0:
						NodeSignal signal = new NodeSignalNewBlock(this.getId(), block.toString());
						this.parseSignal(signal, networkTick);
						this.network.log.special(this.getId(), "BLOCK_CREATION", "Will create a new block: Regular", networkTick);
						break;
					case 1:
						block = this.createInvalidHashChain(block);
						this.addPendingOutgoingToAll(new NodeSignalNewBlock(this.getId(), block.toString()), networkTick);
						this.network.log.special(this.getId(), "BLOCK_CREATION", "Will create a new block: Invalid Hash chain", networkTick);
						break;
					case 2:
						block = this.createInvalidCreator(block);
						this.addPendingOutgoingToAll(new NodeSignalNewBlock(this.getId(), block.toString()), networkTick);
						this.network.log.special(this.getId(), "BLOCK_CREATION", "Will create a new block: Invalid creator", networkTick);
						break;
					case 3:
						block = this.createInvalidHeight(block);
						this.addPendingOutgoingToAll(new NodeSignalNewBlock(this.getId(), block.toString()), networkTick);
						this.network.log.special(this.getId(), "BLOCK_CREATION", "Will create a new block: Invalid height", networkTick);
						break;
					case 4:
						block = this.createFarFutureBlock(block);
						this.addPendingOutgoingToAll(new NodeSignalNewBlock(this.getId(), block.toString()), networkTick);
						this.network.log.special(this.getId(), "BLOCK_CREATION", "Will create a new block: Invalid height -> too far in the future", networkTick);
						break;
				}
			}
			
			return block != null ? this.network.nodeThreadDelayNewBlock : this.network.nodeThreadDelay;
		}
		
		return this.network.nodeThreadDelay;
	}

	private Block createFarFutureBlock(Block block) {
		block.setHeight(block.getHeight()+50);
		
		return block;
	}

	private Block createInvalidHeight(Block block) {
		block.setHeight(block.getHeight()-10);
		
		return block;
	}

	private Block createInvalidCreator(Block block) {
		block.addCreator("invalid_creator");
		return block;
	}

	private Block createInvalidHashChain(Block block) {	
		String bad_hash = "bad hash";
		
		try {
			MessageDigest hash = MessageDigest.getInstance("SHA-256");
			
			String prev = this.internalChain.getCurrentChunkHash();
			hash.update(prev.getBytes("UTF-8"));
			
			bad_hash = String.format("%064x", new BigInteger(1, hash.digest()));
		} catch (NoSuchAlgorithmException | UnsupportedEncodingException e) {}
		
		block.addPreviousChunk(bad_hash);
		
		return block;
	}

	private Block createValidBlock() {
		if(this.accessChain.getNextCreator().equals(this.getId()) && this.transactionPool.size() > 0) {
			Block block = new Block();
			block.addCreator(this.getId());
			
			ArrayList<String> keys = new ArrayList<String>(this.transactionPool.keySet());
			
			if(keys.size() > 0) {
				for(String trans: keys) {
					block.addPayload(this.transactionPool.get(trans).toString());
				}
				
				block.addPrevious(this.internalChain.getCurrentHash());
				
				if(lastChunkHeight + this.network.blockChunkThreshold < this.internalChain.getHeight()) {
					this.internalChain.addChunk();
				}
				
				block.addPreviousChunk(this.internalChain.getCurrentChunkHash());
				block.setHeight(this.internalChain.getHeight()+1);
			}
			
			return block;
		} else {
			return null;
		}
	}
}