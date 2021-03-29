package parspice.sender;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/**
 * Sender implementation for int[].
 *
 * Accepts arrays of fixed length or dynamic length. If fixed length, the length should
 * be declared on initialization. If the length is declared, then using arrays of
 * any other size is undefined behavior, and may or may not cause a runtime error.
 *
 * When sending dynamic arrays, it first sends the length of the array, increasing
 * network usage slightly. It doesn't matter significantly for more tasks, but
 * remember to specify the lengths for optimal performance.
 */
public class IntArraySender implements Sender<int[]> {
    private final int length;

    /**
     * Creates an instance of IntArraySender for dynamic length arrays.
     */
    public IntArraySender() {
        this.length = -1;
    }

    /**
     * Creates an instance of IntArraySender for fixed length arrays.
     *
     * @param length the length of all arrays to be serialized an deserialized
     */
    public IntArraySender(int length) {
        this.length = length;
    }

    @Override
    public int[] read(ObjectInputStream ois) throws IOException {
        int localLength = length;
        if (length == -1) {
            localLength = ois.readInt();
        }
        int[] in = new int[localLength];
        for (int i = 0; i < localLength; i++) {
            in[i] = ois.readInt();
        }
        return in;
    }

    @Override
    public void write(int[] out, ObjectOutputStream oos) throws IOException {
        if (length == -1) {
            oos.writeInt(out.length);
        }
        for (int b : out) {
            oos.writeInt(b);
        }
    }
}