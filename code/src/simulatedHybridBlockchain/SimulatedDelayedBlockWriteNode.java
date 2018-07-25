package simulatedHybridBlockchain;

import java.util.ArrayList;
import java.util.Iterator;

public class SimulatedDelayedBlockWriteNode extends SimulatedBlockWriteNode {
	
	public SimulatedDelayedBlockWriteNode(SimulatedNetwork network, String id) {
		super(network, id);
	}
	
	public SimulatedDelayedBlockWriteNode() {
		super();
	}
	
	@Override
	public ArrayList<SimulatedJob> getPendingOutgoingConnections(){
		for(int a = 0; a < this.pendingOutgoing.size(); a++) {
			NodeSignal sig = this.pendingOutgoing.get(a).getSignal();
			
			if(sig.getType() == NodeSignal.NEW_BLOCK_SIGNAL && sig.getOrigin() != this.getId()) {
				this.pendingOutgoing.remove(a);
			}
		}
		
		ArrayList<String> sigList = new ArrayList<>(this.forwardSignals.keySet());
		Iterator<String> sigIter = sigList.iterator();
		
		while(sigIter.hasNext()) {
			String key = sigIter.next();
			
			if(this.forwardSignals.get(key).getSignal().getType() == NodeSignal.NEW_BLOCK_SIGNAL) {
				this.forwardSignals.remove(key);
			}
		}
		
		return this.pendingOutgoing;
	}
}
