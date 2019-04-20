package av.is.aegis.test.serialize;

import av.is.aegis.NetworkForm;
import av.is.aegis.NetworkLoader;

import java.io.File;
import java.util.logging.Logger;

public class SerializationReadTest {

    private static final Logger LOGGER = Logger.getLogger("SerializationReadTest");

    public static void main(String[] args) {
        NetworkLoader loader = new NetworkLoader(new File("aegis/serialization-test.aegis"));

        NetworkForm form = loader.load();
        LOGGER.info("Synapse Reinfocing: " + form.config().synapseReinforcing);
        LOGGER.info("Synapse Decaying: " + form.config().synapseDecaying);

        form.setVisualization(true);

        loader.start();
    }

}
