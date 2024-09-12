package blockchain;

class InvalidBlockchainException extends Exception {
    public InvalidBlockchainException(String message) {
        super(message);
    }
}