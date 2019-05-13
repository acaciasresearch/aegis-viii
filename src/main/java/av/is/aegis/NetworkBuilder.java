package av.is.aegis;

import java.util.function.Consumer;

public class NetworkBuilder {

    public static NetworkBuilder builder() {
        return new NetworkBuilder();
    }

    private int inputNeurons;
    private int interNeurons;
    private int outputNeurons;
    private boolean visualize;
    private boolean outputGraph;
    private Network.Configuration configuration = new Network.Configuration();

    private NetworkBuilder() {
    }

    public NetworkBuilder inputs(int inputNeurons) {
        this.inputNeurons = inputNeurons;
        return this;
    }

    public NetworkBuilder inters(int interNeurons) {
        this.interNeurons = interNeurons;
        return this;
    }

    public NetworkBuilder outputs(int outputNeurons) {
        this.outputNeurons = outputNeurons;
        return this;
    }

    public NetworkBuilder visualize(boolean visualize) {
        this.visualize = visualize;
        return this;
    }

    public NetworkBuilder outputGraph(boolean outputGraph) {
        this.outputGraph = outputGraph;
        return this;
    }

    public NetworkBuilder configure(Consumer<Network.Configuration> consumer) {
        consumer.accept(configuration);
        return this;
    }

    public NetworkForm build() {
        if(inputNeurons == 0) {
            throw new IllegalArgumentException("Input neurons cannot be zero.");
        }
        if(interNeurons == 0) {
            throw new IllegalArgumentException("Inter neurons cannot be zero.");
        }
        if(outputNeurons == 0) {
            throw new IllegalArgumentException("Output neurons cannot be zero.");
        }
        Network network = new Network(inputNeurons, interNeurons, outputNeurons);
        if(visualize) {
            network.setVisualization(true);
        }
        if(outputGraph) {
            network.setOutputGraph(true);
        }
        network.migrateConfiguration(configuration);
        return network;
    }

}
