package av.is.aegis.test.train;

import av.is.aegis.*;
import com.google.common.util.concurrent.AtomicDouble;
import studio.avis.juikit.Juikit;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

public class FollowerTrainingTest {

    // =========================
    // ==== USER PREFERENCE ====
    // =========================
    private static final boolean LOAD_NETWORK = true;

    private static final Logger LOGGER = Logger.getLogger("FOLLOWER (AEGIS-VIII TRAINING)");

    private static final AtomicDouble MOVE_X = new AtomicDouble();
    private static final AtomicDouble MOVE_Y = new AtomicDouble();

    private static final Cursor CURSOR = new Cursor();
    private static final Computer COMPUTER = new Computer();

    private static final int BALL_RADIUS = 20;

    private static final int WIDTH = 500;
    private static final int HEIGHT = 500 - BALL_RADIUS;

    private static NetworkForm createNetworkForm() {
        NetworkForm form = NetworkBuilder.builder()
                .inputs(3)
                .inters(2000)
                .outputs(4)
                .visualize(true)
                .configure(configuration -> {
                    configuration.visualization.visibility.synapseVisible = Network.SynapseVisibility.NONE;
                    configuration.visualization.stimulateInputOnly = true;

                    configuration.inhibitoryReinforcementRatio = 0.0001d;
                    configuration.excitatoryReinforcementRatio = 0.0001d;

                    configuration.inhibitorySynapseCreationChance = 0.5d;

                    configuration.maxSynapsesForInputNeurons = 10;
                    configuration.maxSynapsesForInterNeurons = 50;

                    configuration.threadSizeForTicking = 20;
                    configuration.threadPoolSize = 100;
                }).build();

        form.start();

        return form;
    }

    private static NetworkForm loadNetworkForm() {
        NetworkLoader loader = new NetworkLoader(new File("aegis/follower.aegis"));

        NetworkForm form = loader.load();

        form.config().synapseDecaying = false;
        form.config().synapseReinforcing = false;

        form.setVisualization(true);

        loader.start();

        return loader.load();
    }

    public static void main(String[] args) {
        NetworkForm form;
        if(LOAD_NETWORK) {
            form = loadNetworkForm();
        } else {
            form = createNetworkForm();
        }

        form.outputListener((neuron, value) -> {
            switch (neuron.getIndex()) {
                case 0:
                case 1:
                    MOVE_X.set(Math.max(0, Math.min(WIDTH, MOVE_X.get() + value)));
                    break;

                case 2:
                case 3:
                    MOVE_Y.set(Math.max(0, Math.min(HEIGHT, MOVE_Y.get() + value)));
                    break;
            }
        });

        new Thread(() -> {
            while(true) {
                try {
                    Thread.sleep(10L);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                {
                    int beforeX = COMPUTER.x.get();
                    int moveToX = MOVE_X.intValue();
                    if(beforeX > moveToX) {
                        COMPUTER.tryMoveToX(COMPUTER.x.get() - 2);
                    } else {
                        COMPUTER.tryMoveToX(COMPUTER.x.get() + 2);
                    }

                    int beforeY = COMPUTER.y.get();
                    int moveToY = MOVE_Y.intValue();
                    if(beforeY > moveToY) {
                        COMPUTER.tryMoveToY(COMPUTER.y.get() - 2);
                    } else {
                        COMPUTER.tryMoveToY(COMPUTER.y.get() + 2);
                    }

                    // TODO Distance-base training
                    /*
                    int afterX = COMPUTER.x.get();
                    int afterY = COMPUTER.y.get();

                    int cursorX = CURSOR.x.get();
                    int cursorY = CURSOR.y.get();
                    int beforeDiffX = cursorX - beforeX;
                    int beforeDiffY = cursorY - beforeY;

                    int afterDiffX = cursorX - afterX;
                    int afterDiffY = cursorY - afterY;

                    double beforeDistance = Math.sqrt(beforeDiffX * beforeDiffX + beforeDiffY * beforeDiffY);
                    double afterDistance = Math.sqrt(afterDiffX * afterDiffX + afterDiffY * afterDiffY);

                    if(beforeDistance < afterDistance) {
                        form.suppress(SynapseType.INHIBITORY);
                        form.suppress(SynapseType.EXCITATORY);
                    } else {
                        form.grow(SynapseType.INHIBITORY);
                        form.grow(SynapseType.EXCITATORY);
                    }
                    */
                }
                {
                    int computerX = COMPUTER.x.get();
                    int computerY = COMPUTER.y.get();

                    int cursorX = CURSOR.x.get();
                    int cursorY = CURSOR.y.get();

                    if(computerX > cursorX && computerY > cursorY) {
                        form.grow(SynapseType.INHIBITORY);
                        form.suppress(SynapseType.EXCITATORY);
                    } else if(computerX < cursorX && computerY > cursorY) {
                        form.grow(SynapseType.INHIBITORY);
                        form.suppress(SynapseType.EXCITATORY);
                    } else if(computerX > cursorX && computerY < cursorY) {
                        form.grow(SynapseType.EXCITATORY);
                        form.suppress(SynapseType.INHIBITORY);
                    } else if(computerX < cursorX && computerY < cursorY) {
                        form.grow(SynapseType.EXCITATORY);
                        form.suppress(SynapseType.INHIBITORY);
                    }
                }
            }
        }).start();

        COMPUTER.tryMoveToX(WIDTH / 2);
        COMPUTER.tryMoveToY(HEIGHT / 2);

        Juikit uikit = Juikit.createFrame()
                .title("FOLLOWER (AEGIS-VIII TRAINING) - 3RD GENERATION ALGORITHM")
                .closeOperation(WindowConstants.EXIT_ON_CLOSE)
                .background(Color.DARK_GRAY)
                .size(WIDTH, HEIGHT + BALL_RADIUS)
                .centerAlign()
                .repaintInterval(10L)
                .resizable(false)

                .data("KEY", false)
                .data("MOUSE_X", 0)
                .data("MOUSE_Y", 0)

                .painter((juikit, graphics) -> {
                    // Personal Cursor
                    graphics.setColor(Color.ORANGE);
                    int cursorX = CURSOR.x.get();
                    int cursorY = CURSOR.y.get();
                    graphics.fillOval(cursorX - (BALL_RADIUS / 2), cursorY - (BALL_RADIUS / 2), BALL_RADIUS, BALL_RADIUS);

                    // Computer
                    graphics.setColor(Color.GREEN);
                    int computerX = COMPUTER.x.get();
                    int computerY = COMPUTER.y.get();
                    graphics.fillOval(computerX - (BALL_RADIUS / 2), computerY - (BALL_RADIUS / 2), BALL_RADIUS, BALL_RADIUS);

                    graphics.setColor(Color.WHITE);
                    if(!form.config().synapseDecaying) {
                        graphics.drawString("Neuroplasticity: OFF (Feed-forwarding)", 10, 20);
                    } else {
                        graphics.drawString("Neuroplasticity: ON (Training; Non-backpropagation)", 10, 20);
                    }
                })
                .mouseMoved((juikit, mouseEvent) -> {
                    int mouseX = mouseEvent.getX();
                    int mouseY = mouseEvent.getY();

                    juikit.data("MOUSE_X", mouseX);
                    juikit.data("MOUSE_Y", mouseY);

                    CURSOR.tryMoveToX(mouseX);
                    CURSOR.tryMoveToY(mouseY);
                })
                .keyPressed((juikit, keyEvent) -> {
                    if(keyEvent.isShiftDown()) {
                        juikit.data("KEY", true);
                        LOGGER.info("Key Pressed: SHIFT");

                        form.config().synapseDecaying = false;
                        form.config().synapseReinforcing = false;
                    }
                })
                .keyReleased((juikit, keyEvent) -> {
                    if(!keyEvent.isShiftDown() && juikit.data("KEY", boolean.class)) {
                        juikit.data("KEY", false);
                        LOGGER.info("Key Released: SHIFT");

                        form.config().synapseDecaying = true;
                        form.config().synapseReinforcing = true;
                    }
                })
                .keyPressed((juikit, keyEvent) -> {
                    if(keyEvent.isMetaDown() && keyEvent.getKeyCode() == 83) {
                        // Command+S
                        LOGGER.info("Saving network data");

                        File file = new File("aegis");
                        if(!file.exists()) {
                            file.mkdirs();
                        }
                        try {
                            form.write(new File("aegis/follower.aegis"));
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        LOGGER.info("Saved network data");
                    }
                })
                .visibility(true);

        new Thread(() -> {
            while(true) {
                int mouseX = uikit.data("MOUSE_X");
                int mouseY = uikit.data("MOUSE_Y");

                int computerX = COMPUTER.x.get();
                int computerY = COMPUTER.y.get();

                form.stimulate(0, computerX - mouseX);
                form.stimulate(1, computerY - mouseY);
                form.stimulate(2, 50);
            }
        }).start();
    }

    private static class Cursor extends IBall {
    }

    private static class Computer extends IBall {
    }

    private static abstract class IBall {

        AtomicInteger x = new AtomicInteger();
        AtomicInteger y = new AtomicInteger();

        void tryMoveToX(int x) {
            this.x.set(Math.max(0, Math.min(WIDTH, x)));
        }

        void tryMoveToY(int y) {
            this.y.set(Math.max(0, Math.min(HEIGHT, y)));
        }
    }

}
