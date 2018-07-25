package simulatedHybridBlockchain;

import java.io.UnsupportedEncodingException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Defines a basic simulated node that performs "concurrent jobs"
 * It has n incoming connections, m outgoing connections, and a main thread for logic execution
 */
public abstract class SimulatedNode {
	private 	String uuid;
	protected 	String currentBlock;
	protected 	SimulatedNetwork network;
	protected 	HashMap<String, Transaction> transactionPool = new HashMap<>();
	protected 	Blockchain 		internalChain = new Blockchain(); 		// access-protected internal data blockchain
	protected 	AuthBlockchain 	accessChain = new AuthBlockchain(); 	// public blockchain with access rights to the internal blockchain
	
	// default settings, can be changed
	private int speedUpload = 1000; 
	private int speedDownload = 1000;
	private int maxIncoming = 2;
	private int maxOutgoing = 2;
	private int limitIncoming = 10;
	private int limitOutgoing = 10;
	private int limitIncomingThreads = 10;
	private int maxMainThreads = 5;
	private int signalRepetitionThreshold = 5;
	
	private int connectionStale = 2000;
	private int connectionCooldown = 10000;
	private int blockTimeout = 2000;
	private int forgerTryout = 5;
	
	private int transactionsBlockHeightCache = 5;
	
	protected int receivedSinceLastUpdate = 0;
	protected int sentSinceLastUpdate = 0;
	protected int acceptedTransactions = 0;
	protected int forgerCounter = 0;
	
	protected int lastConnectionCheck = 0;
	protected int previousUploadSpeed = 0;
	protected int previousDownloadSpeed = 0;
	
	private HashMap<Integer, Integer> incomingThreadAvailabilities = new HashMap<>();
	
	private int lastActiveSignal = 0;
	
	protected ArrayList<Integer> signalsReceived = new ArrayList<>();
	protected ArrayList<Integer> signalsSent = new ArrayList<>();
	
	protected ArrayDeque<SimulatedJob> 	waitingSignals = new ArrayDeque<>();
	protected ArrayList<SimulatedJob> 	pendingOutgoing = new ArrayList<>();
	
	private HashMap<String, Block> waitingBlocks = new HashMap<>();
	
	protected HashMap<String, OutgoingNodeSignal> forwardSignals = new HashMap<>();
	protected HashMap<Integer, HashSet<String>> clearForwardTransactionsAt = new HashMap<>();

	// keeps track of neighbour connections, ie. when they last reported ACTIVE signal
	// also includes nodes with WRITE access (ie. those creating blocks)
	protected HashMap<String, Integer> lastActiveSignalReceived = new HashMap<>();
	private HashMap<String, Integer> connectionCooldownAt = new HashMap<>();
	
	// ACTIVE signal delays, used to estimate if nodes are up or down
	private HashMap<String, Integer> lastActiveSignalDelay = new HashMap<>();
	
	// timeout for ACTIVE signals forwarding
	private HashMap<String, Integer> nextActiveSignalForward = new HashMap<>();
	
	// counter for suspicious blocks at current height, ie. submitted by correct creator, but not correct hashes
	private HashMap<String, Integer> suspiciousBlockCounts = new HashMap<>(); 
	
	// keep track of connections along with their delays
	protected LinkedHashMap<String, Integer> activeIncomingConnections = new LinkedHashMap<>();
	protected LinkedHashMap<String, Integer> activeOutgoingConnections = new LinkedHashMap<>();
	
	public SimulatedNode() {
		// empty constructor for instantiation
	}
	
	public SimulatedNode(SimulatedNetwork network, String id) {		
		this.setId(id);
		this.addNetwork(network);
	}
	
	public void addNetwork(SimulatedNetwork network) {
		this.network = network;
		
		if(network.initialAuths.size() > 0) {
			this.accessChain.addInitial(network.initialAuths);
		}

		for(int a = 0; a <= 15; a++) {
			this.signalsReceived.add(a, 0);
		}
		
		for(int a = 0; a <= 15; a++) {
			this.signalsSent.add(a, 0);
		}
		
		for(int a = 0; a < this.maxIncoming; a++) {
			this.incomingThreadAvailabilities.put(a, 0);
		}
	}
	
	public void setId(String id) {
		this.uuid = id;
	}
	
	/**
	 * Executes the main thread, and returns a "delay" of execution + wait times
	 * Simulates concurrent behaviour for internal parsers, and executes additional internal logic
	 * If null is returned, node is considered to be "faulty"
	 * 
	 * @param int
	 * @return Integer
	 */
	public int executeMainThread(int networkTick) {
		int delay = 0;

		// try to run incoming connections again in case there are any waiting signals
		this.executeIncomingThreads(networkTick);
		
		// assume that specific logic delay is distributed between multiple threads
		delay += this.executeSpecificLogic(networkTick + delay)/this.maxMainThreads; 
		
		// add additional outgoing signals if needed
		this.performSignalForward(networkTick + delay); 
		
		if(this.waitingSignals.size() == 0) {
			delay += this.network.nodeEmptySignalsDelay;
		}
		
		// check if current block creator is over suspicion threshold
		/*if(this.suspiciousBlockCounts.getOrDefault(this.accessChain.getNextCreator(), 0) >= this.suspicionThreshold) {
			// assume node is very suspicious, shift forger
			this.suspiciousBlockCounts.put(this.accessChain.getNextCreator(), 0);
			this.accessChain.shiftForger();
			this.network.log.special(this.getId(), "BLOCK_ACCEPTANCE", "Shifting forger to "+this.accessChain.getNextCreator(), networkTick);
		}*/
		
		// check if current block has timeouts, only if there are transactions waiting
		if(this.activeIncomingConnections.size() > 0 && this.transactionPool.size() > 0) {
			if(this.internalChain.getLastReceived() + this.blockTimeout < this.network.currentTick) {
				this.addPendingOutgoingToAll(new NodeSignalRequestBlock(this.getId(), this.internalChain.getCurrentHash()), delay);
				this.internalChain.setLastReceived(networkTick);
				this.forgerCounter++;
				
				if(this.forgerCounter > this.forgerTryout) {
					this.suspiciousBlockCounts.put(this.accessChain.getNextCreator(), this.suspiciousBlockCounts.getOrDefault(this.accessChain.getNextCreator(), 0)+1);
					this.accessChain.shiftForger();
					this.forgerCounter = 0;
					this.network.log.special(this.getId(), "BLOCK_ACCEPTANCE", "Shifting forger", networkTick);
				}
			}
		}
		
		// go through waiting blocks and see if any can be used
		ArrayList<String> waitingList = new ArrayList<>(this.waitingBlocks.keySet());
		Iterator<String> waitingIter = waitingList.iterator();
		
		while(waitingIter.hasNext()) {
			String block_hash = waitingIter.next();

			if(this.waitingBlocks.get(block_hash).getHeight() < this.internalChain.height) {
				this.waitingBlocks.remove(block_hash);
				continue;
			}
			
			if(this.parseNewBlock(this.waitingBlocks.get(block_hash), this.waitingBlocks.get(block_hash).getCreator())) {
				NodeSignal sig = new NodeSignalNewBlock(this.internalChain.getCurrentBlock().creator, this.internalChain.getCurrentBlock().toString());
				sig.setForwardOrigin(this.getId());
				this.addPendingOutgoingToAll(sig, delay);
			};
		}
		
		// ping ACTIVE signal to all connections after a set time
		if(this.lastActiveSignal <= this.network.getCurrentTick()) {
			NodeSignalActive sig = new NodeSignalActive(this.getId());
			
			if(this.accessChain.hasPermission(this.getId(), AuthBlockchain.WRITE_NODE)) {
				// write nodes report their sent_at time
				sig.setPayload(delay+"");
			}
			
			this.createForwardSignalToAll(sig);
			
			//this.addPendingOutgoingToAll(, delay+networkTick, true);
			this.lastActiveSignal += this.network.activeSignalRepetition;
		}
		
		// clean up used forward signals
		ArrayList<String> sigList = new ArrayList<>(this.forwardSignals.keySet());
		Iterator<String> sigIter = sigList.iterator();
		
		while(sigIter.hasNext()) {
			String signal = sigIter.next();
			OutgoingNodeSignal sig = this.forwardSignals.get(signal);
			
			if(sig.getWaitingForResponse() == 0) {
				HashMap<String, Integer> stalled = sig.getStalled();
				
				if(stalled.size() > 0) {
					for(String node: new ArrayList<String>(stalled.keySet())) {
						this.connectionCooldownAt.put(node, networkTick + this.connectionCooldown);
						if(this.activeIncomingConnections.containsKey(node)) {
							this.closeDownloadFrom(node);
						} 
						if(this.activeOutgoingConnections.containsKey(node)) {
							this.closeUploadTo(node);
						}
					}
				}
				
				this.forwardSignals.remove(signal);
			}
		}

		
		// try to refresh connections after a set time
		if(lastConnectionCheck + this.network.connectionCheckDelay <= this.network.currentTick) {
			// cleanup expired cooldowns
			ArrayList<String> connectionCooldowns = new ArrayList<>(this.connectionCooldownAt.keySet());
			Iterator<String> connectionCooldownsIter = connectionCooldowns.iterator();
			
			while(connectionCooldownsIter.hasNext()) {
				String node_id = connectionCooldownsIter.next();
				if(this.connectionCooldownAt.get(node_id) <= networkTick) {
					this.connectionCooldownAt.remove(node_id);
				}
			}
			
			this.refreshUploadConnections();
			this.refreshDownloadConnections();
			
			this.lastConnectionCheck = this.network.currentTick;
		}
		
		// make sure to return at least 1 tick delay
		return (delay > 0 ? delay : 1);
	}
	
	private void executeIncomingThreads(int networkTick) {
		for(Integer thread_id: this.incomingThreadAvailabilities.keySet()) {
			if(this.incomingThreadAvailabilities.get(thread_id) <= networkTick) {
				int max_delay = 0;
				
				for(int a = 0; a < this.limitIncomingThreads; a++) {
					if(this.waitingSignals.size() > 0) {
						SimulatedJob job = this.waitingSignals.removeFirst();
						
						int delay = this.parseJob(job, networkTick);
						
						max_delay = (delay > max_delay ? delay : max_delay);
					} else {
						break;
					}
				}
				
				this.incomingThreadAvailabilities.put(thread_id, networkTick+max_delay);
			}
			
			if(this.waitingSignals.size() == 0) {
				return;
			}
		}
	}
	
	/**
	 * Forwards job into parseSignal
	 * @param job
	 * @param delay
	 * @return int, delay for thread
	 */
	private int parseJob(SimulatedJob job, int delay) {
		NodeSignal signal = job.getSignal();
		
		try {
			this.receivedSinceLastUpdate += signal.toString().getBytes("UTF-8").length;
		} catch(UnsupportedEncodingException ex) {}
		
		//this.print("received "+signal.getHash()+", ie.'"+signal.toString()+"' ");
		
		return this.parseSignal(signal, delay);
	}
	
	/**
	 * Runs signal through parser and saves decoded output to node.
	 * Returns specific delay for parsing action.
	 * 
	 * @param signal
	 * @param processedAt
	 * @return int, delay for thread
	 */
	protected int parseSignal(NodeSignal signal, int processedAt) {
		boolean isSentFromItself = this.getId().equals(signal.getForwardOrigin());
		
		if(!isSentFromItself) {
			// only send ACK to signals that require ACK
			if(signal.type != NodeSignal.ACK_SIGNAL && signal.type != NodeSignal.BLOCK_REQUEST_SIGNAL
				&& signal.type != NodeSignal.AUTH_BLOCK_REQUEST_SIGNAL && signal.type != NodeSignal.BLOCK_CHUNK_REQUEST_SIGNAL) {
				
				if(signal.getType() == NodeSignal.NEW_TRANSACTION_SIGNAL) {
					this.network.log.special(this.getId(), "ACK_TRANSACTION", "ACK for signal with hash: "+signal.getHash(), processedAt);
				}
				
				if(signal.getType() == NodeSignal.NEW_BLOCK_SIGNAL) {
					this.network.log.special(this.getId(), "ACK_BLOCK", "ACK for signal with hash: "+signal.getHash(), processedAt);
				}
				
				// Send an ACK signal to the sender, exclude ACKs to avoid endless confirmation loops
				this.addPendingOutgoing(signal.getForwardOrigin(), new NodeSignalAck(this.getId(), signal), processedAt);
			}
		}
		
		
		if(!isSentFromItself) {
			// increment signal counter
			this.signalsReceived.set(signal.getType(), this.signalsReceived.get(signal.getType())+1);
			
			this.network.log.signal(signal.getForwardOrigin(), this.getId(), signal, processedAt);
		}
		
		switch(signal.getType()) {
			case NodeSignal.ACK_SIGNAL:
				this.parseAckSignal(signal);
				
				return this.network.nodeThreadDelay;
			case NodeSignal.ACTIVE_SIGNAL:
				// make sure we are not rewriting by a delayed ACTIVE signal
				if(this.lastActiveSignalReceived.getOrDefault(signal.getOrigin(), 0) < processedAt && !signal.getOrigin().equals(this.getId())) {
					this.lastActiveSignalReceived.put(signal.getOrigin(), processedAt);
					
					// forward ACTIVE signal for any WRITE node
					if(this.accessChain.hasPermission(signal.getOrigin(), AuthBlockchain.WRITE_NODE)) {
						try {
							this.lastActiveSignalDelay.put(signal.getOrigin(), Integer.parseInt(signal.getPayload()));
						} catch(NumberFormatException e) {}
						
						// forward only if last ACTIVE signal was received around network-wide threshold
						// if signal was received first it was forwarded - this solution prevents forwarding loops
						if(this.nextActiveSignalForward.getOrDefault(signal.getOrigin(), 0) + this.network.activeSignalRepetition < processedAt) {
							NodeSignal sig = new NodeSignalActive(signal.getOrigin());
							sig.setPayload(this.lastActiveSignalDelay.get(signal.getOrigin())+"");
							
							this.createForwardSignal(sig);
							
							this.nextActiveSignalForward.put(signal.getOrigin(), processedAt);
						}
					}
					
					this.network.log.special(this.getId(), "NODE_KNOWLEDGE", this.lastActiveSignalReceived.toString(), processedAt);
				} else {
					this.print("Cannot update ACTIVE: "+this.lastActiveSignalReceived.getOrDefault(signal.getOrigin(), 0)+" "+signal.getOrigin());
				}
				
				return this.network.nodeThreadDelay;
				
			case NodeSignal.NEW_TRANSACTION_SIGNAL:
				Transaction trans = new Transaction(signal.getPayload());
				String hash = trans.getHash();
				
				if(this.accessChain.hasPermission(signal.getOrigin(), AuthBlockchain.PUBLISH_NODE)) {
					if(this.transactionWasNotReceivedBefore(signal.getHash(), hash)) {
						this.transactionPool.put(hash, trans);
						this.network.log.special(this.getId(), "TRANSACTION_POOL_LEN", "transaction pool: "+this.transactionPool.size(), processedAt);
						
						this.createForwardSignal(new NodeSignalNewTransaction(signal.getOrigin(), signal.getPayload()));
						
						int blockToClearOn = this.internalChain.getHeight()+this.transactionsBlockHeightCache;
						
						HashSet<String> sameBlockTransactions = this.clearForwardTransactionsAt.getOrDefault(blockToClearOn, new HashSet<>());
						sameBlockTransactions.add(hash);
						this.clearForwardTransactionsAt.put(blockToClearOn, sameBlockTransactions);
					}
				}
				
				return this.network.nodeThreadDelayNewTransaction;
				
			case NodeSignal.NEW_BLOCK_SIGNAL:
				if(this.parseNewBlockSignal(signal)) {
					this.createForwardSignal(new NodeSignalNewBlock(signal.getOrigin(), signal.getPayload()));
					this.network.log.special(this.getId(), "BLOCK_HEIGHT", "blockchain height: "+this.internalChain.height, processedAt);
					this.internalChain.setLastReceived(processedAt);
					return this.network.nodeThreadDelayNewBlock;
				}
				
				return this.network.nodeThreadDelay;
				
			case NodeSignal.NEW_AUTH_BLOCK_SIGNAL:
				if(this.parseNewAuthBlockSignal(signal)) {
					this.createForwardSignal(new NodeSignalNewAuthBlock(signal.getOrigin(), signal.getPayload()));
				}
				
				return this.network.nodeThreadDelay;
			
			// REQUEST signals
			case NodeSignal.BLOCK_REQUEST_SIGNAL:
				String caller = signal.getOrigin();
				String block_hash = signal.getPayload();
				
				// if has block
				// then send Response
				// else send Wait, send request to outgoing connections
				
				if(this.accessChain.hasPermission(caller, AuthBlockchain.READ_NODE)) {
					String prev = this.internalChain.findByPrevious(block_hash);
					
					if(prev == null) {
						// send DENY signal, could not find the block in whole blockchain
						this.addPendingOutgoing(caller, new NodeSignalResponseDeny(this.getId(), signal.getHash()), processedAt);
					} else if(prev.equals("")) {
						NodeSignal sig = new NodeSignalRequestBlock(signal.getOrigin(), block_hash);
						sig.setForwardOrigin(this.getId());
						
						this.createForwardSignal(sig);
						
						//this.addPendingOutgoingToAll(sig, processedAt);
						// Send WAIT signal to alert sender to stand-by
						this.addPendingOutgoing(caller, new NodeSignalResponseWait(this.getId(), signal.getHash()), processedAt);
					} else {
						// send block encoded in string
						this.addPendingOutgoing(caller, new NodeSignalResponseBlock(this.getId(), prev), processedAt);
					}
					
				} else {
					// send DENY signal if insufficient authorisation
					this.addPendingOutgoing(caller, new NodeSignalResponseDeny(this.getId(), signal.getHash()), processedAt);
				}
				
				return this.network.nodeThreadDelay;
				
			// RESPONSE signals
			case NodeSignal.BLOCK_RESPONSE_SIGNAL:
				if(signal.getPayload() != null) {
					Block new_block = new Block();
					new_block.decodeNewBlock(signal.getPayload());
					
					//this.print(signal.getOrigin()+" responded with block height "+new_block.getHeight());
					
					this.parseNewBlock(new_block, new_block.getCreator());
				}
				
				return this.network.nodeThreadDelay;
			
			case NodeSignal.REQUEST_DENY_SIGNAL:
				// assumes to be a reaction to a request
				OutgoingNodeSignal sigD = this.forwardSignals.getOrDefault(signal.getPayload(), null);
				
				if(sigD != null) {
					sigD.markNodeFinalResponseReceived(signal.getOrigin());
				}
				
				return this.network.nodeThreadDelay;
				
			case NodeSignal.REQUEST_WAIT_SIGNAL:
				// assumes to be a reaction to a request
				OutgoingNodeSignal sigW = this.forwardSignals.getOrDefault(signal.getPayload(), null);
				
				if(sigW != null) {
					sigW.markNodePingResponseReceived(signal.getOrigin(), processedAt);
				}
				
				return this.network.nodeThreadDelay;
				
			default:
				return this.network.nodeThreadDelay;
		}
	}

	protected boolean parseAckSignal(NodeSignal signal) {
		OutgoingNodeSignal sig = this.forwardSignals.getOrDefault(signal.getPayload(), null);
		
		// mark ACK as received, delete forward signal if all responded
		if(sig != null) {
			sig.markNodeFinalResponseReceived(signal.getOrigin());
			
			if(sig.getWaitingForResponse() == 0) {
				this.forwardSignals.remove(signal.getPayload());
			}
		}
		
		return true;
	}
	
	private boolean parseNewBlockSignal(NodeSignal signal) {
		Block new_block = new Block();
		new_block.decodeNewBlock(signal.getPayload());
		
		if(this.accessChain.hasPermission(signal.getOrigin(), AuthBlockchain.WRITE_NODE) && this.accessChain.hasPermission(new_block.getCreator(), AuthBlockchain.WRITE_NODE)) {	
			return this.parseNewBlock(new_block, new_block.getCreator());
		} else {
			// sending node does not have permissions to create blocks
			return false;
		}
	}
	
	private boolean parseNewBlock(Block new_block, String creator) {
		if(new_block.height <= this.internalChain.getHeight()) {
			return false;
		}
		
		boolean isChainIntact = new_block.getPrevious().equals(this.internalChain.getCurrentHash()) && this.internalChain.getHeight()+1 == new_block.getHeight();
		
		// this forger should be the next in line
		if(this.accessChain.isNextCreator(creator)) {
			if(isChainIntact) {
				// we can continue, this is a valid next block with correct creator
				
				// cleanup blocks that are a potential fork (ie. same previous_hash, but incorrect creator)
				this.cleanupForkBlocks(new_block);
				this.handleNewBlockParsing(new_block);
				
				this.network.log.special(this.getId(), "BLOCK_ACCEPTANCE", "Regular block from "+creator+", height "+new_block.height+", new chain height of "+this.internalChain.getHeight()+" "+new_block.toString(), this.network.getCurrentTick());
				
				return true;
			} else {
				this.waitingBlocks.put(new_block.getHash(), new_block);
				// REFUSE to accept and forward block, something is not correct
				return false;
			}
		} else {
			if(isChainIntact) {
				// this block seems to be next in line, but not with correct creator
				this.network.log.special(this.getId(), "BLOCK_ACCEPTANCE", "Incorrect block received from "+creator+", height "+new_block.height, this.network.getCurrentTick());
				this.waitingBlocks.put(new_block.getHash(), new_block);
				
				// do not forward, potentially suspicious block
				return false;
			} else {
				// we seem to be missing a block in between current and received
				// check if forger could have created this block in the future (e.g. 5 more blocks into the future)
				if(this.accessChain.isFutureBlock(creator) && !this.internalChain.suspiciousFuture(new_block.getHeight())) {
					// keep in memory for future use
					this.waitingBlocks.put(new_block.getHash(), new_block);
					
					this.network.log.special(this.getId(), "BLOCK_ACCEPTANCE", "Future block received from "+creator+", height "+new_block.height, this.network.getCurrentTick());
					
					// do not forward, potentially suspicious block
					return false;
				} else {
					this.waitingBlocks.put(new_block.getHash(), new_block);
					// block was received suspiciously far for the future; every WRITE should submit after receiving previous
					// REFUSE to accept and forward, highly suspicious block
					return false;
				}
			}
		}
	}
	
	private void cleanupForkBlocks(Block new_block) {
		List<String> keys = new ArrayList<String>(this.waitingBlocks.keySet());
		for(String hash: keys) {
			if(this.waitingBlocks.get(hash).getPrevious().equals(new_block.getPrevious())) {
				this.waitingBlocks.remove(hash);
			}
		}
	}

	private void handleNewBlockParsing(Block new_block) {
		// remove block transactions from transactionPool
		String[] transactions = new_block.getPayload().split("\\|\\|");
		
		HashSet<String> sameBlockTransactions = this.clearForwardTransactionsAt.getOrDefault(this.internalChain.getHeight(), new HashSet<>());
		
		for(String transaction: transactions) {
			Transaction trans = new Transaction(transaction);
			
			this.transactionPool.remove(trans.getHash());
			sameBlockTransactions.add(trans.getHash());
			this.acceptedTransactions++;
		}
		
		this.internalChain.addBlock(new_block);
		
		this.clearForwardTransactionsAt.put(this.internalChain.getHeight()+this.transactionsBlockHeightCache, sameBlockTransactions);
		
		// remove transaction hashes that should be expired
		this.clearForwardTransactionsAt.remove(this.internalChain.getHeight());
		
		this.accessChain.shiftForger();
	}
	
	private boolean parseNewAuthBlockSignal(NodeSignal signal) {
		Block block = new Block();
		block.decodeNewBlock(signal.getPayload());
		
		if(this.accessChain.hasPermission(signal.getOrigin(), AuthBlockchain.WRITE_AUTH_NODE) && this.accessChain.hasPermission(block.getCreator(), AuthBlockchain.WRITE_AUTH_NODE)) {	
			if(this.accessChain.isNextAuthCreator(signal.getOrigin())) {
				this.accessChain.addBlock(block);
				this.accessChain.shiftAuthForger();
				return true;
			} else {
				return false;
			}
		} else {
			// sending node does not have permissions to create blocks
			return false;
		}
	}
	
	private boolean signalNotWaitingForAck(String signal_hash) {
		return !this.forwardSignals.containsKey(signal_hash);
	}
	
	private boolean transactionWasNotReceivedBefore(String signal_hash, String trans_hash) {
		if(this.signalNotWaitingForAck(signal_hash)) {
			for(Integer block_id: this.clearForwardTransactionsAt.keySet()) {
				if(this.clearForwardTransactionsAt.get(block_id).contains(trans_hash)) {
					return false;
				}
			}
			
			return true;
		} else {
			return false;
		}
	}

	private void createForwardSignal(NodeSignal copied_signal) {
		copied_signal.setForwardOrigin(this.getId());
		
		this.forwardSignals.put(copied_signal.getHash(), new OutgoingNodeSignal(copied_signal, copied_signal.getForwardOrigin(), this.getId(), this.activeOutgoingConnections.keySet(), this));
	}
	
	private void createForwardSignalToAll(NodeSignal copied_signal) {
		copied_signal.setForwardOrigin(this.getId());
		
		HashSet<String> conns = new HashSet<>();
		conns.addAll(this.activeIncomingConnections.keySet());
		conns.addAll(this.activeOutgoingConnections.keySet());
		
		this.forwardSignals.put(copied_signal.getHash(), new OutgoingNodeSignal(copied_signal, copied_signal.getForwardOrigin(), this.getId(), conns, this));
	}
	
	protected void performSignalForward(int networkTick) {
		for(String node_id: this.forwardSignals.keySet()) {
			OutgoingNodeSignal sig = this.forwardSignals.get(node_id);
			
			sig.removeStalledConnections(this.signalRepetitionThreshold);
			
			ArrayList<String> nodesToSendTo = sig.shouldBeSentAgain(networkTick);
			
			for(String send_to: nodesToSendTo) {
				this.addPendingOutgoing(send_to, sig.getSignal(), networkTick);
			}
		}
	}
	
	protected void refreshUploadConnections() {
		double newMaxOutgoing = 2;
	
		try{
			double averageUtilization = (double)this.sentSinceLastUpdate / (double)this.network.connectionCheckDelay;
			newMaxOutgoing = this.speedUpload/averageUtilization;
			
			this.previousUploadSpeed = (int)averageUtilization;
		} catch (ArithmeticException ex) {} 
		
		if(newMaxOutgoing > this.limitOutgoing) {
			this.maxOutgoing = this.limitOutgoing;
		} else if(newMaxOutgoing > 2) {
			this.maxOutgoing = (int)newMaxOutgoing;
		} else {
			this.maxOutgoing = 2;
		}
		
		// check for stale connections
		ArrayList<String> upList = new ArrayList<>(this.activeOutgoingConnections.keySet());
		Iterator<String> upIter = upList.iterator();
		
		while(upIter.hasNext()) {
			String out = upIter.next();
			
			if(this.lastActiveSignalReceived.getOrDefault(out, 0) + this.connectionStale < this.network.getCurrentTick()) {
				if(this.network.closeUploadConnection(this.getId(), out)) {
					this.network.log.regular(this.getId(), "terminated UP to "+out, this.network.getCurrentTick());
					this.activeIncomingConnections.remove(out);
					this.connectionCooldownAt.put(out, this.network.currentTick + this.connectionCooldown);
				}
			}
		}
		
		//this.network.log.regular(this.getId(), "new max upload: "+maxOutgoing+" --> "+this.maxOutgoing+". UP speed "+this.speedUpload+" b/tick, "+this.sentSinceLastUpdate+" bits per "+this.network.connectionCheckDelay+" ticks", this.network.getCurrentTick());

		this.sentSinceLastUpdate = 0;
		
		if(this.activeOutgoingConnections.size() < this.maxOutgoing) {
			String newNode = this.network.establishNewUploadConnection(this.getId());
			if(newNode != null) {
				this.activeOutgoingConnections.put(newNode, this.network.getConnectionDelay(newNode));
				this.lastActiveSignalReceived.put(newNode, this.network.getCurrentTick());
			}
		} else if(this.activeOutgoingConnections.size() > this.maxOutgoing) {
			ArrayList<String> connectionList = new ArrayList<>(this.activeOutgoingConnections.keySet());
			Iterator<String> connectionIterator = connectionList.iterator();
			
			while(connectionIterator.hasNext()) {
				String node = connectionIterator.next();
				if(this.activeOutgoingConnections.size() <= this.maxOutgoing) {
					break;
				} else {
					if(this.network.closeUploadConnection(this.getId(), node)) {
						this.activeOutgoingConnections.remove(node);	
					}
				}
			}
		}
	};
	
	protected void refreshDownloadConnections() {
		double newMaxIncoming = 2; 
		
		try{
			double averageUtilization = (double)this.receivedSinceLastUpdate / (double)this.network.connectionCheckDelay;
			newMaxIncoming = this.speedDownload/averageUtilization;	
			
			this.previousDownloadSpeed = (int)averageUtilization;
		} catch (ArithmeticException ex) {} 

		if(newMaxIncoming > this.limitIncoming) {
			this.maxIncoming = this.limitIncoming;
		} else if(newMaxIncoming > 2) {
			this.maxIncoming = (int)newMaxIncoming;
		} else {
			this.maxIncoming = 2;
		}
		
		// check for stale connections
		ArrayList<String> downList = new ArrayList<>(this.activeIncomingConnections.keySet());
		Iterator<String> downIter = downList.iterator();
		
		while(downIter.hasNext()) {
			String out = downIter.next();
			
			if(this.lastActiveSignalReceived.getOrDefault(out, 0) + this.connectionStale < this.network.getCurrentTick()) {
				if(this.network.closeDownloadConnection(this.getId(), out)) {
					this.network.log.regular(this.getId(), "terminated DOWN from "+out, this.network.getCurrentTick());
					this.activeIncomingConnections.remove(out);
					this.connectionCooldownAt.put(out, this.network.currentTick + this.connectionCooldown);
				}
			}
		}
		
		//this.network.log.regular(this.getId(), "new max download: "+newMaxIncoming+" --> "+this.maxIncoming+". DOWN speed "+this.speedDownload+" b/tick, "+this.receivedSinceLastUpdate+" bits per "+this.network.connectionCheckDelay+" ticks", this.network.getCurrentTick());
		
		this.receivedSinceLastUpdate = 0;
		
		if(this.activeIncomingConnections.size() < this.maxIncoming) {
			this.print("Will add "+this.activeIncomingConnections.toString());
			String newNode = this.network.establishNewDownloadConnection(this.getId());
			if(newNode != null) {
				this.activeIncomingConnections.put(newNode, this.network.getConnectionDelay(newNode));
				this.lastActiveSignalReceived.put(newNode, this.network.getCurrentTick());
			}
		} else if(this.activeIncomingConnections.size() > this.maxIncoming) {
			this.print("Will close");
			ArrayList<String> connectionList = new ArrayList<>(this.activeIncomingConnections.keySet());
			Iterator<String> connectionIterator = connectionList.iterator();
			
			while(connectionIterator.hasNext()) {
				String node = connectionIterator.next();
				if(this.activeIncomingConnections.size() <= this.maxIncoming) {
					break;
				} else {
					if(this.network.closeDownloadConnection(this.getId(), node)) {
						this.activeIncomingConnections.remove(node);
					}
				}
			}
		}
	}
	
	protected boolean closeDownloadFrom(String source) {
		if(this.activeIncomingConnections.containsKey(source)) {
			this.removeForwardSignalsToNode(source);
			this.network.log.regular(this.getId(), "closed DOWNLOAD from "+source, this.network.currentTick);
			this.connectionCooldownAt.put(source, this.network.currentTick + this.connectionCooldown);
			this.activeIncomingConnections.remove(source);
			return true;
		}
		
		return false;
	}
	
	protected boolean closeUploadTo(String target) {
		if(this.activeOutgoingConnections.containsKey(target)) {
			this.removeForwardSignalsToNode(target);
			this.activeOutgoingConnections.remove(target);
			this.network.log.regular(this.getId(), "closed UPLOAD to "+target, this.network.currentTick);
			this.connectionCooldownAt.put(target, this.network.currentTick + this.connectionCooldown);
			return true;
		}
		
		return false;
	}
	
	private void removeForwardSignalsToNode(String node) {
		for(OutgoingNodeSignal sig: this.forwardSignals.values()) {
			sig.markNodeFinalResponseReceived(node);
		}
	}
	
	/**
	 * Allows child classes to specify their own logic
	 * Used for transactions, blocks, etc.
	 * @return Integer, delay caused by logic execution
	 */
	protected abstract int executeSpecificLogic(int networkTick);
	
	public void executeIncomingConnection(SimulatedJob job, int networkTick) {
		this.waitingSignals.addFirst(job);
		this.executeIncomingThreads(networkTick);
	}
	
	/**
	 * Returns a list of pending outgoing jobs, which will be executed as "incoming" for target nodes
	 * e.g. abstraction of command "NewTransactionSignal to node_2 in 100 ticks with signal"
	 * 
	 * @return
	 */
	public ArrayList<SimulatedJob> getPendingOutgoingConnections(){
		return this.pendingOutgoing;
	}
	
	public void clearPendingOutgoingConnections() {
		this.pendingOutgoing.clear();
	}
	
	/**
	 * Adds a new pending outgoing job for a particular target with proper connection costs
	 * 
	 * @param target
	 * @param signal
	 * @param delay
	 */
	protected void addPendingOutgoing(String target, NodeSignal signal, int delay) {
		this.signalsSent.set(signal.getType(), this.signalsSent.get(signal.getType())+1);
		
		try {
			this.sentSinceLastUpdate += signal.toString().getBytes("UTF-8").length;
		} catch(UnsupportedEncodingException ex) {}
		
		this.pendingOutgoing.add(new SimulatedJob(signal, target, delay+this.getConnectionDelay(target)));
	}
	
	protected void addPendingOutgoingToAll(NodeSignal signal, int delay) {
		for(String nodeId: this.activeOutgoingConnections.keySet()) {
			if(!signal.getOrigin().equals(nodeId)) {
				this.addPendingOutgoing(nodeId, signal, delay);
			}
		}
	}
	
	/**
	 * Optional signal sending to upload connections, usually for ACK/ACTIVE signals
	 * 
	 * @param signal
	 * @param delay
	 * @param includeDownload, specifies if the signal can travel back to downloading nodes (used for ACTIVE signals)
	 */
	protected void addPendingOutgoingToAll(NodeSignal signal, int delay, boolean includeDownload) {
		this.addPendingOutgoingToAll(signal, delay);
		
		if(includeDownload) {
			for(String nodeId: this.activeIncomingConnections.keySet()) {
				if(!this.activeOutgoingConnections.containsKey(nodeId)) {
					this.addPendingOutgoing(nodeId, signal, delay);
				}
			}
		}
	}
	
	public boolean establishUploadConnection(String caller, int connectionDelay) {
		if(this.activeOutgoingConnections.containsKey(caller) || this.connectionCooldownAt.containsKey(caller)) {
			return false;
		} else {
			if(this.activeOutgoingConnections.keySet().size() + 1 > this.maxOutgoing) {
				return false;
			} else {
				this.activeOutgoingConnections.put(caller, connectionDelay);
				this.lastActiveSignalReceived.put(caller, this.network.currentTick);
				this.network.log.regular(this.getId(), "New UP connection to "+caller+", delay "+connectionDelay, this.network.getCurrentTick());
				return true;
			}
		}
	}

	public boolean establishDownloadConnection(String caller, int connectionDelay) {
		if(this.activeIncomingConnections.containsKey(caller) || this.connectionCooldownAt.containsKey(caller)) {
			return false;
		} else {
			if(this.activeIncomingConnections.keySet().size() + 1 > this.maxIncoming) {
				return false;
			} else {
				this.activeIncomingConnections.put(caller, connectionDelay);
				this.lastActiveSignalReceived.put(caller, this.network.currentTick);
				this.network.log.regular(this.getId(), "New DOWN connection from "+caller+", delay "+connectionDelay, this.network.getCurrentTick());
				return true;
			}
		}
	}
	
	protected void print(String message) {
		//System.out.println(this.network.getCurrentTick()+", "+this.getId()+", "+message);
	}
	
	protected void print(String message, int delay) {
		//System.out.println(this.network.getCurrentTick()+delay+", "+this.getId()+", "+message);
	}

	public String getId() {
		return this.uuid;
	}

	public void setDownloadConnectionLimit(int nextInt) {
		this.limitIncoming = nextInt;
	}

	public void setUploadConnectionLimit(int nextInt) {
		this.limitOutgoing = nextInt;
	}

	public void setUploadSpeed(int upload) {
		this.speedUpload = upload;
	}

	public void setDownloadSpeed(int download) {
		this.speedDownload = download;
	}

	public void setConnectionCooldown(int nextInt) {
		this.connectionCooldown = nextInt;
	}

	public void setConnectionStale(int nextInt) {
		this.connectionStale = nextInt;
	}

	public void setRepetitionDisconnectThreshold(int nextInt) {
		this.signalRepetitionThreshold = nextInt;
	}

	public int getConnectionDelay(String node) {
		if(this.activeIncomingConnections.containsKey(node)) {
			return this.activeIncomingConnections.get(node);
		} else if(this.activeOutgoingConnections.containsKey(node)) {
			return this.activeOutgoingConnections.get(node);
		} else {
			return this.network.getConnectionDelay(this.getId(), node);
		}
	}
}
