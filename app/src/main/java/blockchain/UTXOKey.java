package blockchain;

import java.io.Serializable;
import java.util.Objects;

public class UTXOKey implements Serializable {
    private static final long serialVersionUID = 2317709595944805708L;

    private String txHash;
    private int index;

    public UTXOKey(String txHash, int index) {
        this.txHash = txHash;
        this.index = index;
    }

    public String getTxHash() {
        return this.txHash;
    }

    public int getIndex() {
        return this.index;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true; // Check for self comparison
        if (o == null || getClass() != o.getClass())
            return false; // Check for null and ensure exact same class
        UTXOKey utxoKey = (UTXOKey) o;
        return index == utxoKey.index && Objects.equals(txHash, utxoKey.txHash);
    }

    @Override
    public int hashCode() {
        return Objects.hash(txHash, index); // Generate a hash code using txHash and index
    }
}
