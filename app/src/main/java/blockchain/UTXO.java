package blockchain;

import java.io.Serializable;

public class UTXO implements Serializable {
    private static final long serialVersionUID = 2317709595944805708L;

    private double value; // Output value
    private String address; // Address in the UTXO

    public UTXO(double value, String address) {
        this.value = value;
        this.address = address;
    }

    public double getValue() {
        return value;
    }

    public String getAddress() {
        return address;
    }
}
