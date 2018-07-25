package simulatedHybridBlockchain;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Block {
	protected String payload;
	protected String creator;
	protected String previous_hash;
	protected String previous_chunk;
	protected int height;
	
	public Block() {
		this.payload = null;
		this.creator = null;
		this.previous_hash = null;
		this.previous_chunk = null;
		this.height = -1;
	}

	public void addPayload(String payload) {
		if(this.payload == null) {
			this.payload = payload;
		} else {
			this.payload += "||"+payload;
		}
	}

	/**
	 * Calculates SHA-256 hash of the block from this.toString()
	 * @return String, SHA-256 hash | null
	 */
	public String getHash() {
		try {
			MessageDigest hash = MessageDigest.getInstance("SHA-256");
			
			String block = this.toString();
			hash.update(block.getBytes("UTF-8"));
			
			return String.format("%064x", new BigInteger(1, hash.digest()));
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}		
		
		return null;
	}

	public void addPrevious(String hash) {
		this.previous_hash = hash;
	}
	
	public void addPreviousChunk(String hash) {
		this.previous_chunk = hash;
	}

	public void setHeight(int i) {
		this.height = i;
	}

	public int getHeight() {
		return this.height;
	}

	public void addCreator(String creator) {
		this.creator = creator;
	}
	
	public String getCreator() {
		return this.creator;
	}

	public String getPrevious() {
		return this.previous_hash;
	}
	
	public String getPreviousChunk() {
		return this.previous_chunk;
	}
	
	public String getPayload() {
		return this.payload;
	}
	
	public void decodeNewBlock(String signal_payload) {
		// assumes signal payload follows structure from Block.toString and Block.addPayload
		// ie. "height:forger_id:previous_hash:payload_part||payload_part||..."
		
		String[] block_data = signal_payload.split(":", 5);
		this.height = Integer.parseInt(block_data[0]);
		this.creator = block_data[1];
		this.previous_hash = block_data[2];
		this.previous_chunk = block_data[3];
		this.payload = block_data[4];
	}
	
	@Override
	public String toString() {
		return this.getHeight()+":"+this.getCreator()+":"+this.getPrevious()+":"+this.getPreviousChunk()+":"+this.getPayload();
	}
}
