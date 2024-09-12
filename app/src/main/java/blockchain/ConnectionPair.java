package blockchain;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class ConnectionPair {
    private final Socket socket;
    private final ObjectOutputStream out;
    private final ObjectInputStream in;

    public ConnectionPair(Socket socket, ObjectOutputStream out, ObjectInputStream in) {
        this.socket = socket;
        this.out = out;
        this.in = in;
    }

    public Socket getSocket() {
        return this.socket;
    }

    public ObjectOutputStream getOutputStream() {
        return out;
    }

    public ObjectInputStream getInputStream() {
        return in;
    }
}