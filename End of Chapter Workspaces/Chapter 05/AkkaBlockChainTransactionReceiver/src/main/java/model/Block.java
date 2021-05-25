package model;

import java.util.List;

public class Block {
	private String previousHash;
	private List<Transaction> transactions;
	private int nonce;
	private String hash;
	
	public Block(List<Transaction> transactions, String previousHash) {
		this.previousHash = previousHash;
		this.transactions = transactions;
	}
	
	public void setNonce(int nonce) {
		this.nonce = nonce;
	}
	
	public void setHash(String hash) {
		this.hash = hash;
	}
		
	public String getHash() {
		return hash;
	}

	public String getPreviousHash() {
		return previousHash;
	}

	public List<Transaction> getTransactions() {
		return transactions;
	}
	
	public int getNonce() {
		return nonce;
	}
	
}
