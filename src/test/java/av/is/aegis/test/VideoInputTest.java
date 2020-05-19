package av.is.aegis.test;

import av.is.aegis.Network;
import av.is.aegis.NetworkBuilder;
import av.is.aegis.NetworkForm;
import studio.avis.juikit.Juikit;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.logging.Logger;

public class VideoInputTest {

    private static final Logger LOGGER = Logger.getLogger("Video input test");

    private static final int WIDTH = 28;
    private static final int HEIGHT = 28;
    private static final int VOLUME = WIDTH * HEIGHT;

    private static final int[][] OUTPUT_VIEW = new int[WIDTH][HEIGHT];
    private static final int[][] GANGLION_VIEW = new int[WIDTH][HEIGHT];

    private static final int DATASET_VOLUME = 10000;
    private static final BufferedImage[] DATASET = new BufferedImage[DATASET_VOLUME];

    private static final int BLACK_RGB = Color.BLACK.getRGB(); // -16777216

    private static int DATASET_INDEX = 0;

    public static void main(String args[]) throws IOException {
        NetworkForm form = NetworkBuilder.builder()
                .inputs(VOLUME)
                .inters(3000)
                .outputs(VOLUME)
                .visualize(true)
                .configure(configuration -> {
                    configuration.visualization.visibility.synapseVisible = Network.SynapseVisibility.NONE;

                    configuration.inhibitorySynapseCreationChance = 0.5d;

                    configuration.threadSizeForTicking = 100;
                    configuration.threadPoolSize = 100;
                })
                .build();

        form.start();

        File directory = new File("video/MNIST");

        LOGGER.info("Loading " + DATASET_VOLUME + " MNIST datasets...");
        for(int i = 1; i <= DATASET_VOLUME; i++) {
            BufferedImage image = ImageIO.read(new File(directory, "MNIST_" + i + ".png"));
            DATASET[i - 1] = image;

            if(i % 1000 == 0) {
                LOGGER.info("Loaded " + i + " MNIST datasets...");
            }
        }
        LOGGER.info("Completed loading " + DATASET.length + " MNIST datasets.");

        form.outputListener((neuron, value) -> {
            int index = neuron.getIndex();
            if(index >= VOLUME) {
                return;
            }

            int y = index / WIDTH;
            int x = index - (y * WIDTH);

            OUTPUT_VIEW[x][y] = (int) (value * 10000);
        });

        form.ganglionListener((neuron, value) -> {
            int index = neuron.getIndex();
            if(index >= VOLUME) {
                return;
            }

            int y = index / WIDTH;
            int x = index - (y * WIDTH);

            GANGLION_VIEW[x][y] = (int) (value * 10000);
        });

        // Output Neurons Viewer
        Juikit.createFrame()
                .size(WIDTH * 10, HEIGHT * 10)
                .centerAlign()
                .resizable(false)
                .closeOperation(WindowConstants.EXIT_ON_CLOSE)
                .title("Output Neurons")
                .repaintInterval(10L)
                .painter((juikit, graphics) -> {
                    for(int x = 0; x < WIDTH; x++) {
                        for(int y = 0; y < HEIGHT; y++) {
                            graphics.setColor(new Color(OUTPUT_VIEW[x][y]));
                            graphics.fillRect(x * 10, y * 10, 10, 10);
                        }
                    }
                })
                .visibility(true);

        // Ganglion Neurons Viewer
        Juikit.createFrame()
                .size(WIDTH * 10, HEIGHT * 10)
                .centerAlign()
                .resizable(false)
                .closeOperation(WindowConstants.EXIT_ON_CLOSE)
                .title("Ganglion Neurons")
                .repaintInterval(10L)
                .painter((juikit, graphics) -> {
                    for(int x = 0; x < WIDTH; x++) {
                        for(int y = 0; y < HEIGHT; y++) {
                            graphics.setColor(new Color(GANGLION_VIEW[x][y]));
                            graphics.fillRect(x * 10, y * 10, 10, 10);
                        }
                    }
                })
                .visibility(true);

        // Retina Viewer
        Juikit.createFrame()
                .size(WIDTH * 10, HEIGHT * 10)
                .centerAlign()
                .resizable(false)
                .closeOperation(WindowConstants.EXIT_ON_CLOSE)
                .title("Retina (Video)")
                .repaintInterval(10L)
                .painter((juikit, graphics) -> {
                    Image image = DATASET[DATASET_INDEX];
                    if(image != null) {
                        graphics.drawImage(image, 0, 0, WIDTH * 10, HEIGHT * 10, juikit.panel());
                    }
                })
                .visibility(true);

        new Thread(() -> {
            while(true) {
                if(form.getAwaitingStimulationQueues() > 0) {
                    continue;
                }
                try {
                    Thread.sleep(30L);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                if(DATASET_INDEX + 1 >= DATASET_VOLUME) {
                    LOGGER.info("The video has finished. Return to index 0");
                    DATASET_INDEX = 0;
                    break;
                } else {
                    DATASET_INDEX += 1;
                }
            }
        }).start();

        new Thread(() -> {
            while(true) {
                if(form.getAwaitingStimulationQueues() > 0) {
                    continue;
                }
                try {
                    Thread.sleep(30L);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                BufferedImage image = DATASET[DATASET_INDEX];
                if(image != null) {
                    int[] pixels;

                    if(image.getRaster().getDataBuffer() instanceof DataBufferByte) {
                        byte[] pixelData = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
                        ByteBuffer buffer = ByteBuffer.wrap(pixelData).order(ByteOrder.LITTLE_ENDIAN);
                        pixels = new int[pixelData.length / 4];
                        buffer.asIntBuffer().put(pixels);
                    } else {
                        pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
                    }

                    int len = pixels.length;
                    for(int i = 0; i < len; i++) {
                        form.stimulate(i, pixels[i] - BLACK_RGB);
                    }
                }
            }
        }).start();
    }

}
