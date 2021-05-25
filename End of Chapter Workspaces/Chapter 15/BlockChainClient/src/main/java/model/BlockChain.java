package model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.LinkedList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BlockChain {

	private LinkedList<Block> blocks;
	
	public BlockChain() {
		blocks = new LinkedList<>();
	}

	public void setBlocks(LinkedList<Block> blocks) {
		this.blocks = blocks;
	}

	public void printAndValidate() {
		System.out.println(blocks);
		String lastHash = "0";
		for (Block block : blocks) {
			System.out.println("model.Block with trans starting at " + block.getFirstId() + " ");
			System.out.println(block.getTransactions());
			System.out.println("Hash " + block.getHash());
			System.out.println("Lash Hash " + block.getPreviousHash());
			
			if (block.getPreviousHash().equals(lastHash)) {
				System.out.print("Last hash matches ");
			} else {
				System.out.print("Last hash doesn't match ");
			}

			lastHash = block.getHash();
			
		}
	}
	
	public String getLastHash() {
		if (blocks.size() > 0)
			return blocks.getLast().getHash();
		return "0";
	}

	public int getSize() {
		return blocks.size();
	}

	public List<Block> getBlocks() {
		return List.copyOf(blocks);
	}

}
