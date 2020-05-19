package av.is.aegis.test;

import av.is.aegis.Network;
import av.is.aegis.NetworkBuilder;
import av.is.aegis.NetworkForm;
import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamResolution;
import org.imgscalr.Scalr;
import studio.avis.juikit.Juikit;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferInt;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.AbstractMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

/**
 * Require OpenCV 3
 */
public class WebcamInputTest {

    private static final int DIVISION = 4;

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
                .inputs((resolution.width / DIVISION) * (resolution.height / DIVISION))
                .inters(10000)
                .outputs((resolution.width / DIVISION) * (resolution.height / DIVISION))
//                .visualize(true)
//                .outputGraph(true)
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
                .title("Output Neurons")
                .repaintInterval(10L)
                .painter((juikit, graphics) -> {
                    for(Map.Entry<Map.Entry<Integer, Integer>, Integer> entry : OUTPUT_MAP.entrySet()) {
                        Map.Entry<Integer, Integer> grid = entry.getKey();

                        int x = grid.getKey() * DIVISION;
                        int y = grid.getValue() * DIVISION;

                        graphics.setColor(new Color(entry.getValue()));
                        graphics.fillRect(x, y, DIVISION / 2 + 1, DIVISION / 2 + 1);
                    }
                })
                .visibility(true);

        Juikit.createFrame()
                .size(resolution.width, resolution.height)
                .centerAlign()
                .resizable(false)
                .closeOperation(WindowConstants.EXIT_ON_CLOSE)
                .title("Ganglion Neurons")
                .repaintInterval(10L)
                .painter((juikit, graphics) -> {
                    for(Map.Entry<Map.Entry<Integer, Integer>, Integer> entry : GANGLION_MAP.entrySet()) {
                        Map.Entry<Integer, Integer> grid = entry.getKey();

                        int x = grid.getKey() * DIVISION;
                        int y = grid.getValue() * DIVISION;

                        graphics.setColor(new Color(entry.getValue()));
                        graphics.fillRect(x, y, DIVISION / 2 + 1, DIVISION / 2 + 1);
                    }
                })
                .visibility(true);

        int halfWidth = resolution.width / DIVISION;

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

            GANGLION_MAP.put(new AbstractMap.SimpleEntry<>(x, y), (int) (value * 10000));
        });

        new Thread(() -> {
            while(true) {
                BufferedImage image = webcam.getImage();
                if(image != null) {
                    if(JUIKIT.get() == null) {
                        JUIKIT.set(createFrame(image.getWidth(), image.getHeight()));
                    }
                    if(DIVISION == 1) {
                        IMAGE.set(image);
                    } else {
                        IMAGE.set(Scalr.resize(image, Scalr.Method.SPEED, Scalr.Mode.FIT_EXACT, image.getWidth() / DIVISION, image.getHeight() / DIVISION));
                    }
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

                int[] pixels;

                if(image.getRaster().getDataBuffer() instanceof DataBufferByte) {
                    byte[] pixelData = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
                    ByteBuffer buffer = ByteBuffer.wrap(pixelData).order(ByteOrder.LITTLE_ENDIAN);
                    pixels = new int[pixelData.length / 4];
                    buffer.asIntBuffer().put(pixels);
                } else {
                    pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
                }

                for(int i = 0; i < pixels.length; i++) {
                    form.stimulate(i, pixels[i] - BLACK_RGB);
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
                .title("Retina (Webcam)")
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
