package blockchain;

import java.io.Serializable;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.bitcoinj.core.Base58;
import org.bouncycastle.crypto.digests.RIPEMD160Digest;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

public class Wallet implements Serializable {
    private static final long serialVersionUID = 2317709595944805708L;

    private PrivateKey privateKey;
    private String address;
    private PublicKey publicKey;

    public Wallet() {
    }

    public Wallet(PrivateKey privateKey, PublicKey publicKey) {
        this.privateKey = privateKey;
        this.publicKey = publicKey;
    }

    public PublicKey getPublicKey() {
        return this.publicKey;
    }

    public void setPublicKey(PublicKey publicKey) {
        this.publicKey = publicKey;
    }

    public void setPrivateKey(PrivateKey privateKey) {
        this.privateKey = privateKey;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getAddress() {
        return this.address;
    }

    public Transaction performTransaction(String recipientAddress, double amount, Mempool mempool, UTXOPool utxoPool,
            WalletServer walletServer, int port, boolean highPriority)
            throws NoSuchAlgorithmException {
        double userBalance = 0;
        ArrayList<String> userAddresses = new ArrayList<>();
        ArrayList<Wallet> userWallets = walletServer.getWallets(port);
        ArrayList<Input> inputsForTransaction = new ArrayList<>();
        ArrayList<Output> outputsForTransaction = new ArrayList<>();
        for (Wallet wallet : userWallets) {
            userAddresses.add(wallet.getAddress());
        }

        Set<UTXOKey> usedUTXOsInMempool = new HashSet<>();
        List<Map.Entry<UTXOKey, UTXO>> sortedUTXOs = utxoPool.getUTXOMap().entrySet()
                .stream()
                .filter(entry -> !usedUTXOsInMempool.contains(entry.getKey())
                        && userAddresses.contains(entry.getValue().getAddress())) // here
                .sorted(Map.Entry.comparingByValue(Comparator.comparingDouble(UTXO::getValue).reversed()))
                .collect(Collectors.toList());

        for (Transaction mempoolTx : mempool.getTransactions()) {
            for (Input input : mempoolTx.getInputs()) {
                UTXOKey key = new UTXOKey(input.getTxId(), input.getOutputIndex());
                usedUTXOsInMempool.add(key);
            }
        }

        // collect inputs to use for the transaction

        // we want to collect all the UTXO addresses = to useraddress
        // we collect until >= amount
        // if < amount, then invalid transaction
        // we add all <UTXOKey, UTXO> that are related to the input list for the
        // transaction
        // perform checks to make sure user can actually make this payment
        for (HashMap.Entry<UTXOKey, UTXO> entry : sortedUTXOs) {
            if (userBalance > amount) {
                break; // Stop if we've collected enough
            }
            UTXOKey utxoKey = entry.getKey();
            UTXO utxo = entry.getValue();
            if (!usedUTXOsInMempool.contains(utxoKey) && userAddresses.contains(utxo.getAddress())) {
                userBalance += utxo.getValue();
                inputsForTransaction.add(new Input(utxoKey.getTxHash(), utxoKey.getIndex(), null, publicKey));
            }
        }
        // Check if enough balance was collected
        if (userBalance < amount) {
            System.out.println("Not enough balance.");
            return null;
        }

        // Add outputs
        outputsForTransaction.add(new Output(amount, recipientAddress)); // Amount to recipient (0th index)

        Transaction incompleteTransaction = new Transaction(inputsForTransaction, outputsForTransaction);
        double priorityFee = 0.001;
        if (highPriority) {
            priorityFee = 0.005;
        }
        double transactionFee = priorityFee
                * estimateTransactionSize(inputsForTransaction.size(), outputsForTransaction.size() + 1);
        String userAddress = generateAddress();
        incompleteTransaction.calculateAndAddChangeOutput(utxoPool, transactionFee, userAddress); // Amount back to
                                                                                                  // sender (Index 1)

        byte[] digitalSignature = incompleteTransaction.generateDigitalSignature(privateKey);

        if (incompleteTransaction.verifyTransactionSignature(publicKey)) {
            System.out.println("Transaction has a proper signature.");
            for (Input input : inputsForTransaction) {
                input.setDigitalSignature(digitalSignature);
            }

        } else {
            return null;
        }

        return incompleteTransaction;
    }

    public double estimateTransactionSize(double numberOfInputs, double numberOfOutputs) {
        // these numbers are based off bitcoin
        double inputSize = 148; // Average size in bytes
        double outputSize = 34; // Average size in bytes
        double overhead = 10; // Average overhead in bytes
        double calculation = ((numberOfInputs * inputSize) + (numberOfOutputs * outputSize) + overhead);
        return calculation;
        // will be something like 0.226
    }

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    public String generateAddress() throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(publicKey.getEncoded());

        RIPEMD160Digest ripemd160 = new RIPEMD160Digest();
        byte[] ripemd160Hash = new byte[ripemd160.getDigestSize()];
        ripemd160.update(hash, 0, hash.length);
        ripemd160.doFinal(ripemd160Hash, 0);

        byte[] networkHash = new byte[ripemd160Hash.length + 1];
        System.arraycopy(ripemd160Hash, 0, networkHash, 1, ripemd160Hash.length);
        networkHash[0] = 0;

        byte[] firstSha = digest.digest(networkHash);
        byte[] secondSha = digest.digest(firstSha);

        byte[] binaryAddress = new byte[networkHash.length + 4];
        System.arraycopy(networkHash, 0, binaryAddress, 0, networkHash.length);
        System.arraycopy(secondSha, 0, binaryAddress, networkHash.length, 4);
        String base58Address = Base58.encode(binaryAddress);

        setAddress(base58Address);

        return base58Address;
    }

    public double getBalance(UTXOPool utxoPool, WalletServer walletServer, int port) {
        ArrayList<String> userAddresses = new ArrayList<>();
        ArrayList<Wallet> userWallets = walletServer.getWallets(port);
        for (Wallet wallet : userWallets) {
            userAddresses.add(wallet.getAddress());
        }

        double balance = 0;
        HashMap<UTXOKey, UTXO> allUTXOS = utxoPool.getUTXOMap();
        for (UTXO utxo : allUTXOS.values()) {
            if (userAddresses.contains(utxo.getAddress())) {
                balance += utxo.getValue();
            }
        }
        return balance;
    }

    public List<String> getTransactions(Blockchain blockchain, WalletServer walletServer, int port) {
        Map<String, String> transactionMap = new HashMap<>();
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        ArrayList<String> userAddresses = new ArrayList<>();
        ArrayList<Wallet> userWallets = walletServer.getWallets(port);
        for (Wallet wallet : userWallets) {
            userAddresses.add(wallet.getAddress());
        }
        for (Block block : blockchain.getChain()) {
            long timestamp = block.getTimeStamp();
            String newTimestamp = formatter.format(new Date(timestamp));
            for (Transaction transaction : block.getBlockTransactions()) {
                String transactionHash = transaction.getHash();
                for (Output output : transaction.getOutputs()) {
                    if (userAddresses.contains(output.getAddress())) {
                        double amount = output.getValue();
                        String transactionKey = transactionHash + "-" + block.getBlockNumber(); // Unique key
                        String transactionInfo = String.format(
                                "Block: %d Transaction Hash: %s Address: %s End Amount Received/Returned: %.2f timestamp: %s",
                                block.getBlockNumber(), transactionHash, output.getAddress(), amount, newTimestamp);
                        transactionMap.put(transactionKey, transactionInfo); // Only stores unique transactions
                    }
                }
            }
        }

        // Sort by block number using a tree map if order is necessary or just extract
        // values if not
        List<String> sortedTransactions = new ArrayList<>(transactionMap.values());
        sortedTransactions.sort((a, b) -> {
            String[] partsA = a.split(" ");
            String[] partsB = b.split(" ");
            return Integer.compare(Integer.parseInt(partsA[1]), Integer.parseInt(partsB[1]));
        });
        Collections.reverse(sortedTransactions);

        return sortedTransactions;
    }

    public List<String> getAllTransactions(Blockchain blockchain) {
        Map<String, String> transactionMap = new HashMap<>();
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        for (Block block : blockchain.getChain()) {
            long timestamp = block.getTimeStamp();
            String newTimestamp = formatter.format(new Date(timestamp));
            for (Transaction transaction : block.getBlockTransactions()) {
                String transactionHash = transaction.getHash();
                for (Output output : transaction.getOutputs()) {
                    double amount = output.getValue();
                    String transactionKey = transactionHash + "-" + block.getBlockNumber(); // Unique key
                    String transactionInfo = String.format(
                            "Block: %d Transaction Hash: %s Address: %s End Amount Received/Returned: %.2f timestamp: %s",
                            block.getBlockNumber(), transactionHash, output.getAddress(), amount, newTimestamp);
                    transactionMap.put(transactionKey, transactionInfo); // Only stores unique transactions
                }
            }
        }

        // Sort by block number using a tree map if order is necessary or just extract
        // values if not
        List<String> sortedTransactions = new ArrayList<>(transactionMap.values());
        sortedTransactions.sort((a, b) -> {
            String[] partsA = a.split(" ");
            String[] partsB = b.split(" ");
            return Integer.compare(Integer.parseInt(partsA[1]), Integer.parseInt(partsB[1]));
        });
        Collections.reverse(sortedTransactions);

        return sortedTransactions;
    }
}
