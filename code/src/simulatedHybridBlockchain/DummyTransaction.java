package simulatedHybridBlockchain;

public class DummyTransaction extends Transaction {

	private Integer networkTick;
	
	public DummyTransaction(String origin, Integer networkTick) {
		super(origin, null);
		
		this.networkTick = networkTick;
		
		this.generateData();
	}
	
	private void generateData() {
		// predicable data
		this.payload = (this.origin.hashCode()/(this.networkTick == 0 ? 1 : this.networkTick))+"";
	}
}
