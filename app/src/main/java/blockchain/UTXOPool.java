package blockchain;

import java.util.ArrayList;
import java.util.HashMap;

public class UTXOPool {
    private HashMap<UTXOKey, UTXO> utxoMap;

    public UTXOPool() {
        utxoMap = new HashMap<>();
    }

    public UTXOPool(HashMap<UTXOKey, UTXO> utxoMap) {
        this.utxoMap = utxoMap;
    }

    public void addUTXO(UTXOKey utxoKey, UTXO utxo) {
        utxoMap.put(utxoKey, utxo);
    }

    public boolean containsUTXOKey(UTXOKey utxoKey) {
        if (utxoMap.containsKey(utxoKey)) {
            return true;
        } else {
            return false;
        }
    }

    public UTXO getUTXO(UTXOKey utxoKey) {
        UTXO utxo = this.utxoMap.get(utxoKey);
        return utxo;
    }

    public void emptyUTXOPool() {
        this.utxoMap.clear();
    }

    public void removeFromUTXOPool(UTXOKey key) {
        this.utxoMap.remove(key);
    }

    public HashMap<UTXOKey, UTXO> getUTXOMap() {
        return this.utxoMap;
    }

    public ArrayList<UTXO> returnAllUTXOS() {
        ArrayList<UTXO> utxos = new ArrayList<UTXO>();
        for (UTXO utxo : utxoMap.values()) {
            utxos.add(utxo);
        }
        return utxos;
    }
}