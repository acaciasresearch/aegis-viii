package av.is.aegis;

import java.io.File;
import java.io.IOException;

public interface NetworkForm {

    Network.Configuration config();

    void stimulate(int inputNeuronIndex, double stimulation);

    void outputListener(OutputConsumer consumer);

    void ganglionListener(OutputConsumer consumer);

    void setVisualization(boolean visualization);

    void start();

    void supress(SynapseType synapseType);

    void grow(SynapseType synapseType);

    void write(File file) throws IOException;

}
