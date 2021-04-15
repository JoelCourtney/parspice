package parspice;

import parspice.sender.Sender;
import parspice.io.IServer;
import parspice.io.OServer;
import parspice.io.IOManager;
import parspice.worker.AutoWorker;
import parspice.worker.IOWorker;
import parspice.worker.IWorker;
import parspice.worker.OWorker;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Enumeration;
import java.util.List;
import java.util.ArrayList;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * The interface for running parallel multi-process tasks with ParSPICE.
 *
 * Tasks can be run either with input arguments given to the worker processes, or without.
 * For short tasks, the network overhead is significant, so input arguments should be
 * avoided if at all possible. If inputs are not used, the task will receive an integer
 * indicating which run of the task it is, as if it was inside a for loop on a single process.
 */
public class ParSPICE {

    private final String workerJar;
    private final int minPort;

    /**
     * Creates a new ParSPICE object for a given jar file.
     *
     * @param workerJar the jar for all tasks on this instance to be
     *                  run from.
     */
    public ParSPICE(String workerJar, int minPort) throws IOException {
        checkJar(workerJar);

        this.workerJar = workerJar;
        this.minPort = minPort;
    }

    /**
     * Runs a custom task that takes remote inputs and returns outputs
     * to the main process.
     *
     * Sending inputs is slow. The network overhead of both sending inputs and
     * receiving outputs is slightly more than double the overhead of just
     * receiving outputs. Prefer the output-only version if at all possible.
     *
     * @param ioWorker An instance of the worker to parallelize
     * @param inputs List of inputs to be sent and processed in parallel
     * @param numWorkers Number of worker processes to distribute to
     * @param <I> Input argument type
     * @param <O> Output return type
     * @return The list of outputs, in the same order as the inputs
     * @throws Exception
     */
    public <I,O> ArrayList<O> run(
            IOWorker<I,O> ioWorker,
            List<I> inputs,
            int numWorkers
    ) throws Exception {
        String mainClass = "parspice.worker.IOWorker";
        String workerClass = ioWorker.getClass().getName();
        Sender<I> inputSender = ioWorker.getInputSender();
        Sender<O> outputSender = ioWorker.getOutputSender();
        ArrayList<IOManager<I,O>> ioManagers = new ArrayList<>(numWorkers);
        int numIterations = inputs.size();
        int iteration = 0;
        for (int i = 0; i < numWorkers; i++) {
            int subset = subset(numIterations, numWorkers, i);
            ioManagers.add(new IOManager<>(
                    new IServer<>(inputSender, inputs.subList(iteration, iteration + subset), minPort + 2*i, i),
                    new OServer<>(outputSender, subset, minPort + 2*i + 1, i),
                    i
            ));
            iteration += subset;
        }
        runInternal(mainClass, workerClass, numIterations, numWorkers, ioManagers);

        return aggregateOutputs(ioManagers, numIterations);
    }

    /**
     * Runs a custom task that takes a locally-generated integer as input
     * and returns outputs to the main process.
     *
     * @param oWorker An instance of the worker to parallelize.
     * @param numIterations Number of times to run the task. Each run will receive as
     *                      argument a unique index i in the range 0:(numIterations-1) (inclusive)
     * @param numWorkers Number of worker processes to distribute to
     * @param <O> Output return type
     * @return The list of outputs, sorted by index i. (see argument numIterations)
     * @throws Exception
     */
    public <O> ArrayList<O> run(
            OWorker<O> oWorker,
            int numIterations,
            int numWorkers
    ) throws Exception {
        String mainClass = "parspice.worker.OWorker";
        String workerClass = oWorker.getClass().getName();
        Sender<O> outputSender = oWorker.getOutputSender();
        ArrayList<IOManager<Void,O>> ioManagers = new ArrayList<>(numWorkers);
        for (int i = 0; i < numWorkers; i++) {
            int subset = subset(numIterations, numWorkers, i);
            ioManagers.add(new IOManager<>(
                    null,
                    new OServer<>(outputSender, subset, minPort + 2*i + 1, i),
                    i
            ));
        }
        runInternal(mainClass, workerClass, numIterations, numWorkers, ioManagers);

        return aggregateOutputs(ioManagers,  numIterations);
    }

    /**
     * Runs a custom task that takes remote inputs and don't return output.
     *
     * @param iWorker An instance of the worker to parallelize
     * @param inputs List of inputs to be sent and processed in parallel
     * @param numWorkers Number of worker processes to distribute to
     * @param <I> Input argument type
     * @throws Exception
     */
    public <I> void run(
            IWorker<I> iWorker,
            List<I> inputs,
            int numWorkers
    ) throws Exception {
        String mainClass = "parspice.worker.IWorker";
        String workerClass = iWorker.getClass().getName();
        Sender<I> inputSender = iWorker.getInputSender();
        ArrayList<IOManager<I, Void>> ioManagers = new ArrayList<>(numWorkers);
        int numIterations = inputs.size();
        int iteration = 0;
        for (int i = 0; i < numWorkers; i++) {
            int subset = subset(numIterations, numWorkers, i);
            ioManagers.add(new IOManager<>(
                    new IServer<>(inputSender, inputs.subList(iteration, iteration + subset), minPort + 2*i, i),
                    null,
                    i
            ));
            iteration += subset;
        }
        runInternal(mainClass, workerClass, numIterations, numWorkers, ioManagers);
    }

    /**
     * Runs a custom task that takes a locally-generated integer as input
     * and doesn't return output.
     *
     * @param autoWorker An instance of the worker to parallelize
     * @param numIterations Number of times to run the task. Each run will receive as
     *                      argument a unique index i in the range 0:(numIterations-1) (inclusive)
     * @param numWorkers Number of worker processes to distribute to
     * @throws Exception
     */
    public void run(
            AutoWorker autoWorker,
            int numIterations,
            int numWorkers
    ) throws Exception {
        String mainClass = "parspice.worker.AutoWorker";
        String workerClass = autoWorker.getClass().getName();
        runInternal(mainClass, workerClass, numIterations, numWorkers, null);
    }

    /**
     * Internal logic common to both of the publicly facing run functions.
     */
    private <I,O> void runInternal(String mainClass, String workerClass, int numIterations, int numWorkers, ArrayList<IOManager<I,O>> ioManagers) throws Exception {
        checkMainClass(workerJar, mainClass);
        if (ioManagers != null) {
            for (IOManager<I,O> manager : ioManagers) {
                manager.start();
            }
        }
        Process[] processes = new Process[numWorkers];
        int iteration = 0;
        for (int i = 0; i < numWorkers; i++) {
            int subset = subset(numIterations, numWorkers, i);
            String args = "-Dname=parspice_worker_" + i +
                    " -cp " + workerJar +
                    " " + mainClass +
                    " " + workerClass +
                    " " + (minPort + 2*i) +
                    " " + iteration +
                    " " + subset +
                    " " + i;
            processes[i] = Runtime.getRuntime().exec("java " + args);
            iteration += subset;
        }
        if (ioManagers != null) {
            for (IOManager<I,O> manager : ioManagers) {
                manager.join();
            }
        }
        for (int i = 0; i < numWorkers; i++) {
            processes[i].waitFor();
        }
    }

    /**
     * Checks that the given file exists, and that it is a .jar file. Throws an exception if not.
     *
     * @param workerJar path (can be relative) to the jar file.
     * @throws IOException
     */
    private static void checkJar(String workerJar) throws IOException {
        String extension = workerJar.substring(workerJar.length()-4);
        if (!extension.equals(".jar")) {
            throw new IOException("workerJar must be a .jar file, not: " + extension);
        }
        File file = new File(workerJar);
        if (!file.exists()) {
            throw new FileNotFoundException(workerJar);
        }
    }

    /**
     * Checks that the given main class is in the the jar file. Throws an exception if not.
     *
     * @param workerJar path (can be relative) to the jar file
     * @param mainClass main class to look for, in package notation as given as a jvm argument.
     * @throws ClassNotFoundException
     * @throws IOException
     */
    private static void checkMainClass(String workerJar, String mainClass) throws ClassNotFoundException, IOException {
        JarFile jarFile = new JarFile(workerJar);
        Enumeration<JarEntry> e = jarFile.entries();
        while (e.hasMoreElements()) {
            JarEntry jarEntry = e.nextElement();
            if (jarEntry.getName().endsWith(".class")) {
                String className = jarEntry.getName()
                        .replace("/", ".")
                        .replace(".class", "");
                if (className.equals(mainClass)) {
                    return;
                }
            }
        }
        throw new ClassNotFoundException(mainClass);
    }

    /**
     * Calculate how many iterations should be given to a particular worker.
     *
     * Each worker is given an almost-equal subset. If numIterations is not
     * an even multiple of numWorkers, the remainder is spread across the
     * first numIterations % numWorkers workers.
     *
     * @param numIterations total number of iterations
     * @param numWorkers number of workers
     * @param i the index of a particular worker
     * @return the number of iterations that worker should run
     */
    private static int subset(int numIterations, int numWorkers, int i) {
        return numIterations/numWorkers + ((i < numIterations%numWorkers)?1:0);
    }

    private static <I,O> ArrayList<O> aggregateOutputs(ArrayList<IOManager<I,O>> ioManagers, int numIterations) {
        ArrayList<O> results = ioManagers.get(0).getOutputs();
        results.ensureCapacity(numIterations);
        for (IOManager<?, O> ioManager : ioManagers.subList(1, ioManagers.size())) {
            results.addAll(ioManager.getOutputs());
        }
        return results;
    }
}
