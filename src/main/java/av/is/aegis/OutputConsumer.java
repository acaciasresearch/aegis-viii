package av.is.aegis;

@FunctionalInterface
public interface OutputConsumer {

    void accept(Neuron neuron, double value);

}
