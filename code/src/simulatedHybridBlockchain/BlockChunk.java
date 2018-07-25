package simulatedHybridBlockchain;

import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashSet;

public class BlockChunk {
	private LinkedHashSet<String> blocks = new LinkedHashSet<>();
	private String previous;
	
	public BlockChunk() {
		
	}
	
	public void addPrevious(String last_chunk) {
		this.previous = last_chunk;
	}
	
	@Override
	public String toString() {
		return this.previous+":"+String.join("||", this.blocks);
	}

	/**
	 * Calculates SHA-256 hash of the block from this.toString()
	 * @return String, SHA-256 hash | null
	 */
	public String getHash() {
		try {
			MessageDigest hash = MessageDigest.getInstance("SHA-256");
			
			String chunk = this.toString();
			hash.update(chunk.getBytes("UTF-8"));
			
			return String.format("%064x", new BigInteger(1, hash.digest()));
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		
		return null;
	}

	public void addBlock(String block_hash) {
		this.blocks.add(block_hash);
	}
	
}
