package simulatedHybridBlockchain;

import java.util.ArrayList;

public class SimulatedDeafNode extends SimulatedNode {

	public SimulatedDeafNode(SimulatedNetwork network, String id) {
		super(network, id);
	}
	
	public SimulatedDeafNode() {
		
	}

	@Override
	protected int executeSpecificLogic(int networkTick) {
		return 0;
	}

	@Override
	public ArrayList<SimulatedJob> getPendingOutgoingConnections(){
		return new ArrayList<SimulatedJob>();
	}
}
