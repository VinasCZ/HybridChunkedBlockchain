package simulatedHybridBlockchain;

public final class NodeSignalNewAuthBlock extends NodeSignal {
	public NodeSignalNewAuthBlock(String origin, String payload) {
		super(origin, payload);
		this.type = NodeSignal.NEW_AUTH_BLOCK_SIGNAL;
	}
}
