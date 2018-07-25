package simulatedHybridBlockchain;

public class SimulatedTransactionNode extends SimulatedNode {

	private int lastBatch;
	private int ticksPerTransaction = 200;
	
	public SimulatedTransactionNode(SimulatedNetwork network, String id) {
		super(network, id);
		
		this.lastBatch = network.nodes.size();
	}

	public SimulatedTransactionNode() {}
	
	public void setTicksPerTransaction(int ticks) {
		this.ticksPerTransaction = ticks;
	}
	
	@Override
	protected int executeSpecificLogic(int networkTick) {
		if(this.activeOutgoingConnections.size() > 0) {
			for(; this.lastBatch <= networkTick; this.lastBatch += this.ticksPerTransaction) {
				DummyTransaction trans = new DummyTransaction(this.getId(), networkTick);

				NodeSignal signal = new NodeSignalNewTransaction(this.getId(), trans.toString());
				
				this.parseSignal(signal, networkTick);
			}
		}
		
		return this.network.nodeThreadDelay;
	}
}