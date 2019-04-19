package av.is.aegis;

import com.google.common.util.concurrent.AtomicDouble;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class Neuron implements Serializable {

    private static final Set<Neuron> EMPTY_SET = new HashSet<>();
    private static final AtomicInteger ATOMIC_ID_GENERATOR = new AtomicInteger();

    private static final int STRONG_SYNAPSE_THRESHOLD = 50;
    private static final int LIFETIME_SYNAPSE_THRESHOLD = 100;

    private static final Random RANDOM = new Random();

    private static final double EPSP_MULTIPLY = 2.13782d;
    private static final double IPSP_MULTIPLY = 1.06271d;

    static final double STABLE_POTENTIAL = -77d;

    private final ExecutorService executorService;

    private Map<Neuron, Synapse> connections = new ConcurrentHashMap<>();
    private final int threshold;

    private boolean marked = false;

    private AtomicDouble sum = new AtomicDouble(STABLE_POTENTIAL);
    private AtomicInteger refractoryDuration = new AtomicInteger();
    private AtomicBoolean refractory = new AtomicBoolean();
    private AtomicBoolean absoluteRefractory = new AtomicBoolean();

    private Coordinate coordinate;
    private Output output;

    private final Network.Configuration configuration;
    private final int id;

    private final int index;

    Neuron(int index, Network.Configuration configuration, ExecutorService executorService) {
        this.index = index;
        this.configuration = configuration;
        this.id = ATOMIC_ID_GENERATOR.getAndIncrement();

        this.executorService = executorService;
        this.threshold = RANDOM.nextInt(10) - 50;
    }

    public int getIndex() {
        return index;
    }

    class Coordinate implements Serializable {
        int x;
        int y;

        Neuron neuron;

        @Override
        public boolean equals(Object obj) {
            if(!(obj instanceof Coordinate)) {
                return false;
            }
            Coordinate coordinate = (Coordinate) obj;
            return coordinate.x == x && coordinate.y == y;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y);
        }
    }

    void setCoordinate(int x, int y) {
        Coordinate coordinate = new Coordinate();
        coordinate.x = x;
        coordinate.y = y;
        coordinate.neuron = this;
        this.coordinate = coordinate;
    }

    Coordinate getCoordinate() {
        return coordinate;
    }

    class Output {

        OutputConsumer callback;

    }

    void setOutput(OutputConsumer callback) {
        if(callback == null) {
            this.output = null;
        } else {
            Output output = new Output();
            output.callback = callback;
            this.output = output;
        }
    }

    boolean isOutputNeuron() {
        return output != null;
    }

    Set<Neuron> connectedTo(Network.SynapseVisibility visibility) {
        switch (visibility) {
            case ALL:
                return connections.keySet();

            case STRONG_ONLY:
                return filterSynapses(STRONG_SYNAPSE_THRESHOLD);

            case LIFETIME_ONLY:
                return filterSynapses(LIFETIME_SYNAPSE_THRESHOLD);

            case NONE:
            default:
                return EMPTY_SET;
        }
    }

    private Set<Neuron> filterSynapses(double threshold) {
        Set<Neuron> neurons = new HashSet<>();
        for(Map.Entry<Neuron, Synapse> entry : connections.entrySet()) {
            Synapse synapse = entry.getValue();
            switch (synapse.synapseType) {
                case EXCITATORY:
                    if(entry.getValue().transmitter > threshold) {
                        neurons.add(entry.getKey());
                    }
                    break;

                case INHIBITORY:
                    if(entry.getValue().transmitter < -threshold) {
                        neurons.add(entry.getKey());
                    }
                    break;
            }
        }
        return neurons;
    }

    boolean isMarked() {
        return marked;
    }

    void setMarked() {
        this.marked = true;
    }

    void createConnection(Neuron other, Synapse synapse) {
        connections.putIfAbsent(other, synapse);
    }

    boolean needConnection(int max) {
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

    AtomicDouble getSum() {
        return sum;
    }

    /**
     * axon(here) -> connections(dendrite, other)
     */
    private void stimulate() {
        refractory.set(true);
        refractoryDuration.set(2);
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
                        if(configuration.synapseReinforcing) {
                            synapse.transmitter = Math.min(1000, synapse.transmitter + 0.01d);
                        }
                    }
                    break;

                case INHIBITORY:
                    if(transmitter >= 0) {
                        // Destroyable
                        iterator.remove();
                    } else {
                        // IPSP
                        executorService.execute(new Stimulator(entry.getKey(), transmitter * IPSP_MULTIPLY));
                        if(configuration.synapseReinforcing) {
                            synapse.transmitter = Math.max(-1000, synapse.transmitter - 0.01d);
                        }
                    }
                    break;
            }
        }
        sum.set(-77d);
        absoluteRefractory.set(!absoluteRefractory.get());
    }

    void tick() {
        if(refractory.get()) {
            if(refractoryDuration.decrementAndGet() == 0) {
                refractory.set(false);
            }
        }
        if(checkThreshold()) {
            stimulate();
        }
        if(sum.get() != STABLE_POTENTIAL) {
            double diff = sum.get() - STABLE_POTENTIAL;
            diff /= 2;

            double result = diff + STABLE_POTENTIAL;

            if(Math.abs(diff) < 0.01) {
                result = STABLE_POTENTIAL;
            }
            sum.set(Math.max(STABLE_POTENTIAL, result));
        }
        if(configuration.synapseDecaying) {
            for(Map.Entry<Neuron, Synapse> entry : connections.entrySet()) {
                Synapse synapse = entry.getValue();
                switch (synapse.synapseType) {
                    case EXCITATORY:
                        synapse.transmitter -= 0.0001d;
                        break;

                    case INHIBITORY:
                        synapse.transmitter += 0.0001d;
                        break;
                }
            }
        }
    }

    /**
     * axon(other) -> dendrite(here)
     */
    void recept(double stimulation) {
        if(output != null) {
            output.callback.accept(this, stimulation);
        } else {
            if(refractory.get() && absoluteRefractory.get()) {
                return;
            }
            sum.addAndGet(stimulation);
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public boolean equals(Object obj) {
        if(!(obj instanceof Neuron)) {
            return false;
        }
        Neuron other = (Neuron) obj;
        return other.id == id;
    }
}