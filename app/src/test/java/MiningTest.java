import static org.junit.jupiter.api.Assertions.*;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import blockchain.Input;
import blockchain.Mining;
import blockchain.Node;
import blockchain.Output;
import blockchain.Transaction;
import blockchain.UTXOPool;

class MiningTest {
    private Node node;
    private Mining mining;
    private UTXOPool utxoPool;
    private String previousHash = "0000000000000000000";
    private String minerAddress = "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa";
    private double minerReward = 2;
    private KeyPair keyPair;

    @BeforeEach
    void setUp() throws NoSuchAlgorithmException {
        utxoPool = new UTXOPool();
        node = new Node(8080);
        mining = new Mining(previousHash, minerAddress, utxoPool, minerReward);
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("EC");
        keyPairGenerator.initialize(256); // Example initialization, could be adjusted based on actual security
                                          // requirements
        keyPair = keyPairGenerator.generateKeyPair();
    }

    @Test
    void testMerkleRootHashSingleTransaction() {
        ArrayList<Input> inputs = new ArrayList<>();
        inputs.add(new Input("txid1", 0, new byte[] {}, keyPair.getPublic())); // Assume digitalSignature initially
                                                                               // empty
        ArrayList<Output> outputs = new ArrayList<>();
        outputs.add(new Output(50, "address1"));
        Transaction transaction = new Transaction(inputs, outputs);

        ArrayList<Transaction> transactions = new ArrayList<>();
        transactions.add(transaction);
        String merkleRoot = mining.merkleRootHash(transactions);
        assertNotNull(merkleRoot);
        assertFalse(merkleRoot.isEmpty());
    }

    @Test
    void testMerkleRootHashMultipleTransactions() {
        ArrayList<Transaction> transactions = new ArrayList<>();
        transactions.add(new Transaction(new ArrayList<Input>() {
            {
                add(new Input("txid2", 1, new byte[] {}, keyPair.getPublic()));
            }
        }, new ArrayList<Output>() {
            {
                add(new Output(75, "address2"));
            }
        }));
        transactions.add(new Transaction(new ArrayList<Input>() {
            {
                add(new Input("txid3", 2, new byte[] {}, keyPair.getPublic()));
            }
        }, new ArrayList<Output>() {
            {
                add(new Output(100, "address3"));
            }
        }));

        String merkleRoot = mining.merkleRootHash(transactions);
        assertNotNull(merkleRoot);
        assertFalse(merkleRoot.isEmpty());
    }

    @Test
    void testCalculateBlockHash() {
        ArrayList<Input> inputs = new ArrayList<>();
        inputs.add(new Input("txid4", 3, new byte[] {}, keyPair.getPublic()));
        ArrayList<Output> outputs = new ArrayList<>();
        outputs.add(new Output(85, "address4"));
        Transaction transaction = new Transaction(inputs, outputs);

        ArrayList<Transaction> transactions = new ArrayList<>();
        transactions.add(transaction);
        String merkleRoot = mining.merkleRootHash(transactions);
        String blockHash = mining.calculateBlockHash(previousHash, System.currentTimeMillis(), merkleRoot);
        assertNotNull(blockHash);
        assertTrue(blockHash.startsWith("000"));
    }

    @Test
    void testHalveMinerReward() {
        node.halveMinerReward();
        assertEquals(1, node.getMinerReward());
    }

    @Test
    void testGetNumOfTransactions() {
        assertEquals(2, mining.getNumOfTransactions());
    }
}
