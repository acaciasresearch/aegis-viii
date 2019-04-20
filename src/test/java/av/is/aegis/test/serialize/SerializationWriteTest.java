package av.is.aegis.test.serialize;

import av.is.aegis.NetworkBuilder;
import av.is.aegis.NetworkForm;

import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;

public class SerializationWriteTest {

    private static final Logger LOGGER = Logger.getLogger("SerializationWriteTest");

    public static void main(String[] args) {
        NetworkForm form = NetworkBuilder.builder()
                .inputs(5)
                .inters(100)
                .outputs(10)
                .visualize(true)
                .configure(configuration -> {
                    configuration.synapseReinforcing = false;
                    configuration.synapseDecaying = false;
                })
                .build();

        form.start();
        new Thread(() -> {
            try {
                Thread.sleep(5000L);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            LOGGER.info("Saving network as a file...");
            try {
                form.write(new File("aegis/serialization-test.aegis"));
            } catch (IOException e) {
                e.printStackTrace();
            }
            LOGGER.info("Saved network as a file.");
        }).start();
    }

}
