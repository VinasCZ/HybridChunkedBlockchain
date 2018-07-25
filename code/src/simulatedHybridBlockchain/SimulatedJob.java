package simulatedHybridBlockchain;

public class SimulatedJob {
	protected NodeSignal signal;
	protected String target;
	protected int delay;
	
	public SimulatedJob(NodeSignal signal, String target, int delay) {
		this.signal = signal;
		this.target = target;
		this.delay = delay;
	}
	
	public NodeSignal getSignal() {
		return this.signal;
	}
	
	public String getTarget() {
		return this.target;
	}
	
	public int getDelay() {
		return this.delay;
	}
}
