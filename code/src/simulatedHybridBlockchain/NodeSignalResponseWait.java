package simulatedHybridBlockchain;

public final class NodeSignalResponseWait extends NodeSignal {
	public NodeSignalResponseWait(String origin, String payload) {
		super(origin, payload);
		this.type = NodeSignal.REQUEST_WAIT_SIGNAL;
	}
}
