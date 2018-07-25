package simulatedHybridBlockchain;

public class SimulatedSalesTransactionNode extends SimulatedNode {

	private int lastBatch;
	private double chance = 0.1;
	private String style = "xml";
	
	public SimulatedSalesTransactionNode(SimulatedNetwork network, String id) {
		super(network, id);
		
		this.lastBatch = network.nodes.size();
	}

	public SimulatedSalesTransactionNode() {}
	
	public void setChangeForTransaction(int total, int desired) {
		this.chance = ((double)desired / (double)total) / 1000.0;
	}
	
	@Override
	protected int executeSpecificLogic(int networkTick) {
		if(this.activeOutgoingConnections.size() > 0) {
			if(this.network.rng.nextDouble() <= this.chance) {
				SalesTransaction trans = new SalesTransaction(this.getId(), networkTick);
			
				switch(this.style) {
					case "json":
						trans.toJson();
						break;
					case "xml":
					default:
						trans.toXml();
				}
				
				NodeSignal signal = new NodeSignalNewTransaction(this.getId(), trans.toString());
				
				this.parseSignal(signal, networkTick);
			}
		}
		
		return this.network.nodeThreadDelay;
	}
	
	public void setStyle(String style) {
		this.style = style;
	}
}