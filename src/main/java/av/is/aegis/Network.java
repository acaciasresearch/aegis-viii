package av.is.aegis;

import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.logging.Logger;

public class Network {

    private static final Random NEURON_CHOOSER = new Random();

    private static final Logger LOGGER = Logger.getLogger("AEGIS-VIII");

    private static final int INPUT_NEURON_MAX_SYNAPSES = 20;
    private static final int INTER_NEURON_MAX_SYNAPSES = 100;
    private static final int THREAD_POOL_SIZE = 50;
    private static final int TICK_DIVISION = 5;

    private final Neuron[] neurons;
    private final Neuron[] inputNeurons;
    private final int size;
    private final ThreadPoolExecutor threadPoolExecutor;

    private Neuron[] markedNeurons;

    public Network(int inputs, int neurons) {
        System.setProperty("java.util.logging.SimpleFormatter.format", "[%1$tF %1$tT] [%3$s] [%4$-7s] %5$s %n");

        this.inputNeurons = new Neuron[inputs];
        this.neurons = new Neuron[neurons];
        this.size = neurons;
        this.threadPoolExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        this.markedNeurons = new Neuron[0];
    }

    private void allocateNeurons() {
        allocateNeuronsInternal(neurons);
    }

    private void allocateInputNeurons() {
        allocateNeuronsInternal(inputNeurons);
    }

    private void allocateNeuronsInternal(Neuron[] neurons) {
        int checkpoint = size / 10;

        LOGGER.info("Allocate " + neurons.length + " neurons.");
        for(int i = 0; i < neurons.length; i++) {
            neurons[i] = new Neuron(threadPoolExecutor);

            if(i != 0 && i % checkpoint == 0) {
                LOGGER.info(i + " iterator to go.");
            }
        }
        LOGGER.info("Successfully allocated " + neurons.length + " neurons.");
    }

    private synchronized void mark(Neuron neuron) {
        if(neuron.isMarked()) {
            return;
        }
        markedNeurons = Arrays.copyOf(markedNeurons, markedNeurons.length + 1);
        markedNeurons[markedNeurons.length - 1] = neuron;

        neuron.setMarked(true);
    }

    private void createConnection() {
        if(markedNeurons.length == 0) {
            LOGGER.info("No marked neurons. Setup and mark input neurons");
            for(int i = 0; i < inputNeurons.length; i++) {
                Neuron neuron = inputNeurons[i];
                mark(neuron);
            }
        }

        LOGGER.info("Starting thread for connect input neurons to inter neurons.");
        new Thread(() -> {
            while(true) {
                for(int i = 0; i < inputNeurons.length; i++) {
                    Neuron neuron = inputNeurons[i];
                    if(neuron.needConnection(INPUT_NEURON_MAX_SYNAPSES)) {
                        createSynapse(neuron, chooseNeuron());
                    }
                }
            }
        }).start();

        LOGGER.info("Starting thread for self-oraganizing inter neuron map.");
        new Thread(() -> {
            while(true) {
                Neuron neuron = chooseMarkedNeuron();
                if(neuron.needConnection(INTER_NEURON_MAX_SYNAPSES)) {
                    createSynapse(neuron, chooseNeuron());
                }
            }
        }).start();
    }

    private void createSynapse(Neuron origin, Neuron other) {
        Synapse synapse = new Synapse();
        if(Math.random() > 0.8d) {
            synapse.synapseType = SynapseType.EXCITATORY;
        } else {
            synapse.synapseType = SynapseType.INHIBITORY;
        }
        synapse.transmitter = 1d;

        origin.createConnection(other, synapse);
        mark(other);
    }

    private Neuron chooseNeuron() {
        return neurons[NEURON_CHOOSER.nextInt(size)];
    }

    private synchronized Neuron chooseMarkedNeuron() {
        return markedNeurons[NEURON_CHOOSER.nextInt(markedNeurons.length)];
    }

    public void startTick() {
        for(int i = 0; i < TICK_DIVISION; i++) {
            int finalI = i;
            new Thread(() -> {
                int len = markedNeurons.length;
                int piece = len / TICK_DIVISION;

                int start = finalI * piece;
                int end;
                if(finalI == TICK_DIVISION - 1) {
                    end = len;
                } else {
                    end = finalI * piece + piece;
                }

                while(true) {
                    for(int j = start; j < end; j++) {
                        Neuron neuron = markedNeurons[j];
                        neuron.tick();
                    }
                }
            }).start();
        }
    }

    public void buildUp() {
        allocateNeurons();
        allocateInputNeurons();
        memoryInfo();
        markedNeuronsInfo();
        inQueueInPoolInfo();
        createConnection();
        startTick();

        new Thread(() -> {
            boolean memoryInfo = false;
            while(true) {
                try {
                    Thread.sleep(5000L);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                memoryInfo = !memoryInfo;

                LOGGER.info("");
                markedNeuronsInfo();
                inQueueInPoolInfo();

                if(memoryInfo) {
                    memoryInfo();
                }
                LOGGER.info("");
            }
        }).start();
    }

    // ==========================
    // == Information handlers ==
    // ==========================

    private void memoryInfo() {
        Runtime runtime = Runtime.getRuntime();

        double maxMemory = runtime.maxMemory() / 1024d / 1024d;
        double freeMemory = runtime.freeMemory() / 1024d / 1024d;
        double inUseMemory = (runtime.maxMemory() - runtime.freeMemory()) / 1024d / 1024d;

        DecimalFormat format = new DecimalFormat("#.##");

        LOGGER.info("======== Memory Info ========");
        LOGGER.info("Max   Memory: " + format.format(maxMemory) + "MB");
        LOGGER.info("Free  Memory: " + format.format(freeMemory) + "MB");
        LOGGER.info("Total Memory: " + format.format(inUseMemory) + "MB");
        LOGGER.info("=============================");
    }

    private void markedNeuronsInfo() {
        LOGGER.info("Marked neurons: " + markedNeurons.length);
    }

    private void inQueueInPoolInfo() {
        LOGGER.info("Awaiting stimulation queue: " + threadPoolExecutor.getQueue().size());
    }
}
