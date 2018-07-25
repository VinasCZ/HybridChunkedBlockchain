package simulatedHybridBlockchain;

import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public abstract class NodeSignal {
	public static final int ACTIVE_SIGNAL = 0;
	public static final int NEW_BLOCK_SIGNAL = 1;
	public static final int NEW_AUTH_BLOCK_SIGNAL = 2;
	
	public static final int NEW_TRANSACTION_SIGNAL = 3;
	
	public static final int ACK_SIGNAL = 4;
	
	public static final int BLOCK_REQUEST_SIGNAL = 5;
	public static final int BLOCK_CHUNK_REQUEST_SIGNAL = 6;
	
	public static final int AUTH_BLOCK_REQUEST_SIGNAL = 7;
	
	public static final int REQUEST_DENY_SIGNAL = 8;
	public static final int REQUEST_WAIT_SIGNAL = 9;
	
	public static final int BLOCK_RESPONSE_SIGNAL = 10;
	
	protected int type;
	protected String origin;
	protected String payload;
	protected String forwardOrigin;
	
	public NodeSignal(String origin, String payload) {
		this.origin = origin;
		this.payload = payload;
		this.forwardOrigin = origin;
	}
	
	public int getType() {
		return this.type;
	}
	
	public String getOrigin() {
		return this.origin;
	}
	
	public String getForwardOrigin() {
		return this.forwardOrigin;
	}
	
	/**
	 * Records forwarder, used in propagation of forwarded signals to determine immediate origin
	 * NB used for simplification of simulation, real-life system knows which node sent them the forwarded signal.
	 * @param node
	 */
	public void setForwardOrigin(String node) {
		this.forwardOrigin = node;
	}
	
	public String getPayload() {
		return this.payload;
	}
	
	public void setPayload(String payload) {
		this.payload = payload;
	}
	
	/**
	 * Returns Signal structured as
	 * <type_id> <origin_name> <signal_payload>
	 * 
	 * signal_payload can be null
	 * forward_origin can be the same as origin_name
	 */
	@Override
	public String toString() {
		return this.type+" "+this.origin+" "+this.payload;
	}
	
	public String getHash() {
		try {
			MessageDigest hash = MessageDigest.getInstance("SHA-256");
			
			String sig = this.toString();
			hash.update(sig.getBytes("UTF-8"));
			
			return String.format("%064x", new BigInteger(1, hash.digest()));
		} catch (NoSuchAlgorithmException|UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		
		return null;
	}
}
