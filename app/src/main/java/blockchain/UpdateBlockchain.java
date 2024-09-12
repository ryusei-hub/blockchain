package blockchain;

import java.io.Serializable;

public class UpdateBlockchain implements Serializable {
    private static final long serialVersionUID = 2317709595944805708L;

    public final Blockchain blockchain;

    public UpdateBlockchain(Blockchain blockchain) {
        this.blockchain = blockchain;
    }
}
