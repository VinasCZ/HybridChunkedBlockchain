package simulatedHybridBlockchain;

import java.util.ArrayList;

public class SimulatedBlockWriteNode extends SimulatedNode {

	private int lastBatch;
	
	private int lastChunkHeight = 0;
	private int minTransactions = 0;
	
	public SimulatedBlockWriteNode(SimulatedNetwork network, String id) {
		super(network, id);
		
		this.lastBatch = network.nodes.size();
	}
	
	public SimulatedBlockWriteNode() {}

	@Override
	protected int executeSpecificLogic(int networkTick) {
		if(this.activeOutgoingConnections.size() > 0) {
			// only create a block when prompted by access chain
			if(this.accessChain.getNextCreator().equals(this.getId()) && this.transactionPool.size() > minTransactions) {
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
					
					NodeSignal signal = new NodeSignalNewBlock(this.getId(), block.toString());
					
					this.parseSignal(signal, networkTick);
				}
				
				return this.network.nodeThreadDelayNewBlock;
			}
		}
		
		return this.network.nodeThreadDelay;
	}
	
	public void setMinTransactions(int min) {
		this.minTransactions = min;
	}
}
