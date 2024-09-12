package blockchain;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;

public class FileSaver {

    public static <K, V> void saveHashMap(HashMap<K, V> hashMap, String fileName) {
        try (FileOutputStream fileOut = new FileOutputStream(fileName);
                ObjectOutputStream objectOut = new ObjectOutputStream(fileOut)) {
            objectOut.writeObject(hashMap);
            System.out.println("HashMap saved to " + fileName);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @SuppressWarnings("unchecked")
    public static <K, V> HashMap<K, V> loadHashMap(String fileName) {
        HashMap<K, V> hashMap = new HashMap<>();
        try (FileInputStream fileIn = new FileInputStream(fileName);
                ObjectInputStream objectIn = new ObjectInputStream(fileIn)) {
            Object obj = objectIn.readObject();
            if (obj instanceof HashMap<?, ?>) {
                hashMap = (HashMap<K, V>) obj;
            } else {
                System.err.println("Error: Loaded object is not of type HashMap<K, V>");
            }
            System.out.println("HashMap loaded from " + fileName);
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
        return hashMap;
    }

    public static Blockchain loadBlockchain(String filename) {
        Blockchain blockchain = new Blockchain();
        try (FileInputStream fileIn = new FileInputStream(filename);
                ObjectInputStream in = new ObjectInputStream(fileIn)) {
            blockchain = (Blockchain) in.readObject();
            System.out.println("Blockchain loaded from " + filename);
        } catch (IOException i) {
            i.printStackTrace();
            return null;
        } catch (ClassNotFoundException c) {
            System.out.println("Blockchain class not found");
            c.printStackTrace();
            return null;
        }
        return blockchain;
    }

    public static void saveBlockchain(Blockchain blockchain, String filename) {
        try (FileOutputStream fileOut = new FileOutputStream(filename);
                ObjectOutputStream out = new ObjectOutputStream(fileOut)) {
            out.writeObject(blockchain);
            System.out.println("Blockchain saved to " + filename);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
