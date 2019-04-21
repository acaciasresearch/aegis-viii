package av.is.aegis;

import avis.juikit.Juikit;
import com.google.common.collect.Streams;
import com.google.common.util.concurrent.AtomicDouble;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseWheelEvent;
import java.io.*;
import java.text.DecimalFormat;
import java.util.List;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.logging.Logger;
import java.util.stream.Stream;

public class Network implements NetworkForm, Serializable {

    private static final Random NEURON_CHOOSER = new Random();

    private static final Logger LOGGER = Logger.getLogger("AEGIS-VIII-NETWORK");

    private static final int VIS_INDENT = 10;
    private static final int VIS_SQUARE_WIDTH = 10;
    private static final int VIS_SQUARE_WIDTH_HALF = VIS_SQUARE_WIDTH / 2;
    private static final int VIS_SQUARE_HEIGHT = 10;
    private static final int VIS_SQUARE_HEIGHT_HALF = VIS_SQUARE_HEIGHT / 2;
    private static final int VIS_SQUARE_BETWEEN_DISTANCE = 4;

    private static final int THREAD_POOL_SIZE = 50;
    private static final int TICK_DIVISION = 5;

    private static final long serialVersionUID = -6723275806349768869L;

    private final Neuron[] neurons;
    private final Neuron[] inputNeurons;
    private final Neuron[] outputNeurons;
    private final int size;
    private transient ThreadPoolExecutor threadPoolExecutor;

    private Neuron[] markedNeurons;

    private transient boolean visualization = false;
    private List<Neuron.Coordinate> neuronCoordinates = new CopyOnWriteArrayList<>();
    private Map<Neuron, AtomicDouble> outputValues = new ConcurrentHashMap<>();

    private Configuration configuration;

    private transient OutputConsumer consumer;

    private transient boolean loaded;
    private transient int threadSize;

    public static class Configuration implements Serializable {

        private static final long serialVersionUID = 5562270942390906477L;

        public int maxSynapsesForInputNeurons = 10;
        public int maxSynapsesForInterNeurons = 20;

        public boolean synapseDecaying = true;
        public boolean synapseReinforcing = true;

        public double excitatorySuppressionRatio = 0.001d;
        public double inhibitorySuppressionRatio = 0.001d;

        public double excitatoryGrowRatio = 0.001d;
        public double inhibitoryGrowRatio = 0.001d;

        public double excitatoryMaximumStrength = 25d;
        public double inhibitoryMaximumStrength = -25d;

        public double excitatoryReinforcementRatio = 0.01d;
        public double inhibitoryReinforcementRatio = 0.01d;

        public double excitatoryDecayingRatio = 0.00001d;
        public double inhibitoryDecayingRatio = 0.00001d;

        public long delayOnQueueStimulation = 0L;
        public long delayOnNetworkTicking = 0L;

        public double epspMultiply = 1.06271d;
        public double ipspMultiply = 1.06271d;

        public int threadPoolSize = 50;
        public int threadSizeForTicking = 5;

        public double inhibitorySynapseCreationChance = 0.8d;

        public final Visualization visualization = Visualization.Lazy.INSTANCE;

        public static class Visualization implements Serializable {

            private static final long serialVersionUID = 1962942946919965233L;

            static class Lazy {

                static final Visualization INSTANCE = new Visualization();

            }

            public final Visibility visibility = Visibility.Lazy.INSTANCE;

            public static class Visibility implements Serializable {

                private static final long serialVersionUID = -6174597472996071716L;

                static class Lazy {

                    static final Visibility INSTANCE = new Visibility();

                }

                public SynapseVisibility synapseVisible = SynapseVisibility.ALL;
                public boolean inputCellVisible = true;
                public boolean interCellVisible = true;
                public boolean outputCellVisible = true;
                public boolean outputValueVisible = true;
                public boolean stimulationVisible = true;
                public boolean frameVisible = true;
                public boolean mouseRangeVisible = true;

            }

            public boolean stimulateInputOnly = false;

        }

        public final Loggers loggers = Loggers.Lazy.INSTANCE;

        public static class Loggers implements Serializable {

            private static final long serialVersionUID = -3067731846695866949L;

            static class Lazy {

                static final Loggers INSTANCE = new Loggers();

            }

            public boolean stimulations = true;
            public boolean memory = true;
            public boolean markedNeurons = true;
            public boolean awaitingStimulationQueue = true;
            public boolean currentWorkingThreads = true;

        }

    }

    public enum SynapseVisibility {
        ALL,
        STRONG_ONLY,
        LIFETIME_ONLY,
        NONE
    }

    Network(int inputs, int neurons, int outputs) {
        this.inputNeurons = new Neuron[inputs];
        this.neurons = new Neuron[neurons];
        this.size = this.neurons.length;
        this.outputNeurons = new Neuron[outputs];

        this.markedNeurons = new Neuron[0];
        this.configuration = new Configuration();

        staticSetup();
    }

    private void staticSetup() {
        int threadPoolSize = configuration.threadPoolSize;
        if(threadPoolSize == 0) {
            threadPoolSize = THREAD_POOL_SIZE;
        }

        System.setProperty("java.util.logging.SimpleFormatter.format", "[%1$tF %1$tT] [%3$-25s] [%4$-7s] %5$s %n");
        ThreadBuilder.incrementThreadsForStatistic(threadPoolSize);

        this.threadPoolExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(threadPoolSize);
    }

    void setLoaded() {
        this.loaded = true;
        staticSetup();
    }

    void startFromLoaded() {
        LOGGER.info("Starting AEGIS loaded from file...");
        for(Neuron neuron : inputNeurons) {
            neuron.setExecutorService(threadPoolExecutor);

            neuron.load(inputNeurons);
            neuron.load(neurons);
            neuron.load(outputNeurons);
        }
        for(Neuron neuron : neurons) {
            neuron.setExecutorService(threadPoolExecutor);

            neuron.load(inputNeurons);
            neuron.load(neurons);
            neuron.load(outputNeurons);
        }
        for(Neuron neuron : outputNeurons) {
            neuron.setExecutorService(threadPoolExecutor);

            neuron.load(inputNeurons);
            neuron.load(neurons);
            neuron.load(outputNeurons);
        }

        Streams.concat(Stream.of(inputNeurons), Stream.of(neurons), Stream.of(outputNeurons)).forEach(Neuron::clearUnusedReferences);
        buildUp();
    }

    @Override
    public void setVisualization(boolean visualization) {
        boolean old = this.visualization;
        this.visualization = visualization;

        if(!old && visualization) {
            visualizeNetwork();
        }
    }

    @Override
    public void start() {
        if(loaded) {
            LOGGER.severe("Loaded network cannot be started by NetworkForm#start() method. Use NetworkLoader#start() instead.");
            return;
        }
        buildUp();
    }

    @Override
    public synchronized void supress(SynapseType synapseType) {
        for(Neuron neuron : markedNeurons) {
            neuron.suppress(synapseType);
        }
    }

    @Override
    public void grow(SynapseType synapseType) {
        for(Neuron neuron : markedNeurons) {
            neuron.grow(synapseType);
        }
    }

    @Override
    public void write(File file) throws IOException {
        loadedResourceInfo();

        try(FileOutputStream fileOutputStream = new FileOutputStream(file)) {
            try(ObjectOutputStream outputStream = new ObjectOutputStream(fileOutputStream)) {
                outputStream.writeObject(this);
            }
        }
    }

    void migrateConfiguration(Configuration configuration) {
        this.configuration = configuration;
    }

    @Override
    public Configuration config() {
        return configuration;
    }

    @Override
    public void stimulate(int inputNeuronIndex, double stimulation) {
        if(inputNeuronIndex > inputNeurons.length) {
            throw new IllegalArgumentException("Input neurons size is " + inputNeurons.length + "(max index: " + (inputNeurons.length - 1) + "), but Index #" + inputNeuronIndex + " received instead.");
        }
        inputNeurons[inputNeuronIndex].recept(stimulation);
    }

    @Override
    public void outputListener(OutputConsumer consumer) {
        this.consumer = consumer;
    }

    private void visualizeNetwork() {
        Juikit.createFrame()
                .title("AEGIS-VIII")
                .size(1000, 1000)
                .centerAlign()
                .background(Color.DARK_GRAY)
                .repaintInterval(10L)
                .closeOperation(WindowConstants.EXIT_ON_CLOSE)
                .data(1, 0)
                .data("MOUSE_X", 0)
                .data("MOUSE_Y", 0)
                .data("WHEEL_ROT", 0)
                .painter((juikit, graphics) -> {
                    if(configuration.visualization.visibility.frameVisible) {
                        int processed = juikit.data(1);
                        juikit.data(1, processed + 1);

                        graphics.setColor(Color.WHITE);
                        graphics.drawString("Frame: " + processed, VIS_INDENT, juikit.height() - VIS_INDENT * 3);
                    }

                    int inputIndex = 0;
                    for(Neuron neuron : inputNeurons) {
                        if(neuron == null) {
                            continue;
                        }

                        int x = VIS_INDENT + (inputIndex * (VIS_SQUARE_WIDTH + VIS_SQUARE_BETWEEN_DISTANCE));
                        int y = VIS_INDENT;

                        neuron.setCoordinate(x, y);

                        if(!neuronCoordinates.contains(neuron.getCoordinate())) {
                            neuronCoordinates.add(neuron.getCoordinate());
                        }

                        Set<Neuron> others = neuron.connectedTo(configuration.visualization.visibility.synapseVisible);
                        for(Neuron other : others) {
                            Neuron.Coordinate coordinate = other.getCoordinate();
                            if(coordinate == null) {
                                continue;
                            }
                            graphics.setColor(Color.BLACK);
                            graphics.drawLine(x + VIS_SQUARE_WIDTH_HALF, y + VIS_SQUARE_HEIGHT_HALF, coordinate.x + VIS_SQUARE_WIDTH_HALF, coordinate.y + VIS_SQUARE_HEIGHT_HALF);
                        }

                        if(configuration.visualization.visibility.inputCellVisible) {
                            Color color;
                            if(configuration.visualization.visibility.stimulationVisible) {
                                color = new Color(0, (int) Math.max(0, Math.min(255, (neuron.getSum().get() - Neuron.STABLE_POTENTIAL) * 2 + 180)), 0);
                            } else {
                                color = new Color(0, 180, 0);
                            }
                            graphics.setColor(color);
                            graphics.fillRect(x, y, VIS_SQUARE_WIDTH, VIS_SQUARE_HEIGHT);
                        }

                        inputIndex++;
                    }

                    int division = (int) Math.sqrt(size);
                    int neuronX = 0;
                    int neuronY = 0;

                    for(Neuron neuron : neurons) {
                        if(neuron == null) {
                            continue;
                        }

                        int x = VIS_INDENT + (neuronX * (VIS_SQUARE_WIDTH + VIS_SQUARE_BETWEEN_DISTANCE));
                        int y = VIS_INDENT + (neuronY * (VIS_SQUARE_HEIGHT + VIS_SQUARE_BETWEEN_DISTANCE)) + 20;

                        neuron.setCoordinate(x, y);

                        if(!configuration.visualization.stimulateInputOnly) {
                            if(!neuronCoordinates.contains(neuron.getCoordinate())) {
                                neuronCoordinates.add(neuron.getCoordinate());
                            }
                        }

                        Set<Neuron> others = neuron.connectedTo(configuration.visualization.visibility.synapseVisible);
                        for(Neuron other : others) {
                            Neuron.Coordinate coordinate = other.getCoordinate();
                            if(coordinate == null) {
                                continue;
                            }

                            graphics.setColor(Color.BLACK);
                            graphics.drawLine(x + VIS_SQUARE_WIDTH_HALF, y + VIS_SQUARE_HEIGHT_HALF, coordinate.x + VIS_SQUARE_WIDTH_HALF, coordinate.y + VIS_SQUARE_HEIGHT_HALF);
                        }

                        if(configuration.visualization.visibility.interCellVisible) {
                            Color color;
                            if(configuration.visualization.visibility.stimulationVisible) {
                                color = new Color(0, (int) Math.max(0, Math.min(255, (neuron.getSum().get() - Neuron.STABLE_POTENTIAL) * 2 + 180)), 0);
                            } else {
                                color = new Color(0, 180, 0);
                            }
                            graphics.setColor(color);
                            graphics.fillRect(x, y, VIS_SQUARE_WIDTH, VIS_SQUARE_HEIGHT);
                        }

                        neuronX++;
                        if(division == neuronX) {
                            neuronX = 0;
                            neuronY++;
                        }
                    }

                    int outputIndex = 0;
                    for(Neuron neuron : outputNeurons) {
                        if(neuron == null) {
                            continue;
                        }

                        int x = VIS_INDENT + (division * (VIS_SQUARE_WIDTH + VIS_SQUARE_BETWEEN_DISTANCE)) + 50;
                        int y = VIS_INDENT + (outputIndex * (VIS_SQUARE_WIDTH + VIS_SQUARE_BETWEEN_DISTANCE)) + 50;

                        neuron.setCoordinate(x, y);

                        Set<Neuron> others = neuron.connectedTo(configuration.visualization.visibility.synapseVisible);
                        for(Neuron other : others) {
                            Neuron.Coordinate coordinate = other.getCoordinate();
                            if(coordinate == null) {
                                continue;
                            }
                            graphics.setColor(Color.BLACK);
                            graphics.drawLine(x + VIS_SQUARE_WIDTH_HALF, y + VIS_SQUARE_HEIGHT_HALF, coordinate.x + VIS_SQUARE_WIDTH_HALF, coordinate.y + VIS_SQUARE_HEIGHT_HALF);
                        }

                        if(configuration.visualization.visibility.outputCellVisible) {
                            Color color;
                            if(configuration.visualization.visibility.stimulationVisible) {
                                color = new Color(0, (int) Math.max(0, Math.min(255, (neuron.getSum().get() - Neuron.STABLE_POTENTIAL) * 2 + 180)), 0);
                            } else {
                                color = new Color(0, 180, 0);
                            }
                            graphics.setColor(color);
                            graphics.fillRect(x, y, VIS_SQUARE_WIDTH, VIS_SQUARE_HEIGHT);
                        }

                        if(configuration.visualization.visibility.outputValueVisible) {
                            graphics.setColor(Color.WHITE);
                            AtomicDouble value = outputValues.get(neuron);
                            if(value == null) {
                                value = new AtomicDouble(0);
                            }
                            graphics.drawString(value.toString(), x + VIS_SQUARE_WIDTH + 10, y + VIS_SQUARE_HEIGHT);
                        }

                        outputIndex++;
                    }

                    if(configuration.visualization.visibility.mouseRangeVisible) {
                        int x = juikit.data("MOUSE_X");
                        int y = juikit.data("MOUSE_Y");

                        int wheel = juikit.data("WHEEL_ROT");

                        graphics.setColor(Color.GRAY);
                        graphics.drawRect(x - wheel, y - wheel, wheel * 2, wheel * 2);
                    }
                })
                .mouseClicked((juikit, mouseEvent) -> {
                    int x = mouseEvent.getX();
                    int y = mouseEvent.getY();

                    int wheel = juikit.data("WHEEL_ROT");

                    for(Neuron.Coordinate coordinate : neuronCoordinates) {
                        if(Math.abs(coordinate.x - x) < wheel && Math.abs(coordinate.y - y) < wheel) {
                            coordinate.neuron.recept(100);
                        }
                    }
                })
                .mouseDragged((juikit, mouseEvent) -> {
                    int x = mouseEvent.getX();
                    int y = mouseEvent.getY();

                    int wheel = juikit.data("WHEEL_ROT");

                    int recepted = 0;
                    for(Neuron.Coordinate coordinate : neuronCoordinates) {
                        if(Math.abs(coordinate.x - x) < wheel && Math.abs(coordinate.y - y) < wheel) {
                            coordinate.neuron.recept(100);
                            recepted++;
                        }
                    }
                    if(configuration.loggers.stimulations) {
                        LOGGER.info("Recepted to " + recepted + " input neurons in " + neuronCoordinates.size() + " neuron coordinates.");
                    }

                    juikit.data("MOUSE_X", x);
                    juikit.data("MOUSE_Y", y);
                })
                .mouseWheelMoved((juikit, mouseEvent) -> {
                    MouseWheelEvent event = (MouseWheelEvent) mouseEvent;

                    int rotation = event.getWheelRotation();
                    int wheel = juikit.data("WHEEL_ROT");

                    juikit.data("WHEEL_ROT", Math.max(0, wheel + rotation));
                })
                .mouseMoved((juikit, mouseEvent) -> {
                    int x = mouseEvent.getX();
                    int y = mouseEvent.getY();

                    juikit.data("MOUSE_X", x);
                    juikit.data("MOUSE_Y", y);
                })
                .visibility(true);
    }

    private void allocateNeurons() {
        LOGGER.info("Allocating inter neurons...");
        allocateNeuronsInternal(neurons);
    }

    private void allocateInputNeurons() {
        LOGGER.info("Allocating input neurons...");
        allocateNeuronsInternal(inputNeurons);
    }

    private void allocateOutputNeurons() {
        LOGGER.info("Allocating output neurons...");
        allocateNeuronsInternal(outputNeurons);

        if(!loaded) {
            for(Neuron neuron : outputNeurons) {
                neuron.setOutput((OutputConsumer & Serializable) (outputNeuron, value) -> {
                    if(consumer != null) {
                        consumer.accept(outputNeuron, value);
                    }

                    if(!outputValues.containsKey(outputNeuron)) {
                        outputValues.put(outputNeuron, new AtomicDouble(value));
                    } else {
                        outputValues.get(outputNeuron).set(value);
                    }
                });
            }
        } else {
            LOGGER.info("Skipping output neuron listeners...");
        }
    }

    private void allocateNeuronsInternal(Neuron[] neurons) {
        if(loaded) {
            LOGGER.info("Skipping allocating neurons...");
            return;
        }
        int checkpoint = size / 10;
        if(checkpoint == 0) {
            checkpoint = 1;
        }

        LOGGER.info("Allocate " + neurons.length + " neurons.");
        for(int i = 0; i < neurons.length; i++) {
            neurons[i] = new Neuron(i, configuration, threadPoolExecutor);

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

        neuron.setMarked();
    }

    private void createConnection() {
        if(markedNeurons.length == 0) {
            LOGGER.info("No marked neurons. Setup and mark input neurons");
            for(Neuron neuron : inputNeurons) {
                mark(neuron);
            }
        }

        LOGGER.info("Starting thread for connect input neurons to inter neurons.");
        ThreadBuilder.builder().name("Neuroplasiticity-INPUT-INTER").runnable(() -> {
            while(true) {
                for(Neuron neuron : inputNeurons) {
                    if (neuron.needConnection(configuration.maxSynapsesForInputNeurons)) {
                        createSynapse(neuron, chooseNeuron());
                    }
                }
            }
        }).start();

        LOGGER.info("Starting thread for self-oraganizing inter neuron map.");
        ThreadBuilder.builder().name("Self-organizing-INTER-INTER").runnable(() -> {
            while(true) {
                Neuron neuron = chooseMarkedNeuron();
                if(neuron.needConnection(configuration.maxSynapsesForInterNeurons)) {
                    createSynapse(neuron, chooseNeuron());
                }
            }
        }).start();

        LOGGER.info("Starting thread for self-organizing inter neuron maps to output neurons.");
        ThreadBuilder.builder().name("Self-organizing-INTER-OUTPUT").runnable(() -> {
            while(true) {
                Neuron neuron = chooseMarkedNeuron();
                if(neuron.needConnection(configuration.maxSynapsesForInterNeurons)) {
                    createSynapse(neuron, chooseOutputNeuron());
                }
            }
        }).start();
    }

    private void createSynapse(Neuron origin, Neuron other) {
        if(origin.isOutputNeuron()) {
            return;
        }
        Synapse synapse = new Synapse();
        if(Math.random() > configuration.inhibitorySynapseCreationChance) {
            synapse.synapseType = SynapseType.EXCITATORY;
            synapse.transmitter = 5d;
        } else {
            synapse.synapseType = SynapseType.INHIBITORY;
            synapse.transmitter = -5d;
        }

        origin.createConnection(other, synapse);
        mark(other);
    }

    private Neuron chooseOutputNeuron() {
        return outputNeurons[NEURON_CHOOSER.nextInt(outputNeurons.length)];
    }

    private Neuron chooseNeuron() {
        return neurons[NEURON_CHOOSER.nextInt(size)];
    }

    private synchronized Neuron chooseMarkedNeuron() {
        return markedNeurons[NEURON_CHOOSER.nextInt(markedNeurons.length)];
    }

    private void startTick() {
        for(int i = 0; i < threadSize; i++) {
            int finalI = i;
            ThreadBuilder.builder().name("Network Ticking - #" + finalI).runnable(() -> {
                while(true) {
                    if(configuration.delayOnNetworkTicking > 0) {
                        try {
                            Thread.sleep(configuration.delayOnNetworkTicking);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    // Variable markedNeurons are mutable.
                    int len = markedNeurons.length;
                    int piece = len / threadSize;

                    int start = finalI * piece;
                    int end;
                    if(finalI == threadSize - 1) {
                        end = len;
                    } else {
                        end = finalI * piece + piece;
                    }

                    for(int j = start; j < end; j++) {
                        Neuron neuron = markedNeurons[j];
                        neuron.tick();
                    }
                }
            }).start();
        }
    }

    private void buildUp() {
        if(loaded) {
            loadedResourceInfo();
        }

        threadSize = configuration.threadSizeForTicking == 0 ? TICK_DIVISION : configuration.threadSizeForTicking;

        allocateInputNeurons();
        allocateNeurons();
        allocateOutputNeurons();
        memoryInfo();
        markedNeuronsInfo();
        inQueueInPoolInfo();
        currentWorkingThreadInifo();
        createConnection();
        startTick();

        ThreadBuilder.builder().name("Network Statistic Logger").runnable(() -> {
            boolean memoryInfo = false;
            while(true) {
                try {
                    Thread.sleep(5000L);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                memoryInfo = !memoryInfo;

                Configuration.Loggers loggers = configuration.loggers;
                if(loggers.markedNeurons || loggers.awaitingStimulationQueue || loggers.memory) {
                    LOGGER.info("");
                    markedNeuronsInfo();
                    inQueueInPoolInfo();
                    currentWorkingThreadInifo();

                    if(memoryInfo) {
                        memoryInfo();
                    }
                    LOGGER.info("");
                }
            }
        }).start();
    }

    private void memoryInfo() {
        if(!configuration.loggers.memory) {
            return;
        }
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
        if(!configuration.loggers.markedNeurons) {
            return;
        }
        LOGGER.info("Marked neurons: " + markedNeurons.length);
    }

    private void inQueueInPoolInfo() {
        if(!configuration.loggers.awaitingStimulationQueue) {
            return;
        }
        LOGGER.info("Awaiting stimulation queue: " + threadPoolExecutor.getQueue().size());
    }

    private void currentWorkingThreadInifo() {
        if(!configuration.loggers.currentWorkingThreads) {
            return;
        }
        LOGGER.info("Current working threads: " + ThreadBuilder.getThreads() + ", Non-countable but active threads: " + ThreadBuilder.getNonCountableThreads());
    }

    private void loadedResourceInfo() {
        LOGGER.info("================= LOADED AEGIS RESOURCES =================");
        LOGGER.info("Inputs: " + inputNeurons.length + ", Inters: " + neurons.length + ", Outputs: " + outputNeurons.length);
        LOGGER.info("Marked: " + markedNeurons.length);
        LOGGER.info("Neuron Coordinates: " + neuronCoordinates.size());

        LOGGER.info("Syanpses: " + Streams.concat(Arrays.stream(inputNeurons), Arrays.stream(neurons), Arrays.stream(outputNeurons)).mapToInt(Neuron::getSynapses).sum());
        LOGGER.info("==========================================================");
    }
}
