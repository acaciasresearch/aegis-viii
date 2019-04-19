package av.is.aegis.test.train;

import av.is.aegis.Network;
import av.is.aegis.NetworkBuilder;
import av.is.aegis.NetworkForm;
import avis.juikit.Juikit;

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

    public static void main(String[] args) {
        NetworkForm form = NetworkBuilder.builder()
                .inputs(2) // #0: left distance from me, #1: right distance from me
                .inters(100)
                .outputs(4) // #0: go to left, #1: go to right
                .visualize(true)
                .configure(configuration -> {
                    // Visualizations
                    configuration.visualization.visibility.synapseVisible = Network.SynapseVisibility.NONE;
                    configuration.visualization.stimulateInputOnly = true;

                    // Loggers
                    configuration.loggers.markedNeurons = false;
                }).build();

        form.outputListener((neuron, value) -> {
            COMPUTER.tryMoveTo(COMPUTER.x.get() + (int) Math.max(-5, Math.min(5, value)));
        });

        form.start();

        CURSOR.tryMoveTo(LINE_START_X);
        COMPUTER.tryMoveTo(LINE_START_X);

        Juikit uikit = Juikit.createFrame()
                .title("Line up! (AEGIS-VIII TRAINING)")
                .closeOperation(WindowConstants.EXIT_ON_CLOSE)
                .background(Color.DARK_GRAY)
                .size(500, 500)
                .centerAlign()
                .repaintInterval(10L)

                .data("MOUSE_X", 0)

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
                })
                .mouseMoved((juikit, mouseEvent) -> {
                    int mouseX = mouseEvent.getX();
                    juikit.data("MOUSE_X", mouseX);

                    CURSOR.tryMoveTo(mouseX);
                })
                .visibility(true);

        new Thread(() -> {
            while(true) {
                int mouseX = uikit.data("MOUSE_X");
                int computerX = COMPUTER.x.get();

                form.stimulate(0, computerX - mouseX);
                form.stimulate(1, mouseX - computerX);
                // same with:
                /*
                if(mouseX > computerX) {
                    // mouse cursor is righter than computer
                    form.stimulate(0, computerX - mouseX);
                    form.stimulate(1, mouseX - computerX);
                } else {
                    // mouse cursor is lefter than computer
                    form.stimulate(0, computerX - mouseX);
                    form.stimulate(1, mouseX - computerX);
                }
                */
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
