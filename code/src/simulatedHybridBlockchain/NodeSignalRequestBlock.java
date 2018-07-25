package simulatedHybridBlockchain;

public final class NodeSignalRequestBlock extends NodeSignal {
	public NodeSignalRequestBlock(String origin, String payload) {
		super(origin, payload);
		this.type = NodeSignal.BLOCK_REQUEST_SIGNAL;
	}
}
