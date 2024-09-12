package blockchain;

import java.io.Serializable;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;

public class Block implements Serializable {
    private static final long serialVersionUID = 2317709595944805708L;
    private int blockNumber = 1;
    private String prevBlockHash;
    private long blockTimestamp;
    private int nonce;
    private String merkleRoot;
    private ArrayList<Transaction> blockTransactions;
    /*
     * usually you can order the transactions in timestamp order (or by other
     * methods)
     * and then hash them accordingly to form the merkle root (which you then use to
     * check
     * a transaction's inclusion)
     */

    // to reward the miner after mining
    private String minerAddress;

    // what we are ultimately trying to work out
    private String thisBlockHash;

    public Block(ArrayList<Transaction> genesisTransaction, Mining miner) {
        // Default or placeholder values for the genesis block
        this.prevBlockHash = "0";
        this.minerAddress = miner.getMinerAddress();
        this.blockTimestamp = System.currentTimeMillis();
        this.merkleRoot = miner.merkleRootHash(genesisTransaction);
        this.blockTransactions = genesisTransaction;
        String combinedString = prevBlockHash + String.valueOf(blockTimestamp) + merkleRoot;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            // Hash the combined string
            byte[] hashBytes = md.digest(combinedString.getBytes());
            String tempBlockHash = miner.byteToString(hashBytes);

            while (!tempBlockHash.substring(0, miner.getDifficulty()).equals("0".repeat(miner.getDifficulty()))) {
                nonce += 1;
                combinedString = prevBlockHash + String.valueOf(blockTimestamp) + merkleRoot + String.valueOf(nonce);
                hashBytes = md.digest(combinedString.getBytes());
                tempBlockHash = miner.byteToString(hashBytes);
            }

            this.thisBlockHash = tempBlockHash;
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }

    }

    public Block(String previousHash, String miningAddress, long timestamp, ArrayList<Transaction> transactionsInBlock,
            String merkleRootHash, String BlockHash, int nonce) {
        this.prevBlockHash = previousHash;
        this.minerAddress = miningAddress;
        this.blockTimestamp = timestamp;
        this.merkleRoot = merkleRootHash;
        this.blockTransactions = transactionsInBlock;
        this.thisBlockHash = BlockHash;
    }

    public int getBlockNumber() {
        return this.blockNumber;
    }

    public void setBlockNumber(int number) {
        this.blockNumber = number;
    }

    public String getPreviousHash() {
        return this.prevBlockHash;
    }

    public String getBlockHash() {
        return this.thisBlockHash;
    }

    public String getMinerAddress() {
        return this.minerAddress;
    }

    public String getMerkleRoot() {
        return this.merkleRoot;
    }

    public long getTimeStamp() {
        return this.blockTimestamp;
    }

    public int getNonce() {
        return this.nonce;
    }

    public ArrayList<Transaction> getBlockTransactions() {
        return this.blockTransactions;
    }

}
