package simulatedHybridBlockchain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class OutgoingNodeSignal {
	
	private NodeSignal signal;
	private HashMap<String, Integer> sentAt = new HashMap<>();
	private HashSet<String> connections = new HashSet<>();
	private HashMap<String, Integer> sentCounter = new HashMap<>();
	private SimulatedNode parentNode;
	private String sender;
	
	public OutgoingNodeSignal(NodeSignal signal, String origForward, String sender, Set<String> connections, SimulatedNode parentNode) {
		this.connections.addAll(connections);
		
		// will not report back to the forwarder, nor will report to itself or the original sender
		this.connections.remove(signal.getOrigin());
		this.connections.remove(sender);
		this.connections.remove(origForward);
		
		this.signal = signal;
		
		this.parentNode = parentNode;
		this.sender = sender;
	}
	
	public NodeSignal getSignal() {
		return this.signal;
	}
	
	public ArrayList<String> shouldBeSentAgain(int currentTick) {
		ArrayList<String> sendAgain = new ArrayList<>(); 
		
		for(String node: this.connections) {
			if(this.sentAt.getOrDefault(node, 0) + this.parentNode.getConnectionDelay(node)*2.5 < currentTick || !this.sentAt.containsKey(node)) {
				sendAgain.add(node);
				this.sentAt.put(node, currentTick);
				this.sentCounter.put(node, this.sentCounter.getOrDefault(node, 0) + 1);
			}
		}
		
		return sendAgain;
	}
	
	// ACK, REJECT, or response with data received
	public void markNodeFinalResponseReceived(String node_id) {
		this.connections.remove(node_id);
		this.sentAt.remove(node_id);
		this.sentCounter.remove(node_id);
	}
	
	// WAIT signal received, just mark current 
	public void markNodePingResponseReceived(String node_id, int currentTick) {
		this.sentAt.put(node_id, currentTick);
	}
	
	public int getWaitingForResponse() {
		return this.connections.size();
	}
	
	public HashMap<String, Integer> getStalled() {
		return this.sentCounter;
	}
	
	public void removeStalledConnections(int limit) {
		for(String node: this.sentCounter.keySet()) {
			if(this.sentCounter.get(node) > limit) {
				this.connections.remove(node);
			}
		}
	}
}
