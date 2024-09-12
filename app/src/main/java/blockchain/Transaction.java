package blockchain;

import java.io.Serializable;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.util.ArrayList;

public class Transaction implements Serializable {
    private static final long serialVersionUID = 2317709595944805708L;

    private ArrayList<Input> transactionInputs;
    private ArrayList<Output> transactionOutputs;
    private byte[] digitalSignature;
    private String transactionHash;
    private double change;
    private double fee;

    public Transaction(ArrayList<Input> inputs, ArrayList<Output> transactionOutputs) {
        this.transactionInputs = inputs;
        this.transactionOutputs = transactionOutputs;
        this.transactionHash = calculateTransactionHash();
    }

    // Calculate and return the transaction fee
    public double getChange() {
        return change;
    }

    public double getFee() {
        return fee;
    }

    public void setFee(double fee) {
        this.fee = fee;
    }

    public byte[] getDigitalSignature() {
        return this.digitalSignature;
    }
    // This method now calculates the change and indicates where you should add it
    // as an output

    public void calculateAndAddChangeOutput(UTXOPool utxoPool, double transactionFee,
            String senderAddress) {
        boolean isDone = false;
        double inputSum = 0;
        double outputSum = 0;

        for (Input input : transactionInputs) {
            UTXOKey utxoKeyToFind = new UTXOKey(input.getTxId(), input.getOutputIndex());
            UTXO utxo = utxoPool.getUTXO(utxoKeyToFind);
            if (utxo != null) {
                inputSum += utxo.getValue();
            }
        }

        for (Output output : transactionOutputs) {
            outputSum += output.getValue();
        }

        while (isDone == false) {
            // Calculate change
            double change = inputSum - outputSum - transactionFee;
            // Ensure change is not negative; if it is, there might be an error in fee
            // calculation or input selection
            // Check if the change is negative
            if (change < 0) {
                transactionFee /= 10;
            }

            // If change is non-negative, add an output to send change back to the sender
            if (change > 0) {
                isDone = true;
                System.out.println("Transaction Fee: " + transactionFee);
                Output outputBackToSender = new Output(change, senderAddress);
                transactionOutputs.add(outputBackToSender);
            }

            if (change == 0) {
                isDone = true;
                setFee(0);
                System.out.println("Transaction Fee: 0");
            } else {
                setFee(transactionFee);
            }
        }

    }

    public ArrayList<Output> getOutputs() {
        return this.transactionOutputs;
    }

    public ArrayList<Input> getInputs() {
        return this.transactionInputs;
    }

    private String calculateTransactionHash() {
        try {
            // Concatenate relevant transaction data
            String transactionData = this.getTransactionString();

            // Compute the hash using SHA-256
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(transactionData.getBytes());

            // Convert byte array to hexadecimal string
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public String getTransactionString() {
        String transactionInputData = "";
        String transactionOutputData = "";
        if (transactionInputs != null) {
            for (int i = 0; i < transactionInputs.size(); i++) {
                String transactionString = "";
                transactionString = (transactionInputs.get(i)).getInputDataAsString();
                transactionInputData += transactionString;
            }
        }
        for (int i = 0; i < transactionOutputs.size(); i++) {
            String transactionString = "";
            transactionString = (transactionOutputs.get(i)).getOutputDataAsString();
            transactionOutputData += transactionString;
        }
        String toReturn = transactionInputData + transactionOutputData;
        return toReturn;
    }

    public String getHash() {
        return this.transactionHash;
    }

    public void setHash(String hash) {
        this.transactionHash = hash;
    }

    // Sign the input using the private key
    public byte[] generateDigitalSignature(PrivateKey privateKey) {
        // Construct the data that needs to be signed
        String transactionData = getTransactionString();

        // Use the private key to sign the data
        try {
            Signature signature = Signature.getInstance("SHA256withECDSA");
            signature.initSign(privateKey);
            signature.update(transactionData.getBytes());
            digitalSignature = signature.sign();
            return digitalSignature;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public boolean verifyTransactionSignature(PublicKey senderKey) {
        try {
            // Construct the input data that needs to be verified
            String stringToVerify = getTransactionString();

            // Use Signature class to verify the input data with the signature
            Signature signature = Signature.getInstance("SHA256withECDSA");
            signature.initVerify(senderKey);
            signature.update(stringToVerify.getBytes());

            // Verify the signature
            return signature.verify(this.digitalSignature);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

}
