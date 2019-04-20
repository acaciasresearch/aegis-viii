package av.is.aegis.test.train;

import av.is.aegis.Network;
import av.is.aegis.NetworkBuilder;
import av.is.aegis.NetworkForm;
import av.is.aegis.SynapseType;
import avis.juikit.Juikit;
import com.google.common.util.concurrent.AtomicDouble;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

public class LineUpTrainingTest {

    private static final Logger LOGGER = Logger.getLogger("LINE UP! (AEGIS-VIII TRAINING)");

    private static final Cursor CURSOR = new Cursor();
    private static final Computer COMPUTER = new Computer();

    private static final int LINE_MAX_LENGTH = 300;

    private static final int LINE_START_X = 100;
    private static final int LINE_END_X = LINE_START_X + LINE_MAX_LENGTH;

    private static final int CURSOR_LINE_Y = 150;
    private static final int COMPUTER_LINE_Y = 350;

    private static final int BALL_RADIUS = 20;

    private static final AtomicDouble MOVE = new AtomicDouble();

    public static void main(String[] args) {
        NetworkForm form = NetworkBuilder.builder()
                .inputs(2) // #0: left distance from me, #1: right distance from me
                .inters(2000)
                .outputs(1) // #0: go to left, #1: go to right
                .visualize(true)
                .configure(configuration -> {
                    // Visualizations
                    configuration.visualization.visibility.synapseVisible = Network.SynapseVisibility.NONE;
                    configuration.visualization.stimulateInputOnly = true;

                    configuration.inhibitorySuppressionRatio = 0.001d;
                    configuration.excitatorySuppressionRatio = 0.001d;

                    configuration.inhibitoryReinforcementRatio = 0.0001d;
                    configuration.excitatoryReinforcementRatio = 0.0001d;

                    configuration.inhibitorySynapseCreationChance = 0.5d;

                    configuration.maxSynapsesForInputNeurons = 10;
                    configuration.maxSynapsesForInterNeurons = 50;
                }).build();

        form.outputListener((neuron, value) -> {
            MOVE.set(Math.max(0, Math.min(LINE_MAX_LENGTH, MOVE.get() + value)));
        });

        new Thread(() -> {
            while(true) {
                try {
                    Thread.sleep(10L);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                {
                    int computerX = COMPUTER.x.get();
                    int moveTo = LINE_START_X + MOVE.intValue();
                    if(computerX > moveTo) {
                        COMPUTER.tryMoveTo(COMPUTER.x.get() - 1);
                    } else if(computerX < moveTo) {
                        COMPUTER.tryMoveTo(COMPUTER.x.get() + 1);
                    }
                }

                int computerX = COMPUTER.x.get();
                int cursorX = CURSOR.x.get();

                if(cursorX > computerX && cursorX - computerX > 10) {
                    form.supress(SynapseType.INHIBITORY);
                    form.grow(SynapseType.EXCITATORY);
                } else if(cursorX < computerX && computerX - cursorX > 10) {
                    form.supress(SynapseType.EXCITATORY);
                    form.grow(SynapseType.INHIBITORY);
                }
            }
        }).start();

        form.start();

        CURSOR.tryMoveTo(LINE_START_X);
        COMPUTER.tryMoveTo(LINE_START_X);

        Juikit uikit = Juikit.createFrame()
                .title("Line up! (AEGIS-VIII TRAINING) - 3RD GENERATION ALGORITHM")
                .closeOperation(WindowConstants.EXIT_ON_CLOSE)
                .background(Color.DARK_GRAY)
                .size(500, 500)
                .centerAlign()
                .repaintInterval(10L)

                .data("MOUSE_X", 0)
                .data("KEY", false)

                .painter((juikit, graphics) -> {
                    // Personal Cursor
                    graphics.setColor(Color.WHITE);
                    for(int i = 0; i < 5; i++) {
                        graphics.drawLine(LINE_START_X, i + CURSOR_LINE_Y, LINE_END_X, i + CURSOR_LINE_Y);
                    }

                    graphics.setColor(Color.ORANGE);
                    int cursorX = CURSOR.x.get();
                    graphics.fillOval(cursorX - (BALL_RADIUS / 2), (CURSOR_LINE_Y + 2) - (BALL_RADIUS / 2), BALL_RADIUS, BALL_RADIUS);

                    // Computer
                    graphics.setColor(Color.WHITE);
                    for(int i = 0; i < 5; i++) {
                        graphics.drawLine(LINE_START_X, i + COMPUTER_LINE_Y, LINE_END_X, i + COMPUTER_LINE_Y);
                    }

                    graphics.setColor(Color.GREEN);
                    int computerX = COMPUTER.x.get();
                    graphics.fillOval(computerX - (BALL_RADIUS / 2), (COMPUTER_LINE_Y + 2) - (BALL_RADIUS / 2), BALL_RADIUS, BALL_RADIUS);

                    boolean metaPressed = juikit.data("KEY");
                    graphics.setColor(Color.WHITE);
                    if(metaPressed) {
                        graphics.drawString("Neuroplasticity: OFF (Feed-forwarding)", 10, 20);
                    } else {
                        graphics.drawString("Neuroplasticity: ON (Training; Non-backpropagation)", 10, 20);
                    }
                })
                .mouseMoved((juikit, mouseEvent) -> {
                    int mouseX = mouseEvent.getX();
                    juikit.data("MOUSE_X", mouseX);

                    CURSOR.tryMoveTo(mouseX);
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
                .visibility(true);

        new Thread(() -> {
            while(true) {
                int mouseX = uikit.data("MOUSE_X");
                int computerX = COMPUTER.x.get();

                form.stimulate(0, computerX - mouseX);
                form.stimulate(1, mouseX - computerX);
            }
        }).start();
    }

    private static class Cursor extends IBall {
    }

    private static class Computer extends IBall {
    }

    private static abstract class IBall {

        AtomicInteger x = new AtomicInteger();

        void tryMoveTo(int x) {
            this.x.set(Math.max(LINE_START_X, Math.min(LINE_END_X, x)));
        }
    }

}
