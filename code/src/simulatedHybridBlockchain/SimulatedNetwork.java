package simulatedHybridBlockchain;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Random;

public class SimulatedNetwork {
	
	protected int currentTick = 0;
	
	// defines the minimum amount of milliseconds a tick should take
	// basically controls the minimum speed of the simulation
	protected int tickSpeed = 1;
	
	// delay of incoming treads for blocking operations
	protected int nodeThreadDelay = 1;
	protected int nodeThreadDelayNewTransaction = 2;
	protected int nodeThreadDelayNewBlock = 50;
	
	// delay of main thread when no signals are incoming
	protected int nodeEmptySignalsDelay = 1;
	
	protected int activeSignalRepetition = 1000;
	protected int debugDumpRepetition = 2000;
	protected int connectionCheckDelay = 500;
	protected int defaultConnectionCost = 20;
	protected int minConnectionCost = 10;
	protected int maxConnectionCost = 100;
	
	protected int maxNodeUploads = 10;
	protected int maxNodeDownloads = 10;
	
	protected int blockChunkThreshold = 5;
	
	protected Random rng;
	
	protected LinkedHashMap<String, SimulatedNode> nodes = new LinkedHashMap<>();
	protected HashMap<String, Integer> nodeConnectionCosts = new HashMap<>();;
	
	// specifies when a particular node should be executed again
	protected HashMap<Integer, ArrayList<String>> nextNodeExecTimes = new HashMap<>();;
	
	// stores pending signals grouped by time when they should be executed
	protected HashMap<Integer, ArrayList<SimulatedJob>> pendingSignals = new HashMap<>();;
	
	protected HashMap<String, Integer> initialAuths = new HashMap<>();
	
	protected Logger log;
	
	public SimulatedNetwork() {	
		this.rng = new Random(123456789);
		this.log = new Logger();
	}

	public int getEmptyNodeSignalsDelay() {
		return this.nodeEmptySignalsDelay;
	}
	
	public int getTickSpeed() {
		return this.tickSpeed;
	}
	
	public void setTickSpeed(int newTick) {
		if(newTick > 0) {
			this.tickSpeed = newTick;
		}
	}
	
	public int getCurrentTick() {
		return this.currentTick;
	}
	
	public void addNode(SimulatedNode node) {
		this.addNode(node, 0);
	}
	
	public void addNode(SimulatedNode node, int delay) {
		this.nodes.put(node.getId(), node);
		
		ArrayList<String> currentIdList = new ArrayList<>();
		
		if(this.nextNodeExecTimes.containsKey(delay)) {
			currentIdList = this.nextNodeExecTimes.get(delay);
		}
		
		currentIdList.add(node.getId());
		
		this.nextNodeExecTimes.put(delay, currentIdList);
		
		// add new connection with cost between min and max cost, with seeded RNG
		this.nodeConnectionCosts.put(node.getId(), (this.rng.nextInt(this.maxConnectionCost) + this.minConnectionCost));
	}
	
	public void simulate() {		
		// execute pending signals, sorted by exec_time
		ArrayList<Integer> delayTimes = new ArrayList<>(this.pendingSignals.keySet());
		Collections.sort(delayTimes);
		
		Iterator<Integer> delayIterator = delayTimes.iterator();
		while(delayIterator.hasNext()) {
			int key = delayIterator.next();
			
			if(key <= this.currentTick) {
				ArrayList<SimulatedJob> jobs = this.pendingSignals.get(key);
				for(SimulatedJob job: jobs) {
					String target = job.getTarget();
					this.nodes.get(target).executeIncomingConnection(job, this.currentTick);
				}
				
				this.pendingSignals.remove(key);
			} else {
				break; // only future signals from here on
			}
		}
		
		// go through know exec times for nodes
		// if exec_time <= current_time, invoke main thread and add returned delay
		// add pending outgoing jobs to list
		// find the nearest exec_time, so that we can jump ahead
		
		ArrayList<Integer> execTimes = new ArrayList<>(this.nextNodeExecTimes.keySet());
		Collections.sort(execTimes);
		
		//this.print("pending keys: "+this.nextNodeExecTimes.keySet().size());
		
		int nextDelay = -1;
		
		Iterator<Integer> execIterator = execTimes.iterator();
		while(execIterator.hasNext()) {
			int key = execIterator.next();
			
			if(key <= this.currentTick) {
				//this.print("Executing tick jobs at "+key);
				ArrayList<String> nodesToExec = this.nextNodeExecTimes.get(key);
				
				for(String nodeId: nodesToExec) {
					// execute each node
					// add back into queue based on current_tick + returned delay
					
					SimulatedNode node = this.nodes.get(nodeId);
					
					int delay = node.executeMainThread(this.currentTick) + this.currentTick;
					
					ArrayList<SimulatedJob> pending = node.getPendingOutgoingConnections();
					for(SimulatedJob job: pending) {
						ArrayList<SimulatedJob> currentJobList = new ArrayList<>();
						
						if(this.pendingSignals.containsKey(job.getDelay())) {
							currentJobList = this.pendingSignals.get(job.getDelay());
						}
						
						currentJobList.add(job);
						
						this.pendingSignals.put(job.getDelay(), currentJobList);
					}
					
					node.clearPendingOutgoingConnections();
					
					ArrayList<String> newExecTimes = new ArrayList<>();
					
					if(this.nextNodeExecTimes.containsKey(delay)) {
						newExecTimes = this.nextNodeExecTimes.get(delay);
					}
					
					newExecTimes.add(nodeId);
					this.nextNodeExecTimes.put(delay, newExecTimes);
					
					if(nextDelay == -1 || nextDelay > delay) {
						nextDelay = delay;
					}
				}
				
				this.nextNodeExecTimes.remove(key);
			} else {
				break; // only future node invocations from here on
			}
		}
		
		if(nextDelay != -1) {
			this.currentTick = nextDelay;
			//this.print("Skipping to nearest executable tick.");
		} else {
			this.currentTick++;
		}
		
		
		try {
			Thread.sleep(this.getTickSpeed());
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Establishes UPLOAD from caller, target receives a new DOWNLOAD connection
	 * i.e. caller wants to upload somewhere
	 * @param caller
	 * @return
	 */
	public String establishNewUploadConnection(String caller) {
		List<String> keys = new ArrayList<String>(this.nodes.keySet());
		Collections.shuffle(keys, this.rng); // shuffle using seeded RNG
		for(String target: keys) {
			if(!target.equals(caller)) {
				if(this.nodes.get(target).establishDownloadConnection(caller, this.getConnectionDelay(caller, target))) {
					return target;
				}
			}
		}
		
		return null;
	}
	
	public boolean closeUploadConnection(String caller, String target) {
		return this.nodes.get(target).closeDownloadFrom(caller);
	}
	
	/**
	 * Establishes DOWNLOAD from caller, target receives a new UPLOAD connection
	 * i.e. caller wants to download from somewhere
	 * @param caller
	 * @param set 
	 * @return
	 */
	public String establishNewDownloadConnection(String caller) {
		List<String> keys = new ArrayList<String>(this.nodes.keySet());
		Collections.shuffle(keys, this.rng); // shuffle using seeded RNG
		for(String target: keys) {
			if(!target.equals(caller)) {
				if(this.nodes.get(target).establishUploadConnection(caller, this.getConnectionDelay(caller, target))) {
					return target;
				}
			}
		}
		
		return null;
	}
	
	public boolean closeDownloadConnection(String caller, String target) {
		return this.nodes.get(target).closeUploadTo(caller);
	}
	
	/**
	 * Gets connection cost to a particular node, or returns the default connection cost
	 * @param node
	 * @return int delay in ticks
	 */
	public int getConnectionDelay(String node) {
		return this.nodeConnectionCosts.getOrDefault(node, this.defaultConnectionCost);
	}
	
	/**
	 * Shorthand for connection between two nodes.
	 * Takes both connection costs to node1 and node2 and returns a mean average.
	 * @param node1, id of node
	 * @param node2, id of node
	 * @return int delay in ticks
	 */
	public int getConnectionDelay(String node1, String node2) {
		return (this.getConnectionDelay(node1)+this.getConnectionDelay(node2))/2;
	}

	public void addInitialAuth(String string, int writeNode) {
		this.initialAuths.put(string, writeNode);
	}
	
	// BELOW: Graph data methods
	
	public double[][] getWaitingTransactions() {
		double[] xData = new double[this.nodes.size()];
		double[] yData = new double[this.nodes.size()];
		
		int id = 0;
		
		for(SimulatedNode node: this.nodes.values()) {
			double node_id = id++;
			double node_count = node.transactionPool.size();
			xData[id-1] = node_id;
			yData[id-1] = node_count;
		}
		return new double[][] { xData, yData };
	}
	
	public double getWaitingTransactions(String node_id) {
		return this.nodes.get(node_id).transactionPool.size();
	}

	public double[][] getWaitingForwardTransactions() {
		double[] xData = new double[this.nodes.size()];
		double[] yData = new double[this.nodes.size()];
		
		int id = 0;
		
		for(SimulatedNode node: this.nodes.values()) {
			double node_id = id++;
			double node_count = node.forwardSignals.size();
			xData[id-1] = node_id;
			yData[id-1] = node_count;
		}
		return new double[][] { xData, yData };
	}
	
	public double getWaitingForwardTransactions(String node_id) {
		return this.nodes.get(node_id).forwardSignals.size();
	}
	
	public double[][] getActiveUploads() {
		double[] xData = new double[this.nodes.size()];
		double[] yData = new double[this.nodes.size()];
		
		int id = 0;
		
		for(SimulatedNode node: this.nodes.values()) {
			double node_id = id++;
			double node_count = node.activeOutgoingConnections.size();
			xData[id-1] = node_id;
			yData[id-1] = node_count;
		}
		return new double[][] { xData, yData };
	}
	
	public double getActiveUploads(String node_id) {
		return this.nodes.get(node_id).activeOutgoingConnections.size();
	}
	
	public double[][] getActiveDownloads() {
		double[] xData = new double[this.nodes.size()];
		double[] yData = new double[this.nodes.size()];
		
		int id = 0;
		
		for(SimulatedNode node: this.nodes.values()) {
			double node_id = id++;
			double node_count = node.activeIncomingConnections.size();
			xData[id-1] = node_id;
			yData[id-1] = node_count;
		}
		return new double[][] { xData, yData };
	}
	
	public double getActiveDownloads(String node_id) {
		return this.nodes.get(node_id).activeIncomingConnections.size();
	}
	
	public double[][] getSignalsReceived(int type) {
		double[] xData = new double[this.nodes.size()];
		double[] yData = new double[this.nodes.size()];
		
		int id = 0;
		
		for(SimulatedNode node: this.nodes.values()) {
			double node_id = id++;
			double node_count = node.signalsReceived.get(type);
			xData[id-1] = node_id;
			yData[id-1] = node_count;
		}
		return new double[][] { xData, yData };
	}
	
	public double getSignalsReceived(String node_id, int type) {
		return this.nodes.get(node_id).signalsReceived.get(type);
	}
	
	public double[][] getSignalsSent(int type) {
		double[] xData = new double[this.nodes.size()];
		double[] yData = new double[this.nodes.size()];
		
		int id = 0;
		
		for(SimulatedNode node: this.nodes.values()) {
			double node_id = id++;
			double node_count = node.signalsSent.get(type);
			xData[id-1] = node_id;
			yData[id-1] = node_count;
		}
		return new double[][] { xData, yData };
	}
	
	public double getSignalsSent(String node_id, int type) {
		return this.nodes.get(node_id).signalsSent.get(type);
	}
	
	public double[][] getWaitingSignals() {
		double[] xData = new double[this.nodes.size()];
		double[] yData = new double[this.nodes.size()];
		
		int id = 0;
		
		for(SimulatedNode node: this.nodes.values()) {
			double node_id = id++;
			double node_count = node.waitingSignals.size();
			xData[id-1] = node_id;
			yData[id-1] = node_count;
		}
		return new double[][] { xData, yData };
	}
	
	public double getWaitingSignals(String node_id) {
		return this.nodes.get(node_id).waitingSignals.size();
	}
	
	public double[][] getBlockHeight() {
		double[] xData = new double[this.nodes.size()];
		double[] yData = new double[this.nodes.size()];
		
		int id = 0;
		
		for(SimulatedNode node: this.nodes.values()) {
			double node_id = id++;
			double node_count = node.internalChain.height;
			xData[id-1] = node_id;
			yData[id-1] = node_count;
		}
		return new double[][] { xData, yData };
	}
	
	public double getBlockHeight(String node_id) {
		return this.nodes.get(node_id).internalChain.height;
	}
	
	public double[][] getUploadUtilization() {
		double[] xData = new double[this.nodes.size()];
		double[] yData = new double[this.nodes.size()];
		
		int id = 0;
		
		for(SimulatedNode node: this.nodes.values()) {
			double node_id = id++;
			double node_count = node.previousUploadSpeed;
			xData[id-1] = node_id;
			yData[id-1] = node_count;
		}
		return new double[][] { xData, yData };
	}
	
	public double getUploadUtilization(String node_id) {
		return this.nodes.get(node_id).previousUploadSpeed;
	}
	
	public double[][] getDownloadUtilization() {
		double[] xData = new double[this.nodes.size()];
		double[] yData = new double[this.nodes.size()];
		
		int id = 0;
		
		for(SimulatedNode node: this.nodes.values()) {
			double node_id = id++;
			double node_count = node.previousDownloadSpeed;
			xData[id-1] = node_id;
			yData[id-1] = node_count;
		}
		return new double[][] { xData, yData };
	}
	
	public double getDownloadUtilization(String node_id) {
		/*SimulatedNode sn = this.nodes.get(node_id);
		try {
			return sn.receivedSinceLastUpdate/((sn.lastConnectionCheck - this.currentTick) + this.connectionCheckDelay);
		} catch(ArithmeticException e) {
			return 0;
		}*/

		return this.nodes.get(node_id).previousDownloadSpeed;
	}

	public double[][] getVerifiedTransactions() {
		double[] xData = new double[this.nodes.size()];
		double[] yData = new double[this.nodes.size()];
		
		int id = 0;
		
		for(SimulatedNode node: this.nodes.values()) {
			double node_id = id++;
			double node_count = node.acceptedTransactions;
			xData[id-1] = node_id;
			yData[id-1] = node_count;
		}
		return new double[][] { xData, yData };
	}
	
	public double getVerifiedTransactions(String node_id) {
		return this.nodes.get(node_id).acceptedTransactions;
	}

	public ArrayList<Integer> getFinalVerifiedTransactions() {
		ArrayList<Integer> data = new ArrayList<>();
		
		for(String node_id: this.nodes.keySet()) {
			data.add(this.nodes.get(node_id).acceptedTransactions);
		}
		
		return data;
	}

	public ArrayList<Integer> getFinalBlocksAccepted() {
		ArrayList<Integer> data = new ArrayList<>();
		
		for(String node_id: this.nodes.keySet()) {
			data.add(this.nodes.get(node_id).internalChain.height);
		}
		
		return data;
	}

	public ArrayList<Integer> getFinalBlockTransactionCounts() {
		ArrayList<Integer> data = new ArrayList<>();
		
		String longest = null;
		int height = -1;
		
		for(String node_id: this.nodes.keySet()) {
			SimulatedNode node = this.nodes.get(node_id); 
			
			if(node.internalChain.height > height) {
				longest = node_id;
				height = node.internalChain.height;
			}
		}
		
		if(longest != null) {
			for(Block block: this.nodes.get(longest).internalChain.blocks.values()) {
				String[] trans = block.getPayload().split("\\|\\|");
				data.add(trans.length);
			}
		}
		
		return data;
	}

	public ArrayList<Integer> getFinalBlockSizes() {
		ArrayList<Integer> data = new ArrayList<>();
		
		String longest = null;
		int height = -1;
		
		for(String node_id: this.nodes.keySet()) {
			SimulatedNode node = this.nodes.get(node_id); 
			
			if(node.internalChain.height > height) {
				longest = node_id;
				height = node.internalChain.height;
			}
		}
		
		if(longest != null) {
			for(Block block: this.nodes.get(longest).internalChain.blocks.values()) {
				try {
					data.add(block.toString().getBytes("UTF-8").length);
				} catch(UnsupportedEncodingException ex) {}
			}
		}
		
		return data;
	}
}




