package blockchain;

import java.io.Serializable;
import java.util.ArrayList;

public class Blockchain implements Serializable {
    private static final long serialVersionUID = 2317709595944805708L;

    private ArrayList<Block> chain;
    private int blockHeight;

    public Blockchain() {
        this.chain = new ArrayList<Block>();
        blockHeight = 0;
    }

    public Blockchain(ArrayList<Block> chain) {
        this.chain = chain;
    }

    public ArrayList<Block> getChain() {
        return this.chain;
    }

    public void addBlock(Block block) {
        int index = this.chain.size();
        chain.add(index, block);
        blockHeight += 1;
    }

    public void replaceChain(Blockchain newChain) {
        this.chain = newChain.getChain();
        blockHeight = newChain.getChain().size();
    }

    public int getBlockHeight() {
        return this.blockHeight;
    }
}
