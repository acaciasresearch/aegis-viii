package av.is.aegis.test;

import av.is.aegis.Network;
import av.is.aegis.NetworkBuilder;
import av.is.aegis.NetworkForm;
import avis.juikit.Juikit;
import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamResolution;
import org.imgscalr.Scalr;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.AbstractMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

/**
 * Require OpenCV 3
 */
public class WebcamInputTest {

    private static final Logger LOGGER = Logger.getLogger("Webcam input test");
    private static final AtomicReference<Image> IMAGE = new AtomicReference<>();
    private static final AtomicReference<Juikit> JUIKIT = new AtomicReference<>();

    private static final Map<Map.Entry<Integer, Integer>, Integer> OUTPUT_MAP = new ConcurrentHashMap<>();
    private static final Map<Map.Entry<Integer, Integer>, Integer> GANGLION_MAP = new ConcurrentHashMap<>();

    private static final int BLACK_RGB = Color.BLACK.getRGB(); // -16777216

    public static void main(String[] args) {
        Dimension resolution = WebcamResolution.QQVGA.getSize();

        Webcam webcam = Webcam.getDefault();
        webcam.setViewSize(resolution);

        NetworkForm form = NetworkBuilder.builder()
                .inputs(resolution.width * resolution.height / 4)
                .inters(10)
                .outputs(resolution.width * resolution.height / 4)
                .visualize(true)
                .configure(configuration -> {
                    configuration.visualization.visibility.synapseVisible = Network.SynapseVisibility.NONE;

                    configuration.inhibitorySynapseCreationChance = 0.5d;

                    configuration.threadSizeForTicking = 100;
                    configuration.threadPoolSize = 100;
                })
                .build();

        form.start();

        LOGGER.info("Window Resolution: (" + resolution.width + "x" + resolution.height + " - " + (resolution.width * resolution.height) + "px)");

        webcam.open();

        Juikit.createFrame()
                .size(resolution.width, resolution.height)
                .centerAlign()
                .resizable(false)
                .closeOperation(WindowConstants.EXIT_ON_CLOSE)
                .title("Webcam-Neural Network Output Testing")
                .repaintInterval(10L)
                .painter((juikit, graphics) -> {
                    for(Map.Entry<Map.Entry<Integer, Integer>, Integer> entry : OUTPUT_MAP.entrySet()) {
                        Map.Entry<Integer, Integer> grid = entry.getKey();

                        int x = grid.getKey() * 2;
                        int y = grid.getValue() * 2;

                        graphics.setColor(new Color(entry.getValue()));
                        graphics.drawRect(x, y, 1, 1);
                    }
                })
                .visibility(true);

        Juikit.createFrame()
                .size(resolution.width, resolution.height)
                .centerAlign()
                .resizable(false)
                .closeOperation(WindowConstants.EXIT_ON_CLOSE)
                .title("Webcam-Neural Network Ganglion Testing")
                .repaintInterval(10L)
                .painter((juikit, graphics) -> {
                    for(Map.Entry<Map.Entry<Integer, Integer>, Integer> entry : GANGLION_MAP.entrySet()) {
                        Map.Entry<Integer, Integer> grid = entry.getKey();

                        int x = grid.getKey() * 2;
                        int y = grid.getValue() * 2;

                        graphics.setColor(new Color(entry.getValue()));
                        graphics.drawRect(x, y, 1, 1);
                    }
                })
                .visibility(true);

        int halfWidth = resolution.width / 2;

        form.outputListener((neuron, value) -> {
            int index = neuron.getIndex();

            int y = index / halfWidth;
            int x = index - (y * halfWidth);

            OUTPUT_MAP.put(new AbstractMap.SimpleEntry<>(x, y), (int) (value * 10000));
        });

        form.ganglionListener((neuron, value) -> {
            int index = neuron.getIndex();

            int y = index / halfWidth;
            int x = index - (y * halfWidth);

            GANGLION_MAP.put(new AbstractMap.SimpleEntry<>(x, y), (int) value);
        });

        new Thread(() -> {
            while(true) {
                BufferedImage image = webcam.getImage();
                if(image != null) {
                    if(JUIKIT.get() == null) {
                        JUIKIT.set(createFrame(image.getWidth(), image.getHeight()));
                    }
                    IMAGE.set(Scalr.resize(image, Scalr.Method.SPEED, Scalr.Mode.FIT_EXACT, image.getWidth() / 2, image.getHeight() / 2));
                } else {
                    LOGGER.info("No captured frame from Webcam (Disconnected?)");
                    break;
                }
            }
        }).start();

        new Thread(() -> {
            while(true) {
                BufferedImage image = (BufferedImage) IMAGE.get();
                if(image == null) {
                    continue;
                }
                int width = image.getWidth();
                int height = image.getHeight();

                for(int x = 0; x < width; x++) {
                    for(int y = 0; y < height; y++) {
                        int index = x + width * y;
                        int rgb = image.getRGB(x, y) - BLACK_RGB;

                        form.stimulate(index, rgb);
                    }
                }
            }
        }).start();
    }

    private static Juikit createFrame(int width, int height) {
        return Juikit.createFrame()
                .size(width, height)
                .centerAlign()
                .resizable(false)
                .closeOperation(WindowConstants.EXIT_ON_CLOSE)
                .title("Webcam Input Testing")
                .repaintInterval(10L)
                .painter((juikit, graphics) -> {
                    Image image = IMAGE.get();
                    if(image != null) {
                        graphics.drawImage(image, 0, 0, width, height, juikit.panel());
                    }
                })
                .visibility(true);
    }

}
