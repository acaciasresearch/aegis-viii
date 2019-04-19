package av.is.aegis.test;

import av.is.aegis.Network;
import av.is.aegis.NetworkBuilder;
import av.is.aegis.NetworkForm;

public class FreeTest {

    public static void main(String[] args) {
        NetworkForm form = NetworkBuilder.builder()
                .inputs(3).inters(100).outputs(2)
                .visualize(true)
                .configure(configuration -> {
            configuration.visualization.visibility.synapseVisible = Network.SynapseVisibility.STRONG_ONLY;
            configuration.visualization.stimulateInputOnly = true;

            configuration.loggers.stimulations = false;
            configuration.loggers.memory = false;
            configuration.loggers.awaitingStimulationQueue = false;
            configuration.loggers.markedNeurons = false;
        }).build();

        form.start();
    }
}