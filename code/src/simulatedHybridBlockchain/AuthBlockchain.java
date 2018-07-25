package simulatedHybridBlockchain;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;

public class AuthBlockchain extends Blockchain {
	
	// permission are iterative, e.g. WRITE can PUBLISH and READ too.
	public static final int READ_NODE = 0;
	public static final int PUBLISH_NODE = 1;
	public static final int WRITE_NODE = 2;
	public static final int WRITE_AUTH_NODE = 3;
	
	private HashMap<String, Integer> nodePermissions = new HashMap<>();
	private ArrayDeque<String> writeNodes = new ArrayDeque<>();
	private ArrayDeque<String> authWriteNodes = new ArrayDeque<>();
	
	public boolean hasPermission(String origin, int requiredPermission) {
		return this.nodePermissions.getOrDefault(origin, 0) >= requiredPermission;
	}

	public boolean isFutureBlock(String origin) {
		Iterator<String> writeIterator = this.writeNodes.iterator();
		int blockLimit = 5;
		int currentBlock = 1;
		
		while(writeIterator.hasNext() && currentBlock <= blockLimit) {
			if(writeIterator.next().equals(origin)) {
				return true;
			}
			
			currentBlock++;
		}
		
		return false;
	}

	public boolean isNextCreator(String origin) {
		return this.writeNodes.size() > 0 ? this.writeNodes.peek().equals(origin) : true;
	}
	
	public boolean isNextAuthCreator(String origin) {
		return this.writeNodes.size() > 0 ? this.writeNodes.peek().equals(origin) : true;
	}
	
	public String getNextCreator() {
		return this.writeNodes.size() > 0 ? this.writeNodes.peek() : null;
	}
	
	public String getNextAuthCreator() {
		return this.authWriteNodes.size() > 0 ? this.authWriteNodes.peek() : null;
	}

	public void shiftForger() {
		String current = this.writeNodes.pollFirst();
		
		if(current != null) {
			this.writeNodes.addLast(current);
		}
	}
	
	public void shiftAuthForger() {
		String current = this.authWriteNodes.pollFirst();
		
		if(current != null) {
			this.authWriteNodes.addLast(current);
		}
	}
	
	public void parseBlock(Block block) {
		String[] auths = block.getPayload().split("\\|\\|");
		
		for(String auth: auths) {
			String[] vals = auth.split(" ");
			int perm = this.nodePermissions.getOrDefault(vals[0], -1);
			
			if(Integer.parseInt(vals[1]) > perm) {
				this.nodePermissions.put(vals[0], Integer.parseInt(vals[1]));
				perm = this.nodePermissions.get(vals[0]);
			}

			if(this.hasPermission(vals[0], AuthBlockchain.WRITE_NODE)) {
				this.writeNodes.addLast(vals[0]);
			}
			
			if(this.hasPermission(vals[0], AuthBlockchain.WRITE_AUTH_NODE)) {
				this.authWriteNodes.addLast(vals[0]);
			}
		}
	}

	/**
	 * Serves to seed the initial authorizations
	 * 
	 * @param initialAuths
	 */
	public void addInitial(HashMap<String, Integer> initialAuths) {
		Block block = new Block();
		for(String key: initialAuths.keySet()) {
			block.addPayload(key+" "+initialAuths.get(key));
		}
		
		this.parseBlock(block);
	}
}
