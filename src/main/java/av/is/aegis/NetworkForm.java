package av.is.aegis;

public interface NetworkForm {

    Network.Configuration config();

    void stimulate(int inputNeuronIndex, double stimulation);

    void outputListener(OutputConsumer consumer);

    void setVisualization(boolean visualization);

    void start();

    byte[] serialize();

}
