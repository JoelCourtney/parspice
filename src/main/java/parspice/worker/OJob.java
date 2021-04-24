package parspice.worker;

import parspice.ParSPICE;
import parspice.io.IOManager;

import java.util.ArrayList;

/**
 * Jobs that produce output. The user can only get an instance of OJob
 * by calling init on a Worker that produces output.
 *
 * @param <S> Type for setup inputs (Void if none)
 * @param <I> Type for task inputs (Void if none)
 * @param <O> Type for task outputs (Void if none)
 */
public class OJob<S,I,O> extends Job<S,I,O> {
    OJob(Worker worker) {
        super(worker);
    }

    /**
     * Calls Job.runCommon(par), then collects and returns the outputs
     * stored in the IOManagers.
     *
     * @param par instance of ParSPICE to use.
     * @return The outputs generated by the workers.
     * @throws Exception
     */
    public ArrayList<O> run(ParSPICE par) throws Exception {
        runCommon(par);

        ArrayList<O> results = ioManagers.get(0).getOutputs();
        if (results == null) {
            return null;
        }
        results.ensureCapacity(numTasks);
        for (IOManager<S, I, O> ioManager : ioManagers.subList(1, ioManagers.size())) {
            results.addAll(ioManager.getOutputs());
        }
        return results;
    }
}