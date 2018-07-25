package simulatedHybridBlockchain;

public final class NodeSignalResponseDeny extends NodeSignal {
	public NodeSignalResponseDeny(String origin, String payload) {
		super(origin, payload);
		this.type = NodeSignal.REQUEST_DENY_SIGNAL;
	}
}
