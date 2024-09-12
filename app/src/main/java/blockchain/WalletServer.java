package blockchain;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;

public class WalletServer implements Serializable {
    private static final long serialVersionUID = 2317709595944805708L;

    private HashMap<Integer, ArrayList<Wallet>> portToWalletMap = new HashMap<>();

    public WalletServer() {
        this.portToWalletMap = new HashMap<Integer, ArrayList<Wallet>>();
    }

    public WalletServer(HashMap<Integer, ArrayList<Wallet>> portToWalletMap) {
        this.portToWalletMap = portToWalletMap;
    }

    public void addWallet(int port, Wallet userWallet) {
        if (portToWalletMap.get(port) != null) {
            ArrayList<Wallet> userWallets = portToWalletMap.get(port);
            userWallets.add(userWallet);
            portToWalletMap.remove(port);
            portToWalletMap.put(port, userWallets);
        } else {
            ArrayList<Wallet> userWallets = new ArrayList<>();
            userWallets.add(userWallet);
            portToWalletMap.put(port, userWallets);
        }

        System.out.println("Wallet created and assigned to port: " + port);
    }

    public HashMap<Integer, ArrayList<Wallet>> getHashMap() {
        return this.portToWalletMap;
    }

    public ArrayList<Wallet> getWallets(int port) {
        return portToWalletMap.get(port);
    }
}
