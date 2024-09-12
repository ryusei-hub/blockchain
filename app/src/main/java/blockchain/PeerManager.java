package blockchain;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class PeerManager implements Runnable {
    private Node node;
    private int port;
    private Map<String, ConnectionPair> connections = new ConcurrentHashMap<>();
    private ExecutorService executorService = Executors.newCachedThreadPool();
    private ScheduledExecutorService scheduledExecutor = Executors.newSingleThreadScheduledExecutor();
    private ServerSocket serverSocket;
    private Set<String> connectedEndpoints = ConcurrentHashMap.newKeySet();

    public PeerManager(Node node, int port) {
        this.node = node;
        this.port = port;
        try {
            serverSocket = new ServerSocket(this.port);
        } catch (IOException e) {
            System.err.println("Could not start server on port " + this.port + ": " + e.getMessage());
        }
    }

    public void connectToPeer() {
        String host = "localhost";
        try {
            for (int targetPort = 8080; targetPort <= 8089; targetPort++) {
                if (targetPort != this.port) {
                    String endpoint = host + ":" + targetPort;
                    if (!connectedEndpoints.contains(endpoint)) {
                        try {
                            Socket socket = new Socket();
                            socket.connect(new InetSocketAddress(host, targetPort), 5000);
                            System.out.println("Connected to " + endpoint);
                            handleNewConnection(socket, endpoint);
                        } catch (IOException e) {
                            // System.err.println("Failed to connect to " + endpoint + ": " +
                            // e.getMessage());
                        }
                    }
                }
            }
            Thread.sleep(10000);
        } catch (InterruptedException ie) {
            System.err.println("Thread interrupted: " + ie.getMessage());
            Thread.currentThread().interrupt();
            return;
        } catch (Exception e) {
            System.err.println("An error occurred during the connection loop: " + e.getMessage());
        }
    }

    @Override
    public void run() {
        connectToPeer();
        scheduledExecutor.scheduleAtFixedRate(this::requestBlockchainFromPeers, 0, 10, TimeUnit.SECONDS);
        executorService.submit(this::listenForConnections);
    }

    private void listenForConnections() {
        System.out.println("PeerManager listening on port " + this.port);
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Socket socket = serverSocket.accept();
                String endpoint = socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
                if (!connectedEndpoints.contains(endpoint)) {
                    handleNewConnection(socket, endpoint);
                }

            } catch (IOException e) {
                System.err.println("Error accepting connection: " + e.getMessage());
            }
        }
    }

    private void handleNewConnection(Socket socket, String endpoint) {
        try {
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            InputStream inStream = socket.getInputStream();
            out.flush();
            ObjectInputStream in = new ObjectInputStream(inStream);

            connections.put(endpoint, new ConnectionPair(socket, out, in));
            connectedEndpoints.add(endpoint);
            System.out.println("New connection established with " + endpoint);

            executorService.submit(() -> manageCommunication(socket, endpoint));
        } catch (IOException e) {
            System.err.println("Failed to set up connection streams: " + e.getMessage());
            closeSocket(socket);
        }
    }

    private void manageCommunication(Socket socket, String endpoint) {
        try {
            ConnectionPair pair = connections.get(endpoint);
            ObjectInputStream in = pair.getInputStream();
            ObjectOutputStream out = pair.getOutputStream();

            while (!socket.isClosed() && socket.isConnected()) {
                Object data = in.readObject();
                processReceivedObject(out, data);
            }
        } catch (EOFException e) {
            System.out.println("Peer disconnected: " + socket.getPort());
        } catch (IOException | ClassNotFoundException | InvalidBlockchainException e) {
            // System.err.println("Error during communication with " + socket.getPort() + ":
            // " + e.getMessage());
        } finally {
            closeSocket(socket);
            connections.remove(endpoint);
            connectedEndpoints.remove(endpoint);
        }
    }

    public void broadcast(Object object) {
        connections.forEach((endpoint, pair) -> executorService.submit(() -> {
            if (!pair.socket.isClosed()) {
                try {
                    pair.getOutputStream().writeObject(object);
                    pair.getOutputStream().flush();
                    System.out.println("Successfully broadcasted update to peer: " + endpoint);
                } catch (IOException e) {
                    System.err.println("Failed to broadcast update to peer " + endpoint + ": " + e.getMessage());
                    closeSocket(pair.socket); // Close socket on failure to prevent resource leak
                }
            }
        }));
    }

    private void closeSocket(Socket socket) {
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
                System.out.println("Closed connection for socket: " + socket);
            } catch (IOException e) {
                System.err.println("Failed to close socket: " + e.getMessage());
            }
        }
    }

    public void shutdown() {
        try {
            scheduledExecutor.shutdownNow();
            executorService.shutdownNow();
            if (!serverSocket.isClosed()) {
                serverSocket.close();
            }
            connections.values().forEach(pair -> closeSocket(pair.socket));
            System.out.println("PeerManager shutdown completed.");
        } catch (IOException e) {
            System.err.println("Error closing server socket during shutdown: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Error during PeerManager shutdown: " + e.getMessage());
        }
    }

    private static class ConnectionPair {
        private final Socket socket;
        private final ObjectOutputStream out;
        private final ObjectInputStream in;

        public ConnectionPair(Socket socket, ObjectOutputStream out, ObjectInputStream in) {
            this.socket = socket;
            this.out = out;
            this.in = in;
        }

        public ObjectOutputStream getOutputStream() {
            return out;
        }

        public ObjectInputStream getInputStream() {
            return in;
        }
    }

    private void processReceivedObject(ObjectOutputStream out, Object receivedObject)
            throws IOException, InvalidBlockchainException {
        try {
            node.setShouldInterrupt(true);
            if (receivedObject instanceof Transaction) {
                processTransaction((Transaction) receivedObject);
            } else if (receivedObject instanceof Block) {
                processBlock((Block) receivedObject);
            } else if (receivedObject instanceof BlockchainRequest) {
                respondWithBlockchainMapping(out);
            } else if (receivedObject instanceof UpdateBlockchain) {
                UpdateBlockchain updateBlockchain = (UpdateBlockchain) receivedObject;
                processBlockchain(updateBlockchain.blockchain, out);
            }

        } catch (

        Exception e) {
        } finally {
            node.setShouldInterrupt(false);
        }

    }

    private void processTransaction(Transaction transaction) {
        String transactionHash = transaction.getHash();
        boolean validTransaction = true;

        if (!node.getProcessedTransactions().contains(transactionHash) && node.validateTransaction(transaction)) {
            if (node.validateTransaction(transaction)) {
                for (Input input : transaction.getInputs()) {
                    UTXOKey utxoKeyToCheck = new UTXOKey(input.getTxId(), input.getOutputIndex());
                    if (node.getUTXOPool().getUTXO(utxoKeyToCheck) == null) {
                        System.out.println("Invalid transaction received; block invalid.");
                        validTransaction = false;
                    }
                }
                if (validTransaction) {
                    System.out.println("Received Valid Transaction: " + transactionHash);
                    node.addProcessedTransactions(transaction.getHash());
                    broadcast(transaction);
                }
            }
        }
    }

    private void processBlock(Block block) {
        boolean validBlock = false;
        String blockHash = block.getBlockHash();
        if (!node.getProcessedBlocks().contains(blockHash) && node.validateBlock(block)) {
            int blockHeight = node.getBlockchain().getBlockHeight();
            if (blockHeight + 1 == block.getBlockNumber()) {
                System.out.println("Received Valid Block: " + blockHash);
                (node.getBlockchain()).addBlock(block);
                validBlock = true;
            }
            for (Transaction tx : block.getBlockTransactions()) {
                (node.getMempool()).removeTransactionFromMempool(tx);
            }
            node.addProcessedBlocks(blockHash);
            if (validBlock) {
                broadcast(block);
            }
        }
    }

    private void respondWithBlockchainMapping(ObjectOutputStream out) throws IOException {
        out.writeObject(
                new UpdateBlockchain(node.getBlockchain()));
        out.flush();

    }

    private void processBlockchain(Blockchain receivedBlockchain, ObjectOutputStream out)
            throws InvalidBlockchainException, IOException {
        if (receivedBlockchain.getChain().size() > node.getBlockchain().getChain().size()) {
            System.out.println("Found longer blockchain " + receivedBlockchain);

            Blockchain currentBlockchain = node.getBlockchain();
            UTXOPool currentUTXOPool = node.getUTXOPool();

            node.getBlockchain().replaceChain(currentBlockchain);
            node.getUTXOPool().emptyUTXOPool();
            for (int j = 0; j < node.getBlockchain().getChain().size(); j++) {
                Block block = node.getBlockchain().getChain().get(j);
                if (j == 0) { // genesis block doesn't have a signature
                    for (Transaction transaction : block.getBlockTransactions()) {
                        ArrayList<Output> outputs = transaction.getOutputs();
                        for (int i = 0; i < outputs.size(); i++) {
                            String transactionHash = transaction.getHash();
                            Output output = outputs.get(i);
                            UTXOKey utxoKey = new UTXOKey(transactionHash, i); // Use the transaction hash and output
                                                                               // index
                                                                               // as the
                                                                               // key
                            UTXO utxo = new UTXO(output.getValue(), output.getAddress());
                            node.getUTXOPool().addUTXO(utxoKey, utxo); // Add the new UTXO to the pool
                        }
                    }
                    continue;
                }
                for (Transaction transaction : block.getBlockTransactions()) {
                    if (transaction.getInputs().get(0).getTxId().startsWith("reward")) {
                        ArrayList<Output> outputs = transaction.getOutputs();
                        for (int i = 0; i < outputs.size(); i++) {
                            String transactionHash = transaction.getHash();
                            Output output = outputs.get(i);
                            UTXOKey utxoKey = new UTXOKey(transactionHash, i); // Use the transaction hash and
                                                                               // output
                                                                               // index
                                                                               // as the
                                                                               // key
                            UTXO utxo = new UTXO(output.getValue(), output.getAddress());
                            node.getUTXOPool().addUTXO(utxoKey, utxo); // Add the new UTXO to the pool
                        }
                    } else {
                        ArrayList<Output> outputs = transaction.getOutputs();
                        ArrayList<Input> inputs = transaction.getInputs();
                        boolean validTransaction = false;
                        for (Input input : inputs) {
                            PublicKey senderPublicKey = input.getPublicKey();
                            validTransaction = input.verifySignature(senderPublicKey,
                                    transaction.getTransactionString());
                            if (!validTransaction) {
                                break;
                            }
                        }
                        if (validTransaction) {
                            String transactionHash = transaction.getHash();

                            for (Input input : inputs) {
                                String txHash = input.getTxId();
                                int index = input.getOutputIndex();
                                UTXOKey keyToRemove = new UTXOKey(txHash, index);
                                if (node.getUTXOPool().containsUTXOKey(keyToRemove)) {
                                    node.getUTXOPool().removeFromUTXOPool(keyToRemove);
                                } else {
                                    node.getBlockchain().replaceChain(currentBlockchain);
                                    node.setUTXOPool(currentUTXOPool);
                                    // stop checking and make a new blockchain
                                    throw new InvalidBlockchainException(
                                            "Invalid block received.: UTXO not found in pool. Reverting to original blockchain.");
                                }
                            }

                            for (int i = 0; i < outputs.size(); i++) {
                                Output output = outputs.get(i);
                                UTXOKey utxoKey = new UTXOKey(transactionHash, i); // Use the transaction hash and
                                                                                   // output
                                                                                   // index
                                                                                   // as the
                                                                                   // key
                                UTXO utxo = new UTXO(output.getValue(), output.getAddress());
                                node.getUTXOPool().addUTXO(utxoKey, utxo); // Add the new UTXO to the pool
                            }

                        } else {
                            node.getBlockchain().replaceChain(currentBlockchain);
                            node.setUTXOPool(currentUTXOPool);
                            throw new InvalidBlockchainException(
                                    "Invalid block received: Invalid Transaction Signature. Reverting back to original blockchain.");
                        }
                    }

                }
            }
            System.out.println("Blockchain fetched from peer.");

        } else if (receivedBlockchain.getChain().size() > node.getBlockchain().getChain().size()) {
            System.out.println("Current chain is longer- sending current chain...");
            out.reset();
            respondWithBlockchainMapping(out);
        }
    }

    public Map<String, ObjectOutputStream> getPeerConnections() {
        Map<String, ObjectOutputStream> peerConnections = new ConcurrentHashMap<>();
        connections.forEach((endpoint, pair) -> peerConnections.put(endpoint, pair.getOutputStream()));
        return peerConnections;
    }

    public void requestBlockchainFromPeers() {
        connections.forEach((endpoint, pair) -> {
            ObjectOutputStream out = pair.getOutputStream();
            try {
                out.writeObject(new BlockchainRequest());
                out.flush();
            } catch (IOException e) {
                System.err.println("Failed to request blockchain from peer: " + endpoint + ": " + e.getMessage());
            }
        });
    }

}
