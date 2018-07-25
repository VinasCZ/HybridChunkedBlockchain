package simulatedHybridBlockchain;

public final class NodeSignalNewBlock extends NodeSignal {
	public NodeSignalNewBlock(String origin, String payload) {
		super(origin, payload);
		this.type = NodeSignal.NEW_BLOCK_SIGNAL;
	}
}
