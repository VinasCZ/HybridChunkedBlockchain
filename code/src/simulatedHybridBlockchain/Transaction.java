package simulatedHybridBlockchain;

import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Transaction {
	protected String origin;
	protected String payload;
	
	public Transaction(String origin, String payload) {
		this.origin = origin;
		this.payload = payload;
	}
	
	public Transaction(String signal_payload) {
		this.origin = null;
		this.payload = null;
		
		this.decode(signal_payload);
	}
	
	private void decode(String data) {
		// assumes that delimited is ";" as specified in Transaction.toString()
		String[] parts = data.split(";", 2);
		
		this.origin = parts[0];
		this.payload = parts[1];
	}
	
	public String getPayload() {
		return this.payload;
	}
	
	@Override
	public String toString() {
		return this.origin+";"+this.payload;
	}
	
	/**
	 * Returns SHA256 hash of the transaction
	 * @return String
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
}
