package av.is.aegis.test;

import av.is.aegis.Network;
import av.is.aegis.NetworkBuilder;

public class FullNegativeConfigurationTest {

    public static void main(String[] args) {
        NetworkBuilder.builder()
                .inputs(2).inters(3).outputs(1)
                .visualize(false)
                .configure(configuration -> {
                    configuration.synapseDecaying = false;
                    configuration.synapseReinforcing = false;

                    configuration.loggers.memory = false;
                    configuration.loggers.awaitingStimulationQueue = false;
                    configuration.loggers.markedNeurons = false;
                    configuration.loggers.stimulations = false;

                    configuration.visualization.stimulateInputOnly = true;

                    configuration.visualization.visibility.synapseVisible = Network.SynapseVisibility.NONE;
                    configuration.visualization.visibility.stimulationVisible = false;
                    configuration.visualization.visibility.frameVisible = false;
                    configuration.visualization.visibility.inputCellVisible = false;
                    configuration.visualization.visibility.interCellVisible = false;
                    configuration.visualization.visibility.outputCellVisible = false;
                    configuration.visualization.visibility.outputValueVisible = false;
                    configuration.visualization.visibility.mouseRangeVisible = false;
                }).build().start();
    }

}
