package av.is.aegis.test.train;

import av.is.aegis.*;
import com.google.common.util.concurrent.AtomicDouble;
import studio.avis.juikit.Juikit;
import studio.avis.juikit.internal.Button;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

public class XORTrainingTest {

    // =========================
    // ==== USER PREFERENCE ====
    // =========================
    private static final boolean LOAD_NETWORK = true;

    private static final Logger LOGGER = Logger.getLogger("XOR (AEGIS-VIII TRAINING)");

    private static final int WINDOW_WIDTH = 500;

    private static final Computer COMPUTER = new Computer();

    private static final int LINE_MAX_LENGTH = 300;

    private static final int LINE_START_X = 100;
    private static final int LINE_END_X = LINE_START_X + LINE_MAX_LENGTH;

    private static final int COMPUTER_LINE_Y = 350;

    private static final int BALL_RADIUS = 20;

    private static final int BUTTON_WIDTH = 80;
    private static final int BUTTON_HEIGHT = 40;
    private static final int BUTTON_DISTANCE = 5;

    private static final int BUTTON_Y = 150;

    private static final AtomicDouble MOVE = new AtomicDouble();

    enum GateInput {
        ZERO_ZERO("(0, 0)") {
            @Override
            public void train(NetworkForm form) {
                int computerX = COMPUTER.x.get();

                if(computerX < LINE_END_X) {
                    form.suppress(SynapseType.INHIBITORY);
                    form.grow(SynapseType.EXCITATORY);
                }
            }

            @Override
            public void stimulate(NetworkForm form) {
                form.stimulate(0, -50);
                form.stimulate(1, -50);
            }
        },
        ONE_ZERO("(1, 0)") {
            @Override
            public void train(NetworkForm form) {
                int computerX = COMPUTER.x.get();

                if(computerX > LINE_START_X) {
                    form.suppress(SynapseType.EXCITATORY);
                    form.grow(SynapseType.INHIBITORY);
                }
            }

            @Override
            public void stimulate(NetworkForm form) {
                form.stimulate(0, 50);
                form.stimulate(1, -50);
            }
        },
        ZERO_ONE("(0, 1)") {
            @Override
            public void train(NetworkForm form) {
                int computerX = COMPUTER.x.get();

                if(computerX > LINE_START_X) {
                    form.suppress(SynapseType.EXCITATORY);
                    form.grow(SynapseType.INHIBITORY);
                }
            }

            @Override
            public void stimulate(NetworkForm form) {
                form.stimulate(0, -50);
                form.stimulate(1, 50);
            }
        },
        ONE_ONE("(1, 1)") {
            @Override
            public void train(NetworkForm form) {
                int computerX = COMPUTER.x.get();

                if(computerX < LINE_END_X) {
                    form.suppress(SynapseType.INHIBITORY);
                    form.grow(SynapseType.EXCITATORY);
                }
            }

            @Override
            public void stimulate(NetworkForm form) {
                form.stimulate(0, 50);
                form.stimulate(1, 50);
            }
        };

        private final String name;

        GateInput(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public abstract void train(NetworkForm form);

        public abstract void stimulate(NetworkForm form);
    }

    private static final AtomicInteger GATE_INDEX = new AtomicInteger();
    private static final AtomicReference<GateInput> GATE_INPUT = new AtomicReference<>(GateInput.ZERO_ZERO);

    private static final List<Runnable> BUTTONS = Arrays.asList(() -> GATE_INPUT.set(GateInput.ZERO_ZERO),
                                                                () -> GATE_INPUT.set(GateInput.ONE_ZERO),
                                                                () -> GATE_INPUT.set(GateInput.ZERO_ONE),
                                                                () -> GATE_INPUT.set(GateInput.ONE_ONE));

    private static NetworkForm createNetworkForm() {
        NetworkForm form = NetworkBuilder.builder()
                .inputs(3)
                .inters(2000)
                .outputs(1)
                .visualize(true)
                .configure(configuration -> {
                    configuration.visualization.visibility.synapseVisible = Network.SynapseVisibility.NONE;
                    configuration.visualization.stimulateInputOnly = true;

                    configuration.inhibitoryReinforcementRatio = 0.01d;
                    configuration.excitatoryReinforcementRatio = 0.01d;

                    configuration.inhibitorySuppressionRatio = 0.1d;
                    configuration.excitatorySuppressionRatio = 0.1d;

                    configuration.inhibitorySynapseCreationChance = 0.5d;

                    configuration.maxSynapsesForInputNeurons = 10;
                    configuration.maxSynapsesForInterNeurons = 50;
                }).build();

        form.start();

        return form;
    }

    private static NetworkForm loadNetworkForm() {
        NetworkLoader loader = new NetworkLoader(new File("aegis/xor.aegis"));

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
                        COMPUTER.tryMoveTo(COMPUTER.x.get() - 10);
                    } else if(computerX < moveTo) {
                        COMPUTER.tryMoveTo(COMPUTER.x.get() + 10);
                    }
                }

                GATE_INPUT.get().train(form);
            }
        }).start();

        Juikit uikit = Juikit.createFrame()
                .title("XOR (AEGIS-VIII TRAINING) - 3RD GENERATION ALGORITHM")
                .closeOperation(WindowConstants.EXIT_ON_CLOSE)
                .background(Color.DARK_GRAY)
                .size(WINDOW_WIDTH, 500)
                .centerAlign()
                .repaintInterval(10L)
                .resizable(false)

                .data("KEY", false)

                .painter((juikit, graphics) -> {
                    // Computer

                    graphics.setColor(Color.WHITE);

                    graphics.drawString("0", LINE_START_X - 20, COMPUTER_LINE_Y + 7);
                    graphics.drawString("1", LINE_END_X + 10, COMPUTER_LINE_Y + 7);

                    for(int i = 0; i < 5; i++) {
                        graphics.drawLine(LINE_START_X, i + COMPUTER_LINE_Y, LINE_END_X, i + COMPUTER_LINE_Y);
                    }

                    graphics.setColor(Color.GREEN);
                    int computerX = COMPUTER.x.get();
                    graphics.fillOval(computerX - (BALL_RADIUS / 2), (COMPUTER_LINE_Y + 2) - (BALL_RADIUS / 2), BALL_RADIUS, BALL_RADIUS);

                    graphics.setColor(Color.WHITE);
                    if(!form.config().synapseDecaying) {
                        graphics.drawString("Neuroplasticity: OFF (Feed-forwarding)", 10, 20);
                    } else {
                        graphics.drawString("Neuroplasticity: ON (Training; Non-backpropagation)", 10, 20);
                    }

                    graphics.drawString("Current Gate: " + GATE_INPUT.get().getName(), 10, 40);
                })
                .afterPainter((juikit, graphics) -> {
                    int left = (WINDOW_WIDTH / 2) - (((BUTTONS.size() * (BUTTON_WIDTH + BUTTON_DISTANCE)) - BUTTON_DISTANCE) / 2);

                    for(int i = 0; i < BUTTONS.size(); i++) {
                        int minX = left + (i * (BUTTON_WIDTH + BUTTON_DISTANCE));
                        int minY = BUTTON_Y - (BUTTON_HEIGHT / 2);

                        GateInput input = GateInput.values()[i];

                        graphics.setColor(Color.WHITE);
                        graphics.drawString(input.getName(), minX + (BUTTON_WIDTH / 2) - 17, minY + 25);
                    }
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
                            form.write(new File("aegis/xor.aegis"));
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        LOGGER.info("Saved network data");
                    }
                })
                .visibility(true);

        int left = (WINDOW_WIDTH / 2) - (((BUTTONS.size() * (BUTTON_WIDTH + BUTTON_DISTANCE)) - BUTTON_DISTANCE) / 2);

        for(int i = 0; i < BUTTONS.size(); i++) {
            Runnable runnable = BUTTONS.get(i);

            int minX = left + (i * (BUTTON_WIDTH + BUTTON_DISTANCE));
            int minY = BUTTON_Y - (BUTTON_HEIGHT / 2);

            int finalI = i;
            uikit.button(Button.builder()
                                 .sizeFixed(minX, minY, BUTTON_WIDTH, BUTTON_HEIGHT)
                                 .background(new Color(91, 219, 255))
                                 .hover(new Color(80, 193, 225))
                                 .press(new Color(73, 175, 204))
                                 .highPriorityOnly(true)
                                 .processReleased((juikit, graphics) -> {
                                     runnable.run();
                                     GATE_INDEX.set(finalI);
                                 }));
        }

        new Thread(() -> {
            while(true) {
                GATE_INPUT.get().stimulate(form);

                // Kind of Bias
                form.stimulate(2, 50);
            }
        }).start();
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
