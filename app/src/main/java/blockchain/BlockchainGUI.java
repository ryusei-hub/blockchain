package blockchain;

import java.io.File;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;

public class BlockchainGUI extends Application {
    private static Node nodeInstance;
    private Wallet userWallet;
    private ArrayList<Wallet> userWallets;

    public static void setNodeInstance(Node node) {
        BlockchainGUI.nodeInstance = node;
    }

    public BlockchainGUI() {
    }

    String txId = "";
    int outputIndex = -1;

    File utxoFile = new File("utxoMap.ser");

    @Override
    public void stop() {
        if (nodeInstance != null) {
            nodeInstance.getTaskExecutorService().shutdownNow();
            if (nodeInstance.getPeerManager() != null) {
                nodeInstance.getPeerManager().shutdown();
            }
            nodeInstance.saveMappingsOnShutdown();

        }
        System.out.println("Application is closing. Data saved.");
        System.exit(0);
    }

    @Override
    public void start(Stage primaryStage) {
        nodeInstance.initializeBlockchainComponents();
        primaryStage.setTitle("Blockchain Interface");
        primaryStage.setFullScreen(true);
        GridPane grid = new GridPane();
        grid.setPadding(new Insets(20));
        grid.setHgap(10);
        grid.setVgap(10);
        // update the blockchain (get the longest blockchain on the network)
        // update ALL the hashmaps from information on other connected nodes

        TextArea blockInfoTextArea = new TextArea();
        blockInfoTextArea.setEditable(false);
        blockInfoTextArea.setWrapText(true);

        String blockInfo = "Block Number: \n" +
                "Prev Block Hash: \n" +
                "Block Hash: \n" +
                "Miner Address: \n" +
                "Block Timestamp: \n" +
                "Number of Block Transactions: ";
        blockInfoTextArea.setText(blockInfo);

        Button miningButton = new Button("Start Mining");

        Label recipientLabel = new Label("Recipient Address:");
        TextField recipientTextField = new TextField();
        CheckBox highPriority = new CheckBox("High Priority");

        Label amountLabel = new Label("Amount:");
        TextField amountTextField = new TextField();
        CheckBox useNewWallet = new CheckBox("Use New Wallet?");
        Button sendButton = new Button("Send");
        Button accountButton = new Button("Account Details");
        Button showHistoryButton = new Button("Show Personal Transaction History");
        Button showAllHistoryButton = new Button("Show Blockchain Transaction History");

        Label messageLabel = new Label("");

        TextArea recentTx = new TextArea();
        recentTx.setEditable(false);
        recentTx.setWrapText(true);

        Label accountBalanceLabel = new Label("Account Balance: ");
        TextField accountBalance = new TextField();
        accountBalance.setEditable(false);

        TextArea history = new TextArea();
        history.setEditable(false);
        history.setWrapText(true);

        TextArea allHistory = new TextArea();
        allHistory.setEditable(false);
        allHistory.setWrapText(true);
        TextArea helpText = new TextArea();
        helpText.setEditable(false);
        helpText.setWrapText(true);
        helpText.setText(
                "Instructions:\nPress Start Mining to start mining blocks.\n" +
                        "Details of the UTXO pool, which stores UTXOs of each user on the network, can be seen in the terminal after every block.\n"
                        + "Press Account Details to see your account balance and the three most recent transactions you were involved in.\n"
                        + "Press Personal Transaction History to view all transactions you were involved in.\n"
                        + "Press Show Blockchain Transaction History to view all transactions made on the network.\n"
                        + "Enter recipient address then value to send, then hit send to make the transaction.\n"
                        + "Once a block is mined, the update UTXO pool and the block hash and the block number of the newly mined block will be shown in the terminal.\n"
                        + "Block details are shown in the text area under the send button.");
        grid.add(accountButton, 0, 0);
        grid.add(recentTx, 1, 0);

        grid.add(accountBalanceLabel, 0, 1);

        grid.add(accountBalance, 1, 1);

        grid.add(miningButton, 0, 2);

        grid.add(showHistoryButton, 3, 0);
        grid.add(history, 4, 0);
        grid.add(showAllHistoryButton, 3, 3);
        grid.add(allHistory, 4, 3);

        grid.add(useNewWallet, 2, 5);
        grid.add(highPriority, 2, 4);
        grid.add(recipientLabel, 0, 3);

        grid.add(recipientTextField, 1, 3);
        grid.add(amountLabel, 0, 4);
        grid.add(amountTextField, 1, 4);
        grid.add(sendButton, 0, 5);

        grid.add(blockInfoTextArea, 0, 6, 2, 1);
        grid.add(messageLabel, 0, 8);

        grid.add(helpText, 4, 6);
        miningButton.setOnAction(e -> {
            new Thread(() -> {
                try {
                    nodeInstance.registerOrVerifyUser(messageLabel,
                            miningButton, blockInfoTextArea);

                    this.userWallets = nodeInstance.getUserWallets();
                    Random random = new Random();

                    int randomIndex = random.nextInt(this.userWallets.size());
                    this.userWallet = this.userWallets.get(randomIndex);
                    String minerAddress = userWallet.generateAddress();
                    nodeInstance.startMining(userWallet, minerAddress, blockInfoTextArea, accountBalance);
                } catch (NoSuchAlgorithmException a) {

                }
            }).start();

        });
        showHistoryButton.setOnAction(e -> {
            new Thread(() -> {
                ArrayList<Wallet> userWallets = nodeInstance.getUserWallets();
                if (!userWallets.isEmpty()) {
                    Set<String> userTransactions = new HashSet<>();
                    for (Wallet wallet : userWallets) {
                        userTransactions.addAll(wallet.getTransactions(nodeInstance.getBlockchain(),
                                nodeInstance.getWalletServer(), nodeInstance.getPort()));
                    }

                    String transactionDetails = userTransactions.stream().sorted(Comparator.reverseOrder())
                            .collect(Collectors.joining("\n"));
                    Platform.runLater(() -> {
                        history.setText(transactionDetails);
                    });
                } else {
                    Platform.runLater(() -> {
                        history.setText("No wallet available.");
                    });
                }
            }).start();
        });
        // fix
        showAllHistoryButton.setOnAction(e -> {
            new Thread(() -> {
                ArrayList<Wallet> userWallets = nodeInstance.getUserWallets();
                if (!userWallets.isEmpty()) {
                    Set<String> userTransactions = new HashSet<>();

                    for (Wallet wallet : userWallets) {
                        userTransactions.addAll(wallet.getAllTransactions(nodeInstance.getBlockchain()));
                    }
                    String transactionDetails = userTransactions.stream().sorted(Comparator.reverseOrder())
                            .collect(Collectors.joining("\n"));
                    Platform.runLater(() -> {
                        if (transactionDetails.length() > 0) {
                            allHistory.setText(transactionDetails.toString());
                        } else {
                            allHistory.setText("No transactions found.");
                        }
                    });
                } else {
                    Platform.runLater(() -> {
                        allHistory.setText("No wallet available.");
                    });
                }
            }).start();
        });
        accountButton.setOnAction(e -> {
            new Thread(() -> {
                ArrayList<Wallet> userWallets = nodeInstance.getUserWallets();
                if (userWallets != null && !userWallets.isEmpty()) {
                    Set<String> recentTransactions = new HashSet<>();
                    for (Wallet wallet : userWallets) {
                        List<String> transactions = wallet.getTransactions(nodeInstance.getBlockchain(),
                                nodeInstance.getWalletServer(), nodeInstance.getPort());
                        recentTransactions.addAll(transactions.subList(0, Math.min(3, transactions.size())));
                    }
                    String transactionDetails = recentTransactions.stream()
                            .limit(3) // Limiting to the 3 most recent overall
                            .sorted(Comparator.reverseOrder())
                            .collect(Collectors.joining("\n"));
                    Platform.runLater(() -> {
                        recentTx.setText(
                                "Recent Transactions:\n" + transactionDetails);
                    });
                } else {
                    Platform.runLater(() -> {
                        recentTx.setText("No wallet available.");
                    });
                }
            }).start();
        });

        sendButton.setOnAction(e -> {
            new Thread(() -> {
                String recipientAddress = recipientTextField.getText();
                // Perform transaction logic here

                double amount = Double.parseDouble(amountTextField.getText());
                // check users exist
                boolean newAddress = false;
                boolean high = false;
                if (useNewWallet.isSelected()) {
                    newAddress = true;
                }
                if (highPriority.isSelected()) {
                    high = true;
                }
                if (!(nodeInstance.isAddressValid(recipientAddress))) {
                    Platform.runLater(() -> {
                        messageLabel.setText("Invalid address. Enter valid address.");

                    });
                } else {
                    try {
                        nodeInstance.performTransaction(recipientAddress, amount, newAddress, high);
                    } catch (NoSuchAlgorithmException b) {
                    }
                }
            }).start();
        });

        Scene scene = new Scene(grid, 400, 300);
        primaryStage.setScene(scene);
        primaryStage.setTitle("Blockchain Transaction GUI");
        primaryStage.show();
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java BlockchainGUI <port>");
            System.exit(1);
        }

        int port;
        try {
            port = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            System.err.println("Error: Port must be a valid integer.");
            System.exit(1);
            return;
        }

        // Initialize and set the node instance here
        Node node = new Node(port);
        setNodeInstance(node);

        node.start();
        // Start the JavaFX application
        launch(args);
    }
}