package simulatedHybridBlockchain;

public class NodeSignalAck extends NodeSignal {

	public NodeSignalAck(String origin, NodeSignal response_to) {
		super(origin, response_to.getHash());
		this.type = NodeSignal.ACK_SIGNAL;
	}
}
