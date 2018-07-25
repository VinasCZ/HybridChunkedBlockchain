package simulatedHybridBlockchain;

public final class NodeSignalNewTransaction extends NodeSignal {
	public NodeSignalNewTransaction(String origin, String payload) {
		super(origin, payload);
		this.type = NodeSignal.NEW_TRANSACTION_SIGNAL;
	}
}
