package blockchain;

import java.io.Serializable;

public class Output implements Serializable {
    private static final long serialVersionUID = 2317709595944805708L;

    private double outputValue;
    private String OutputString;
    private String address;

    public Output(double value, String receiveraddress) {
        this.outputValue = value;
        this.address = receiveraddress;
    }

    public String getAddress() {
        return this.address;
    }

    public double getValue() {
        return this.outputValue;
    }

    public String getOutputDataAsString() {
        OutputString = Double.toString(outputValue);
        return OutputString;
    }

}
