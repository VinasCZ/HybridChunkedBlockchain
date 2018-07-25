package simulatedHybridBlockchain;

import java.util.HashMap;

public class Blockchain {
	protected HashMap<String, Block> blocks = new HashMap<>();
	private HashMap<String, BlockChunk> chunks = new HashMap<>();
	
	protected int height = 0;
	private String last_block;
	private String last_chunk;
	
	private int lastReceived = 0;
	
	public Blockchain() {
		this.last_block = "INIT";
		this.last_chunk = "INIT";
	}
	
	public Blockchain(String initial_payload) {
		this.last_chunk = null;
		
		Block block = new Block();
		block.addPayload(initial_payload);
		block.addCreator("INIT");
		
		this.addBlock(block);
	}

	public void addBlock(Block block) {
		// establish Merkle tree
		block.addPrevious(this.last_block);
		block.setHeight(this.height+1);
		
		this.blocks.put(block.getHash(), block);
		this.height++;
		this.last_block = block.getHash();
	}
	
	public void addChunk() {
		BlockChunk chunk = new BlockChunk();
		
		chunk.addPrevious(last_chunk);
		
		String block_hash = this.getCurrentHash();
		
		while(true) {
			Block inspected = this.blocks.getOrDefault(block_hash, null);
			
			if(inspected == null) {
				break;
			}
			
			String last = inspected.getPreviousChunk();
			
			if(last != null && last.equals(this.last_chunk)) {
				chunk.addBlock(block_hash);
				block_hash = inspected.getPrevious();
			} else {
				break;
			}
		}
		
		this.chunks.put(chunk.getHash(), chunk);
		this.last_chunk = chunk.getHash();
	}
	
	public int getHeight() {
		return this.height;
	}
	
	public Block getCurrentBlock() {
		return this.blocks.getOrDefault(this.last_block, null);
	}
	
	public BlockChunk getCurrentChunk() {
		return this.chunks.getOrDefault(this.last_chunk, null);
	}
	
	public String getCurrentHash() {
		return this.last_block;
	}
	
	public String getCurrentChunkHash() {
		return this.last_chunk;
	}

	public String findByPrevious(String block_hash) {
		int search_height = this.height;
		String last_known_hash = this.last_block;
		
		while(search_height > 0) {
			Block prev = this.blocks.getOrDefault(last_known_hash, null);
			
			if(prev == null) {
				// we don't have previous block, perhaps it is a part of un-fetched chunk
				// return an empty response instead of null
				return "";
			}
			
			if(prev.getPrevious().equals(block_hash)) {
				return this.blocks.get(last_known_hash).toString();
			} else {
				last_known_hash = this.blocks.get(last_known_hash).getPrevious();
			}
			
			search_height--;
		}
		
		return null;
	}

	public boolean suspiciousFuture(int height2) {
		return this.height + 5 < height2;
	}

	public int getLastReceived() {
		return this.lastReceived;
	}

	public void setLastReceived(int processedAt) {
		this.lastReceived = processedAt;
	}
}
