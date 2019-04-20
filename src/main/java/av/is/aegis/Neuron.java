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

    private static final int STRONG_SYNAPSE_THRESHOLD = 25;
    private static final int LIFETIME_SYNAPSE_THRESHOLD = 50;

    private static final Random RANDOM = new Random();

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
                return filterSynapses(configuration.excitatoryMaximumStrength / 4, configuration.inhibitoryMaximumStrength / 4);

            case LIFETIME_ONLY:
                return filterSynapses(configuration.excitatoryMaximumStrength / 2, configuration.inhibitoryMaximumStrength / 2);

            case NONE:
            default:
                return EMPTY_SET;
        }
    }

    private Set<Neuron> filterSynapses(double exicitatoryThreshold, double inhibitoryThreshold) {
        Set<Neuron> neurons = new HashSet<>();
        for(Map.Entry<Neuron, Synapse> entry : connections.entrySet()) {
            Synapse synapse = entry.getValue();
            switch (synapse.synapseType) {
                case EXCITATORY:
                    if(entry.getValue().transmitter > exicitatoryThreshold) {
                        neurons.add(entry.getKey());
                    }
                    break;

                case INHIBITORY:
                    if(entry.getValue().transmitter < inhibitoryThreshold) {
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
                        executorService.execute(new Stimulator(entry.getKey(), transmitter * configuration.epspMultiply));
                        if(configuration.synapseReinforcing) {
                            if(configuration.excitatoryMaximumStrength != 0) {
                                synapse.transmitter += (configuration.excitatoryMaximumStrength - synapse.transmitter) * 0.0005d;
                            } else {
                                synapse.transmitter += configuration.excitatoryReinforcementRatio;
                            }
                        }

                        if(configuration.delayOnQueueStimulation > 0) {
                            try {
                                Thread.sleep(configuration.delayOnQueueStimulation);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                    break;

                case INHIBITORY:
                    if(transmitter >= 0) {
                        // Destroyable
                        iterator.remove();
                    } else {
                        // IPSP
                        executorService.execute(new Stimulator(entry.getKey(), transmitter * configuration.ipspMultiply));
                        if(configuration.synapseReinforcing) {
                            if(configuration.inhibitoryMaximumStrength != 0) {
                                synapse.transmitter += (configuration.inhibitoryMaximumStrength - synapse.transmitter) * 0.0005d;
                            } else {
                                synapse.transmitter -= configuration.inhibitoryReinforcementRatio;
                            }
                        }

                        if(configuration.delayOnQueueStimulation > 0) {
                            try {
                                Thread.sleep(configuration.delayOnQueueStimulation);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                    break;
            }
        }
        sum.set(-77d);
        absoluteRefractory.set(!absoluteRefractory.get());
    }

    void suppress(SynapseType synapseType) {
        for(Synapse synapse : connections.values()) {
            if(synapse.synapseType == synapseType) {
                switch (synapseType) {
                    case EXCITATORY:
                        // down to 0
                        synapse.transmitter -= configuration.excitatorySuppressionRatio;
                        break;

                    case INHIBITORY:
                        // up to 0
                        synapse.transmitter += configuration.inhibitorySuppressionRatio;
                        break;
                }
            }
        }
    }

    void grow(SynapseType synapseType) {
        for(Synapse synapse : connections.values()) {
            if(synapse.synapseType == synapseType) {
                switch (synapseType) {
                    case EXCITATORY:
                        synapse.transmitter += configuration.excitatoryGrowRatio;
                        break;

                    case INHIBITORY:
                        synapse.transmitter -= configuration.inhibitoryGrowRatio;
                        break;
                }
            }
        }
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
                        synapse.transmitter -= configuration.excitatoryDecayingRatio;
                        break;

                    case INHIBITORY:
                        synapse.transmitter += configuration.inhibitoryDecayingRatio;
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