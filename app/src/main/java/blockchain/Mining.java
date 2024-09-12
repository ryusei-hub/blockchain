package blockchain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;

public class Mining {
    private int nonce = 0;
    private String previousHash;
    private String merkleRoot;
    private long timestamp;
    private String blockHash;
    private String minerAddress;
    private double minerReward;
    private UTXOPool utxoPool;
    private Block block; // what we will produce
    private int numOfTransactions = 2;
    private int difficulty = 6;

    public Mining(String previousHash, String minerAddress, UTXOPool utxoPool, double minerReward) {
        this.previousHash = previousHash;
        this.timestamp = System.currentTimeMillis();
        this.minerAddress = minerAddress;
        this.utxoPool = utxoPool;
        this.nonce = 0;
        this.minerReward = minerReward;
    }

    public int getNumOfTransactions() {
        return this.numOfTransactions;
    }

    public String getMinerAddress() {
        return this.minerAddress;
    }

    public void setMinerAddress(String minerAddress) {
        this.minerAddress = minerAddress;
    }

    public ArrayList<Transaction> getBlockTransactionsToBeHashed(Mempool mempool) {
        // retrieves a set number of transactions from the Mempool to be made into a
        // block
        ArrayList<Transaction> transactionList = new ArrayList<>();

        if (mempool.getTransactions().size() >= numOfTransactions) {
            for (int i = 0; i < numOfTransactions; i++) {
                transactionList.add(mempool.getTransactions().poll());
            }

        }
        return transactionList;
    }

    public String merkleRootHash(ArrayList<Transaction> transactions) {
        // Edge case: if there is only one transaction, return its hash.
        // Merkle Root = Transaction hash.
        if (transactions.size() == 1) {
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                final byte[] rewardHash = md.digest(transactions.get(0).getTransactionString().getBytes());
                String hexString = byteToString(rewardHash);
                return hexString;
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException("SHA-256 algorithm not available", e);
            }
        }

        ArrayList<String> merkleTree = new ArrayList<>();

        // You now want to hash every transaction.
        for (int i = 0; i < transactions.size(); i += 1) {
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                final byte[] transactionHash = md.digest(transactions.get(i).getTransactionString().getBytes());
                merkleTree.add(byteToString(transactionHash));
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException("SHA-256 algorithm not available", e);
            }
        }

        // You have [HashA, HashB, HashC]
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            while (merkleTree.size() > 1) {
                ArrayList<String> nextLevel = new ArrayList<>();

                for (int i = 0; i < merkleTree.size(); i += 2) {
                    String currentHash = merkleTree.get(i);
                    String nextHash = (i + 1 < merkleTree.size()) ? merkleTree.get(i + 1) : currentHash; // Duplicate
                                                                                                         // last
                                                                                                         // hash if odd
                                                                                                         // number of
                                                                                                         // elements

                    // Concatenate and hash the current and next hash (or duplicated last hash)
                    String combinedHash = currentHash + nextHash;
                    byte[] hashBytes = md.digest(combinedHash.getBytes(StandardCharsets.UTF_8));
                    String hashHex = byteToString(hashBytes);

                    nextLevel.add(hashHex);
                }
                merkleTree = nextLevel; // Prepare for the next iteration with the newly formed level
            }

        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }

        merkleRoot = merkleTree.get(0);
        return merkleRoot;
    }

    public String calculateBlockHash(String previousHash, long timestamp, String merkleRoot) {

        String combinedString = previousHash + String.valueOf(timestamp) + merkleRoot + String.valueOf(nonce);
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            // Hash the combined string
            byte[] hashBytes = md.digest(combinedString.getBytes());
            blockHash = byteToString(hashBytes);

            while (!blockHash.substring(0, difficulty).equals("0".repeat(difficulty))) {
                nonce += 1;
                combinedString = previousHash + String.valueOf(timestamp) + merkleRoot + String.valueOf(nonce);
                hashBytes = md.digest(combinedString.getBytes());
                blockHash = byteToString(hashBytes);
            }

            return blockHash;
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }

    }

    public UTXOPool getUTXOPool() {
        return this.utxoPool;
    }

    // add mined block to the blockchain
    public Block mineBlock(ArrayList<Transaction> transactionsToMine, Node node) {
        // Create a temporary copy of the UTXOPool to verify transactions against
        UTXOPool tempUTXOPool = node.getUTXOPool();

        ArrayList<Transaction> validTransactions = new ArrayList<>();
        for (Transaction transaction : transactionsToMine) {
            boolean isValid = true;
            for (Input input : transaction.getInputs()) {
                UTXOKey utxoKey = new UTXOKey(input.getTxId(), input.getOutputIndex());
                if (!tempUTXOPool.containsUTXOKey(utxoKey)) {
                    isValid = false;
                    break; // This input does not have a corresponding UTXO, thus invalid
                }
            }
            if (isValid) {
                validTransactions.add(transaction);
                // Mark UTXOs as spent for subsequent transactions
                for (Input input : transaction.getInputs()) {
                    UTXOKey utxoKey = new UTXOKey(input.getTxId(), input.getOutputIndex());
                    tempUTXOPool.removeFromUTXOPool(utxoKey);
                }
            }
        }

        // Now, validTransactions contains only the transactions that are valid
        // considering the current UTXO set.

        // Add the miner reward transaction
        Transaction minerRewardTransaction = createMinerRewardTransaction(node, validTransactions,
                node.getBlockchain().getBlockHeight());
        validTransactions.add(minerRewardTransaction); // Add miner reward at the end

        String merkleRoot = merkleRootHash(validTransactions);
        String outputHash = calculateBlockHash(previousHash, timestamp, merkleRoot);

        // Create a new block with only the valid transactions
        block = new Block(previousHash, minerAddress, timestamp, validTransactions, merkleRoot, outputHash,
                nonce);

        // Process UTXOs for valid transactions, including miner reward
        for (Transaction transaction : validTransactions) {
            String transactionHash = transaction.getHash();
            // Inputs were already removed from tempUTXOPool, process outputs now
            for (int i = 0; i < transaction.getOutputs().size(); i++) {
                Output output = transaction.getOutputs().get(i);
                UTXOKey utxoKey = new UTXOKey(transactionHash, i);
                UTXO utxo = new UTXO(output.getValue(), output.getAddress());
                node.getUTXOPool().addUTXO(utxoKey, utxo);
            }
            // Remove the transaction from mempool, if applicable
            if (transaction != minerRewardTransaction) {
                node.getMempool().removeTransactionFromMempool(transaction);
            }
        }

        return block;
    }

    private Transaction createMinerRewardTransaction(Node node, ArrayList<Transaction> transactions, int blockHeight) {
        // Create a new output with the miner's reward

        ArrayList<Input> inputList = new ArrayList<>();
        ArrayList<Output> outputList = new ArrayList<>();

        double minerGains = minerReward;
        if (transactions.size() != 0) {
            for (Transaction transaction : transactions) {
                minerGains += transaction.getFee();
            }
        }

        String prevTxIdToUse = "reward" + Integer.toString(blockHeight);
        Input minerRewardInput = new Input(prevTxIdToUse, -1, null, node.getUserWallet().getPublicKey());
        Output minerRewardOutput = new Output(minerGains, minerAddress);
        inputList.add(minerRewardInput);
        outputList.add(minerRewardOutput);

        // Create a new transaction with the miner reward output
        Transaction minerRewardTransaction = new Transaction(inputList, outputList);
        return minerRewardTransaction;
    }

    public String byteToString(byte[] hash) {
        StringBuilder hexString = new StringBuilder(); // convert the hash to string
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1)
                hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    public int getDifficulty() {
        return this.difficulty;
    }
}