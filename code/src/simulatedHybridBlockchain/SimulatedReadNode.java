package simulatedHybridBlockchain;

public class SimulatedReadNode extends SimulatedNode {

	public SimulatedReadNode(SimulatedNetwork network, String id) {
		super(network, id);
	}
	
	public SimulatedReadNode() {
		
	}

	@Override
	protected int executeSpecificLogic(int networkTick) {
		return 0;
	}

}
