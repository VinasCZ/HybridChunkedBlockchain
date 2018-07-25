package simulatedHybridBlockchain;

public final class NodeSignalResponseBlock extends NodeSignal {
	public NodeSignalResponseBlock(String origin, String payload) {
		super(origin, payload);
		this.type = NodeSignal.BLOCK_RESPONSE_SIGNAL;
	}
}
