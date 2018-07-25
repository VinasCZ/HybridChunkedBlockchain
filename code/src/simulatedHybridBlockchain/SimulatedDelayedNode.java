package simulatedHybridBlockchain;

import java.util.ArrayList;

public class SimulatedDelayedNode extends SimulatedNode {

	private int signalDelay = 100;
	
	public SimulatedDelayedNode(SimulatedNetwork network, String id) {
		super(network, id);
	}
	
	public SimulatedDelayedNode() {
		
	}

	@Override
	protected int executeSpecificLogic(int networkTick) {
		return 0;
	}
	
	@Override
	public ArrayList<SimulatedJob> getPendingOutgoingConnections(){
		for(SimulatedJob job: this.pendingOutgoing) {
			job.delay += this.signalDelay;
		}
		
		return this.pendingOutgoing;
	}
}
