package parspice.job;

import parspice.ParSPICE;
import parspice.io.IOManager;
import parspice.io.IServer;
import parspice.io.OServer;
import parspice.sender.Sender;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

/**
 * Superclass of all Jobs that don't take input arguments sent from
 * the main process, and do return outputs.
 *
 * @param <I> The type given by the main process as argument.
 * @param <O> The type returned by the job to the main process.
 */
public abstract class IOJob<I,O> extends Job<O> {

    private final Sender<I> inputSender;
    private final Sender<O> outputSender;

    private Socket inputSocket;
    private Socket outputSocket;
    private ObjectInputStream ois;
    private ObjectOutputStream oos;

    /**
     * [main] inputs supplied by user
     */
    private List<I> inputs;

    public IOJob(Sender<I> inputSender, Sender<O> outputSender) {
        this.inputSender = inputSender;
        this.outputSender = outputSender;
    }

    /**
     * [main process] Initialize the job with the inputs it needs to run.
     *
     * @param numWorkers number of workers to use.
     * @param inputs inputs to split among the workers
     * @return this (builder pattern)
     */
    public final IOJob<I,O> init(int numWorkers, List<I> inputs) {
        this.numWorkers = numWorkers;
        this.inputs = inputs;
        this.numTasks = inputs.size();

        validate();

        return this;
    }

    /**
     * [main process] Runs the job in parallel.
     *
     * @param par a ParSPICE instance with worker jar and minimum port number.
     * @return An ArrayList of outputs, collected from the job's return values.
     * @throws Exception
     */
    public final ArrayList<O> run(ParSPICE par) throws Exception {
        if (inputs == null) {
            throw new IllegalStateException("Inputs must be specified.");
        }
        ArrayList<IOManager<?,?,O>> ioManagers = new ArrayList<>(numWorkers);

        int task = 0;
        int minPort = par.getMinPort();

        for (int i = 0; i < numWorkers; i++) {
            int taskSubset = taskSubset(numTasks, numWorkers, i);
            List<I> inputsSublist = inputs.subList(task, task+taskSubset);
            IServer<Void,I> iServer = new IServer<>(inputSender, null, inputsSublist, null, minPort + 2*i, i);
            OServer<O> oServer = new OServer<>(outputSender, taskSubset, minPort + 2*i + 1, i);
            ioManagers.add(new IOManager<>(iServer, oServer, i));
            task += taskSubset;
        }

        runCommon(par, ioManagers);

        return collectOutputs(ioManagers);
    }

    /**
     * [worker process] Calls setup.
     *
     * The user cannot call or override this function.
     *
     * @throws Exception any exception the user code needs to throw
     */
    @Override
    final void setupWrapper() throws Exception {
        setup();
    }

    /**
     * [worker process] Repeatedly reads an input, calls task, and writes the output
     *
     * The user cannot call or override this function.
     *
     * @throws Exception any exception the user code needs to throw
     */
    @Override
    final void taskWrapper() throws Exception {
        for (int i = getStartIndex(); i < getStartIndex() + getTaskSubset(); i++) {
            outputSender.write(task(inputSender.read(ois)), oos);
        }
    }

    /**
     * [worker process] Starts the input and output socket connections with the main process.
     *
     * @throws IOException if the connections cannot be made
     */
    @Override
    final void startConnections() throws IOException {
        inputSocket = new Socket("localhost", getInputPort());
        outputSocket = new Socket("localhost", getOutputPort());
        ois = new ObjectInputStream(inputSocket.getInputStream());
        oos = new ObjectOutputStream(outputSocket.getOutputStream());
    }

    /**
     * [worker process] Ends the input and output connections with the main process.
     *
     * @throws IOException if the connections cannot be ended.
     */
    @Override
    final void endConnections() throws IOException {
        oos.close();
        outputSocket.close();
        ois.close();
        inputSocket.close();
    }

    /**
     * [worker] Called once on each worker when the job starts running.
     *
     * The user can optionally override this function; by default it does nothing.
     *
     * @throws Exception any exception the user code needs to throw
    */
    public void setup() throws Exception {}

    /**
     * [worker] Called repeatedly, once for each input sent from the main process.
     *
     * @param input The input given by the main process to the worker.
     * @return The value to be sent back to the main process.
     * @throws Exception any exception the user code needs to throw
     */
    public abstract O task(I input) throws Exception;
}
