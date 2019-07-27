package av.is.aegis;

import java.io.File;
import java.io.IOException;

public interface NetworkForm {

    Network.Configuration config();

    void stimulate(int inputNeuronIndex, double stimulation);

    void outputListener(OutputConsumer consumer);

    void ganglionListener(OutputConsumer consumer);

    void setVisualization(boolean visualization);

    void setOutputGraph(boolean outputGraph);

    void start();

    void suppress(SynapseType synapseType);

    void grow(SynapseType synapseType);

    void suppressOf(Neuron neuron, SynapseType synapseType);

    void growOf(Neuron neuron, SynapseType synapseType);

    int getAwaitingStimulationQueues();

    void write(File file) throws IOException;

}
