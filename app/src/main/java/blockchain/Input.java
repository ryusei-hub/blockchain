package blockchain;

import java.io.Serializable;
import java.security.PublicKey;
import java.security.Signature;

public class Input implements Serializable {
    private static final long serialVersionUID = 2317709595944805708L;

    private String transactionId;
    private int outputIndex;
    private byte[] digitalSignature; // This will store the actual signature bytes
    private PublicKey publicKey;

    public Input(String prevTxId, int outputIndex, byte[] digitalSignature, PublicKey publicKey) {
        this.transactionId = prevTxId;
        this.outputIndex = outputIndex;
        this.digitalSignature = digitalSignature;
        this.publicKey = publicKey;
    }

    public String getTxId() {
        return this.transactionId;
    }

    public PublicKey getPublicKey() {
        return this.publicKey;
    }

    public void setPublicKey(PublicKey publicKey) {
        this.publicKey = publicKey;
    }

    public int getOutputIndex() {
        return this.outputIndex;
    }

    public void setDigitalSignature(byte[] digitalSignature) {
        this.digitalSignature = digitalSignature;
    }

    public byte[] getDigitalSignature() {
        return this.digitalSignature;
    }

    public String getInputDataAsString() {
        return this.transactionId + Integer.toString(this.outputIndex);
    }

    public boolean verifySignature(PublicKey publicKey, String transactionString) {
        try {
            // Prepare a Signature object for verification
            Signature signature = Signature.getInstance("SHA256withECDSA");
            signature.initVerify(publicKey);
            signature.update(transactionString.getBytes()); // Update the signature with the original data

            // Verify the signature directly with the binary digital signature
            return signature.verify(this.digitalSignature);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

}
