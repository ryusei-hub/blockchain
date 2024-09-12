package blockchain;

import java.io.File;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.bitcoinj.core.AddressFormatException;

import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

public class Node {
    private Blockchain blockchain;
    private Mempool mempool;
    private UTXOPool utxoPool;
    private final int port;
    private ExecutorService taskExecutor = Executors.newCachedThreadPool();

    private PeerManager peerManager;
    private volatile boolean shouldInterrupt = false;
    private Set<String> processedTransactions = new HashSet<>();
    private Set<String> processedBlocks = new HashSet<>();
    private Wallet userWallet = new Wallet();
    private ArrayList<Wallet> userWallets = new ArrayList<Wallet>();
    private WalletServer walletServer;
    private double minerReward = 2;

    public void halveMinerReward() {
        this.minerReward /= 2;
    }

    public Node(int port) {
        this.port = port;
    }

    public Set<String> getProcessedTransactions() {
        return this.processedTransactions;
    }

    public Set<String> getProcessedBlocks() {
        return this.processedBlocks;
    }

    public void addProcessedBlocks(String blockHash) {
        processedBlocks.add(blockHash);
    }

    public void addProcessedTransactions(String txHash) {
        processedTransactions.add(txHash);
    }

    public int getPort() {
        return this.port;
    }

    public Wallet getUserWallet() {
        return this.userWallet;
    }

    public ExecutorService getTaskExecutorService() {
        return this.taskExecutor;
    }

    public PeerManager getPeerManager() {
        return this.peerManager;
    }

    public void findBlockchain() throws InvalidBlockchainException {
        if (peerManager.getPeerConnections().isEmpty()) {
            System.out.println(peerManager.getPeerConnections());
            File blockChainFile = new File("blockchain.ser");
            if (!blockChainFile.exists()) {
                System.out.println("Creating new blockchain.");
                blockchain = new Blockchain();
            } else {
                System.out.println("Fetching existing blockchain.");
                Blockchain tempBlockchain = FileSaver.loadBlockchain("blockchain.ser");
                UTXOPool tempUTXOPool = new UTXOPool();
                for (int j = 0; j < tempBlockchain.getChain().size(); j++) {
                    Block block = tempBlockchain.getChain().get(j);
                    for (Transaction transaction : block.getBlockTransactions()) {
                        if (transaction.getInputs().get(0).getDigitalSignature() != null) {
                            ArrayList<Output> outputs = transaction.getOutputs();
                            ArrayList<Input> inputs = transaction.getInputs();
                            boolean validTransaction = false;
                            String transactionString = transaction.getTransactionString();
                            for (Input input : inputs) {
                                PublicKey senderPublicKey = input.getPublicKey();
                                validTransaction = input.verifySignature(senderPublicKey, transactionString);
                                if (!validTransaction) {
                                    System.out.println("Invalid Signature: " + input.getTxId());
                                    break;
                                }
                            }
                            if (validTransaction) {
                                String transactionHash = transaction.getHash();
                                for (Input input : inputs) {
                                    String txHash = input.getTxId();
                                    int index = input.getOutputIndex();
                                    UTXOKey keyToRemove = new UTXOKey(txHash, index);
                                    if (tempUTXOPool.containsUTXOKey(keyToRemove)) {
                                        tempUTXOPool.removeFromUTXOPool(keyToRemove);
                                    } else {
                                        System.out.println(
                                                "Transaction sender: " + transaction.getOutputs().get(1).getAddress()
                                                        + "\n TX Hash of a previous UTXO (supposedly): " + txHash
                                                        + "\n Output index of that UTXO: " + index
                                                        + "\n Amount sent: "
                                                        + transaction.getOutputs().get(0).getValue());
                                        blockchain.getChain().clear();
                                        for (int i = 0; i <= j; i++) {
                                            Block blockToCopy = tempBlockchain.getChain().get(i);
                                            blockchain.addBlock(blockToCopy);
                                        }
                                        throw new InvalidBlockchainException(
                                                "Invalid block received: UTXO not found in pool");
                                    }
                                }

                                for (int i = 0; i < outputs.size(); i++) {
                                    Output output = outputs.get(i);
                                    UTXOKey utxoKey = new UTXOKey(transactionHash, i); // Use the transaction hash and
                                                                                       // output
                                                                                       // index
                                                                                       // as the
                                                                                       // key
                                    UTXO utxo = new UTXO(output.getValue(), output.getAddress());
                                    tempUTXOPool.addUTXO(utxoKey, utxo); // Add the new UTXO to the pool
                                }
                            } else {
                                blockchain.getChain().clear();
                                for (int i = 0; i <= j; i++) {
                                    Block blockToCopy = tempBlockchain.getChain().get(i);
                                    blockchain.addBlock(blockToCopy);
                                }
                                for (Block blocks : blockchain.getChain()) {
                                    processedBlocks.add(blocks.getBlockHash());
                                    for (Transaction transactions : blocks.getBlockTransactions()) {
                                        processedTransactions.add(transactions.getHash());
                                    }
                                }
                                throw new InvalidBlockchainException(
                                        "Invalid block received: Invalid Transaction Signature");
                            }

                        } else {
                            if (!(transaction.getInputs().get(0).getTxId().startsWith("reward"))
                                    && !(transaction.getInputs().get(0).getTxId().equals("genesis"))) {

                                throw new InvalidBlockchainException("Invalid block received: Invalid Reward Block.");
                            } else {
                                ArrayList<Output> outputs = transaction.getOutputs();
                                String transactionHash = transaction.getHash();

                                for (int i = 0; i < outputs.size(); i++) {
                                    Output output = outputs.get(i);
                                    UTXOKey utxoKey = new UTXOKey(transactionHash, i); // Use the transaction hash and
                                                                                       // output
                                                                                       // index
                                                                                       // as the
                                                                                       // key
                                    UTXO utxo = new UTXO(output.getValue(), output.getAddress());
                                    tempUTXOPool.addUTXO(utxoKey, utxo); // Add the new UTXO to the pool
                                }
                            }
                        }
                    }
                }
                blockchain.getChain().clear();
                blockchain = tempBlockchain;
                for (Block block : blockchain.getChain()) {
                    processedBlocks.add(block.getBlockHash());
                    for (Transaction transactions : block.getBlockTransactions()) {
                        processedTransactions.add(transactions.getHash());
                    }
                }
                utxoPool.getUTXOMap().clear();
                utxoPool = tempUTXOPool;
            }
        } else {
            peerManager.requestBlockchainFromPeers();
        }
    }

    public Mempool getMempool() {
        return this.mempool;
    }

    public boolean isAddressValid(String address) {
        try {
            Base64.getDecoder().decode(address);
            return true;
        } catch (AddressFormatException e) {
            return false; // Base64 decoding issue
        }
    }

    public void setShouldInterrupt(boolean bool) {
        this.shouldInterrupt = bool;
    }

    public Blockchain getBlockchain() {
        return this.blockchain;
    }

    public Block createGenesisBlock(String senderAddress) {
        Input input = new Input("genesis", -1, null, userWallet.getPublicKey());
        Output output = new Output(25, senderAddress);
        ArrayList<Input> inputs = new ArrayList<Input>();
        inputs.add(input);
        ArrayList<Output> outputs = new ArrayList<Output>();
        outputs.add(output);
        Transaction genesisTransaction = new Transaction(inputs, outputs);
        ArrayList<Transaction> genesisList = new ArrayList<Transaction>();
        genesisList.add(genesisTransaction);
        Mining mining = new Mining("0", senderAddress, utxoPool, minerReward);
        Block genesisBlock = new Block(genesisList, mining);
        UTXOKey utxoKey = new UTXOKey(genesisTransaction.getHash(), 0);
        UTXO utxo = new UTXO(output.getValue(), output.getAddress());
        utxoPool.addUTXO(utxoKey, utxo);
        System.out.println("Genesis Block created.");

        return genesisBlock;
    }

    public boolean validateTransaction(Transaction transaction) {
        // Verifies the transaction signature's validity

        for (Input input : transaction.getInputs()) {
            PublicKey publicKey = input.getPublicKey();
            if (!input.verifySignature(publicKey, transaction.getTransactionString())) {
                return false; // If any input fails verification, the transaction is invalid
            }
        }
        return true; // If all inputs are valid, return true
    }

    public boolean validateBlock(Block block) {
        // Validate the block itself
        // For each transaction in the block, validate it, then update the UTXO pool and
        // mempool accordingly
        if (block.getBlockTransactions().size() == 1) {
            if (block.getBlockTransactions().get(0).getInputs().get(0).getTxId().equals("genesis")) {
                return true;
            }
        }

        for (Transaction transaction : block.getBlockTransactions()) {
            if (transaction.getInputs().get(0).getTxId().startsWith("reward")) {
                continue;
            }
            if (!validateTransaction(transaction)) {
                return false; // If any transaction is invalid, the block is invalid
            }
        }

        for (Transaction transaction : block.getBlockTransactions()) {
            if (transaction.getInputs().get(0).getTxId().startsWith("reward")) {
                String transactionHash = transaction.getHash();

                ArrayList<Output> outputs = transaction.getOutputs();
                for (int i = 0; i < outputs.size(); i++) {
                    Output output = outputs.get(i);
                    UTXOKey utxoKey = new UTXOKey(transactionHash, i); // Use the transaction hash and output
                                                                       // index
                                                                       // as the
                                                                       // key
                    UTXO utxo = new UTXO(output.getValue(), output.getAddress());
                    utxoPool.addUTXO(utxoKey, utxo); // Add the new UTXO to the pool
                }
                continue;
            }
            ArrayList<Input> inputs = transaction.getInputs();
            ArrayList<Output> outputs = transaction.getOutputs();
            for (Input input : inputs) {
                String txHash = input.getTxId();
                int index = input.getOutputIndex();
                UTXOKey keyToRemove = new UTXOKey(txHash, index);
                if (this.utxoPool.containsUTXOKey(keyToRemove)) {
                    this.utxoPool.removeFromUTXOPool(keyToRemove);
                } else {
                    // stop checking and make a new blockchain
                    System.out.println("Cannot find UTXO- either spent or never existed.");
                    return false;
                }
            }
            this.mempool.removeTransactionFromMempool(transaction);

            String transactionHash = transaction.getHash();

            for (int i = 0; i < outputs.size(); i++) {
                Output output = outputs.get(i);
                UTXOKey utxoKey = new UTXOKey(transactionHash, i); // Use the transaction hash and output
                                                                   // index
                                                                   // as the
                                                                   // key
                UTXO utxo = new UTXO(output.getValue(), output.getAddress());
                utxoPool.addUTXO(utxoKey, utxo); // Add the new UTXO to the pool
            }
        }

        return true;
    }

    public void startServer() {
        try {
            this.peerManager = new PeerManager(this, this.port);
            taskExecutor.submit(() -> peerManager.run());
            this.findBlockchain();
        } catch (InvalidBlockchainException e) {
            System.out.println(e);
        }

    }

    public void start() {
        taskExecutor.submit(this::startServer);
    }

    // Starts the mining process in a separate thread
    public void startMining(Wallet userWallet, String minerAddress, TextArea blockInfoTextArea, TextField userBalance) {
        taskExecutor.submit(() -> {
            while (!shouldInterrupt && !Thread.currentThread().isInterrupted()) {
                try {
                    if (blockchain.getChain().isEmpty()) {
                        Block genesisBlock = createGenesisBlock(minerAddress);
                        processedBlocks.add(genesisBlock.getBlockHash());
                        for (Transaction transaction : genesisBlock.getBlockTransactions()) {
                            processedTransactions.add(transaction.getHash());
                        }
                        blockchain.addBlock(genesisBlock);

                        UpdateBlockchain updateBlockchain = new UpdateBlockchain(blockchain);
                        utxoPool.getUTXOMap().forEach(
                                (key, value) -> System.out
                                        .println("UTXO Pool after block " + blockchain.getBlockHeight() + ": Address: "
                                                + value.getAddress() + ", Value: "
                                                + value.getValue()));
                        Platform.runLater(() -> {
                            double balance = userWallet.getBalance(utxoPool, walletServer, port);
                            userBalance.setText(String.valueOf(balance));
                        });
                        long timestamp = genesisBlock.getTimeStamp();
                        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                        String newTimestamp = formatter.format(new Date(timestamp));
                        String updatedBlockInfo = String.format(
                                "Block Number: %d\nPrev Block Hash: %s\nBlock Hash: %s\nMiner Address: %s\nBlock Timestamp: %s\nNumber of Block Transactions: %d",
                                genesisBlock.getBlockNumber(),
                                genesisBlock.getPreviousHash(),
                                genesisBlock.getBlockHash(),
                                genesisBlock.getMinerAddress(),
                                newTimestamp,
                                genesisBlock.getBlockTransactions().size());
                        Platform.runLater(() -> {
                            blockInfoTextArea.setText(updatedBlockInfo);
                        });
                        peerManager.broadcast(updateBlockchain);
                    }

                    String previousHash = blockchain.getChain().get((blockchain.getChain().size() - 1)).getBlockHash();
                    Mining mining = new Mining(previousHash, minerAddress, utxoPool, minerReward);
                    if (blockchain.getChain().size() % 1000 == 0) {
                        halveMinerReward();
                    }
                    // Retrieve transactions from the mempool
                    ArrayList<Transaction> mempoolTransactions = mining.getBlockTransactionsToBeHashed(mempool);
                    if (!mempoolTransactions.isEmpty() && mempoolTransactions.size() >= mining.getNumOfTransactions()) {
                        System.out.println("Mempool transactions fetched: " + mempoolTransactions);
                        Block minedBlock = mining.mineBlock(mempoolTransactions, this);
                        if (minedBlock != null) {
                            blockchain.addBlock(minedBlock);
                            minedBlock.setBlockNumber(blockchain.getBlockHeight());

                            System.out.println(
                                    "Block (with not just reward transaction): " + minedBlock.getBlockNumber()
                                            + " Hash: " + minedBlock.getBlockHash());
                            utxoPool.getUTXOMap().forEach(
                                    (key, value) -> System.out
                                            .println("UTXO Pool after block " + blockchain.getBlockHeight()
                                                    + ": Address: "
                                                    + value.getAddress() + ", Value: "
                                                    + value.getValue()));
                            Platform.runLater(() -> {
                                double balance = userWallet.getBalance(utxoPool, walletServer, port);
                                userBalance.setText(String.valueOf(balance));
                            });
                            System.out.println("Mempool after block : " + blockchain.getBlockHeight() + ": "
                                    + mempool.getTransactions());
                            peerManager.broadcast(minedBlock);
                            long timestamp = minedBlock.getTimeStamp();

                            SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                            String newTimestamp = formatter.format(new Date(timestamp));
                            String updatedBlockInfo = String.format(
                                    "Block Number: %d\nPrev Block Hash: %s\nBlock Hash: %s\nMiner Address: %s\nBlock Timestamp: %s\nNumber of Block Transactions: %d",
                                    minedBlock.getBlockNumber(),
                                    minedBlock.getPreviousHash(),
                                    minedBlock.getBlockHash(),
                                    minedBlock.getMinerAddress(),
                                    newTimestamp,
                                    minedBlock.getBlockTransactions().size());
                            Platform.runLater(() -> {
                                blockInfoTextArea.setText(updatedBlockInfo);

                            });
                        }
                    } else if (mempoolTransactions.size() < mining.getNumOfTransactions()) {
                        ArrayList<Transaction> rewardsList = new ArrayList<Transaction>();
                        Block rewardBlock = mining.mineBlock(rewardsList, this);
                        this.utxoPool = mining.getUTXOPool();
                        blockchain.addBlock(rewardBlock);
                        processedBlocks.add(rewardBlock.getBlockHash());
                        for (Transaction transaction : rewardBlock.getBlockTransactions()) {
                            processedTransactions.add(transaction.getHash());
                        }
                        rewardBlock.setBlockNumber(this.getBlockchain().getBlockHeight());
                        peerManager.broadcast(rewardBlock);
                        System.out.println(
                                "Block: " + blockchain.getBlockHeight() + " Hash: " + rewardBlock.getBlockHash());

                        utxoPool.getUTXOMap().forEach(
                                (key, value) -> System.out
                                        .println("UTXO Pool after block " + blockchain.getBlockHeight() + ": Address: "
                                                + value.getAddress() + ", Value: "
                                                + value.getValue()));
                        Platform.runLater(() -> {
                            double balance = userWallet.getBalance(utxoPool, walletServer, port);
                            userBalance.setText(String.valueOf(balance));
                        });
                        long timestamp = rewardBlock.getTimeStamp();

                        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                        String newTimestamp = formatter.format(new Date(timestamp));
                        Platform.runLater(() -> {
                            String updatedBlockInfo = String.format(
                                    "Block Number: %d\nPrev Block Hash: %s\nBlock Hash: %s\nMiner Address: %s\nBlock Timestamp: %s\nNumber of Block Transactions: %d",
                                    rewardBlock.getBlockNumber(),
                                    rewardBlock.getPreviousHash(),
                                    rewardBlock.getBlockHash(),
                                    rewardBlock.getMinerAddress(),
                                    newTimestamp,
                                    rewardBlock.getBlockTransactions().size());
                            blockInfoTextArea.setText(updatedBlockInfo);
                        });
                    }
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.out.println("Mining thread interrupted");
                    break;
                }
            }
        });
    }

    public double getMinerReward() {
        return this.minerReward;
    }

    public UTXOPool getUTXOPool() {
        return this.utxoPool;
    }

    public WalletServer getWalletServer() {
        return this.walletServer;
    }

    public void registerOrVerifyUser(Label messageLabel, Button loginButton,
            TextArea blockTextArea) throws NoSuchAlgorithmException {
        if (walletServer.getWallets(port) != null) {

            this.userWallets = walletServer.getWallets(port);

            // Set the userWallet to a random wallet from the list
            loginButton.setDisable(true);
        } else if (walletServer.getWallets(port) == null) {

            KeyPairs senderKeyPairs = new KeyPairs();
            PublicKey senderPublicKey = senderKeyPairs.getPublicKey();
            PrivateKey senderPrivateKey = senderKeyPairs.getPrivateKey();

            this.userWallet.setPrivateKey(senderPrivateKey);
            this.userWallet.setPublicKey(senderPublicKey);
            String senderAddress = userWallet.generateAddress();

            this.userWallet.setAddress(senderAddress);
            walletServer.addWallet(port, userWallet);
            this.userWallets = walletServer.getWallets(port);

            loginButton.setDisable(true);
            UpdateBlockchain updateBlockchain = new UpdateBlockchain(blockchain);
            peerManager.broadcast(updateBlockchain);
        }
    }

    public ArrayList<Wallet> getUserWallets() {
        return this.userWallets;
    }

    public void performTransaction(String recipientAddress, double amount, boolean useNewAddress, boolean highPriority)
            throws NoSuchAlgorithmException {
        Random random = new Random();
        if (useNewAddress == true) {
            KeyPairs senderKeyPairs = new KeyPairs();
            PublicKey senderPublicKey = senderKeyPairs.getPublicKey();
            PrivateKey senderPrivateKey = senderKeyPairs.getPrivateKey();
            this.userWallet = new Wallet(senderPrivateKey, senderPublicKey);
            userWallet.generateAddress();
            walletServer.addWallet(port, userWallet);
        } else {
            int randomIndex = random.nextInt(this.userWallets.size());
            this.userWallet = this.userWallets.get(randomIndex);
        }

        Transaction transaction = userWallet.performTransaction(recipientAddress, amount, mempool, utxoPool,
                walletServer, port, highPriority);
        if (transaction != null) {
            System.out.println("Valid transaction, checked and verified: " + transaction);
            mempool.addTransactionToMempool(transaction);
            peerManager.broadcast(transaction);
        } else {
            System.out.println("Invalid transaction.");
        }

    }

    public void initializeBlockchainComponents() {
        this.blockchain = new Blockchain();
        this.mempool = new Mempool();
        this.utxoPool = new UTXOPool();

        loadMappings();
    }

    private void loadMappings() {
        File walletFile = new File("walletServer.ser");
        File utxoFile = new File("utxoPool.ser");
        if (walletFile.exists()) {
            walletServer = new WalletServer(FileSaver.loadHashMap("walletServer.ser"));
            if (walletServer.getWallets(port) != null) {
                userWallets = walletServer.getWallets(port);

            }
        } else {
            walletServer = new WalletServer();
        }
        if (utxoFile.exists()) {
            utxoPool = new UTXOPool(FileSaver.loadHashMap("utxoPool.ser"));
        } else {
            utxoPool = new UTXOPool();
        }
    }

    public void saveMappingsOnShutdown() {
        File file = new File("blockchain.ser");
        if (file.exists()) {

            Blockchain loadBlockchain = FileSaver.loadBlockchain("blockchain.ser");
            if (loadBlockchain != null) {
                if (loadBlockchain.getBlockHeight() < blockchain.getBlockHeight()) {

                    FileSaver.saveHashMap(walletServer.getHashMap(), "walletServer.ser");
                    FileSaver.saveHashMap(utxoPool.getUTXOMap(), "utxoPool.ser");
                    FileSaver.saveBlockchain(blockchain, "blockchain.ser");

                    UpdateBlockchain updateBlockchain = new UpdateBlockchain(blockchain);
                    System.out.println("Broadcasting changes before shutting down.");
                    peerManager.broadcast(updateBlockchain);
                }
            }
        } else {
            FileSaver.saveHashMap(walletServer.getHashMap(), "walletServer.ser");
            FileSaver.saveHashMap(utxoPool.getUTXOMap(), "utxoPool.ser");
            FileSaver.saveBlockchain(blockchain, "blockchain.ser");

        }
    }

    public void setUTXOPool(UTXOPool UTXOPool) {
        this.utxoPool.emptyUTXOPool();
        this.utxoPool = UTXOPool;
    }
}