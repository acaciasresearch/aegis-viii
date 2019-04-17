package av.is.aegis;

import com.google.common.util.concurrent.AtomicDouble;

import java.io.Serializable;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

public class Neuron implements Serializable {

    public static final Random RANDOM = new Random();

    private static final double EPSP_MULTIPLY = 2.13782d;
    private static final double IPSP_MULTIPLY = 1.06271d;

    private static final double STABLE_POTENTIAL = -77d;

    private final ExecutorService executorService;

    private Map<Neuron, Synapse> connections = new ConcurrentHashMap<>();
    private final int threshold;

    private boolean marked = false;

    private AtomicDouble sum = new AtomicDouble(STABLE_POTENTIAL);
    private AtomicBoolean refractory = new AtomicBoolean();
    private AtomicBoolean absoluteRefractory = new AtomicBoolean();

    public Neuron(ExecutorService executorService) {
        this.executorService = executorService;
        this.threshold = RANDOM.nextInt(10) - 50;
    }

    public boolean isMarked() {
        return marked;
    }

    public void setMarked(boolean marked) {
        this.marked = marked;
    }

    public void createConnection(Neuron other, Synapse synapse) {
        connections.putIfAbsent(other, synapse);
    }

    public boolean needConnection(int max) {
        return connections.size() < max;
    }

    private class Stimulator implements Runnable {

        private final Neuron neuron;
        private final double stimulation;

        Stimulator(Neuron neuron, double stimulation) {
            this.neuron = neuron;
            this.stimulation = stimulation;
        }

        @Override
        public void run() {
            neuron.recept(stimulation);
        }
    }

    private boolean checkThreshold() {
        return sum.get() >= threshold;
    }

    /**
     * axon(here) -> connections(dendrite, other)
     */
    private void stimulate() {
        refractory.set(true);
        for(Iterator<Map.Entry<Neuron, Synapse>> iterator = connections.entrySet().iterator(); iterator.hasNext(); ) {
            Map.Entry<Neuron, Synapse> entry = iterator.next();
            Synapse synapse = entry.getValue();

            double transmitter = synapse.transmitter;
            switch (synapse.synapseType) {
                case EXCITATORY:
                    if(transmitter <= 0) {
                        // Destroyable
                        iterator.remove();
                    } else {
                        // EPSP
                        executorService.execute(new Stimulator(entry.getKey(), transmitter * EPSP_MULTIPLY));
                    }
                    break;

                case INHIBITORY:
                    if(transmitter >= 0) {
                        // Destroyable
                        iterator.remove();
                    } else {
                        // IPSP
                        executorService.execute(new Stimulator(entry.getKey(), transmitter * IPSP_MULTIPLY));
                    }
                    break;
            }
        }
        sum.set(-77d);
        absoluteRefractory.set(!absoluteRefractory.get());
    }

    public void tick() {
        if(checkThreshold()) {
            stimulate();
        }
        sum.set(Math.max(sum.get() - 0.01d, STABLE_POTENTIAL));
        for(Map.Entry<Neuron, Synapse> entry : connections.entrySet()) {
            Synapse synapse = entry.getValue();
            switch (synapse.synapseType) {
                case EXCITATORY:
                    synapse.transmitter -= 0.01d;
                    break;

                case INHIBITORY:
                    synapse.transmitter += 0.01d;
                    break;
            }
        }
    }

    /**
     * axon(other) -> dendrite(here)
     */
    private void recept(double stimulation) {
        if(refractory.get() && absoluteRefractory.get()) {
            return;
        }
        sum.addAndGet(stimulation);
    }

}