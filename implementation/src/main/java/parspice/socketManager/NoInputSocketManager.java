package parspice.socketManager;

import parspice.sender.Sender;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;

/**
 * A threadable class that listens on a socket for all responses coming
 * back from a single worker.
 *
 * It aggregates the responses into a list, which is later combined with the
 * lists generated by other workers.
 *
 * @param <O> The type deserialized by the given sender object.
 */
public class NoInputSocketManager<O> extends SocketManager<O> {
    private final Sender<O> sender;

    public NoInputSocketManager(ServerSocket serverSocket, Sender<O> send, int workerIndex, int batchSize) {
        super(serverSocket, workerIndex, batchSize);
        sender = send;
    }

    @Override
    public void sendAndReceive() {
        try {
            ObjectInputStream ois = getInputStream();
            for (int i = 0; i < batchSize; i++) {
                outputs.add(sender.read(ois));
            }
            ois.close();
        } catch (IOException e) {
            System.out.println("Socket Manager " + workerIndex + " failed after " + outputs.size());
            e.printStackTrace();
        }
    }
}
