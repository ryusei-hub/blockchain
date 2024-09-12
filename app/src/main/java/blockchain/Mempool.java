package blockchain;

import java.util.PriorityQueue;

public class Mempool {
    private PriorityQueue<Transaction> storedTransactions;

    public Mempool() {
        this.storedTransactions = new PriorityQueue<>((t1, t2) -> Double.compare(t2.getFee(), t1.getFee()));
    }

    public void addTransactionToMempool(Transaction transaction) {
        storedTransactions.add(transaction);
    }

    public void removeTransactionFromMempool(Transaction transaction) {
        storedTransactions.remove(transaction);
    }

    public PriorityQueue<Transaction> getTransactions() {
        return this.storedTransactions;
    }

}
