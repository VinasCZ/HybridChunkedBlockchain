package simulatedHybridBlockchain;

public class NodeSignalActive extends NodeSignal {

	public NodeSignalActive(String origin) {
		super(origin, null);
		this.type = NodeSignal.ACTIVE_SIGNAL;
	}
}
