

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Receiver;
import javax.sound.midi.ShortMessage;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

public class Main extends JFrame {
    private static final long serialVersionUID = 1L;
    private JTextArea textArea = null;
    private PianoPanel pianoPanel = null;
    
    // VirtualPiano normal keys (white keys / default notes)
    private Map<Integer, Integer> normalKeyMap = new HashMap<>();
    // VirtualPiano shifted keys (black keys / sharp notes)
    private Map<Integer, Integer> shiftKeyMap = new HashMap<>();
    
    private MidiDevice outDevice = null;
    private Receiver receiver = null;
    private String targetPortName = "loopMIDI Port";

    private Map<Integer, Integer> physicalKeyToMidi = new ConcurrentHashMap<>();
    private Set<Integer> activeMidiNotes = ConcurrentHashMap.newKeySet();

    private volatile int releaseTime = 100; // Sustain Release Time (ms)
    private boolean isSpacePressed = false;
    private boolean isShiftPressed = false;
    private boolean isSustainToggled = false;

    // --- Reverb Settings ---
    private boolean isReverbOn = false;
    private int reverbDepth = 64; // 범위: 0 ~ 127 (기본값: 64)

    // --- Transpose Settings & Limits ---
    private int transposeAmount = 0; // 반음 단위
    private static final int OCTAVE_STEP = 12;

    // 88건반(21 ~ 108) 및 VirtualPiano 기본범위(36 ~ 96) 기준 한계값 설정
    private static final int PIANO_START_NOTE = 21; // A0
    private static final int PIANO_END_NOTE = 108;  // C8
    private static final int BASE_MIN_NOTE = 36;    // C2
    private static final int BASE_MAX_NOTE = 96;    // C7

    private static final int MIN_TRANSPOSE = PIANO_START_NOTE - BASE_MIN_NOTE; // -15
    private static final int MAX_TRANSPOSE = PIANO_END_NOTE - BASE_MAX_NOTE;   // +12

    // --- UI Components for MIDI Selection ---
    private JComboBox<MidiDeviceItem> midiDeviceComboBox;
    private List<MidiDevice.Info> availableMidiInfos = new ArrayList<>();

    private static class MidiDeviceItem {
        private final MidiDevice.Info info;

        public MidiDeviceItem(MidiDevice.Info info) {
            this.info = info;
        }

        public MidiDevice.Info getInfo() {
            return info;
        }

        @Override
        public String toString() {
            if (info == null) return "-- Select MIDI Device --";
            return info.getName() + " (" + info.getDescription() + ")";
        }
    }

    public Main() {
        setVirtualPianoKeyMap();
        setTextArea();
        pianoPanel = new PianoPanel();
        setFrame();
        refreshMidiDevices();
        updateStatus();
    }

    private void refreshMidiDevices() {
        midiDeviceComboBox.removeAllItems();
        availableMidiInfos.clear();

        midiDeviceComboBox.addItem(new MidiDeviceItem(null));

        MidiDevice.Info[] infos = MidiSystem.getMidiDeviceInfo();
        int autoSelectIndex = -1;

        for (MidiDevice.Info info : infos) {
            try {
                MidiDevice dev = MidiSystem.getMidiDevice(info);
                if (dev.getMaxReceivers() != 0) {
                    availableMidiInfos.add(info);
                    MidiDeviceItem item = new MidiDeviceItem(info);
                    midiDeviceComboBox.addItem(item);

                    if (info.getName().contains(targetPortName)) {
                        autoSelectIndex = midiDeviceComboBox.getItemCount() - 1;
                    }
                }
            } catch (Exception ignored) {}
        }

        if (autoSelectIndex != -1) {
            midiDeviceComboBox.setSelectedIndex(autoSelectIndex);
        } else if (midiDeviceComboBox.getItemCount() > 1) {
            midiDeviceComboBox.setSelectedIndex(1);
        }
    }

    private synchronized void switchMidiDevice(MidiDevice.Info info) {
        if (receiver != null) {
            try { receiver.close(); } catch (Exception ignored) {}
            receiver = null;
        }
        if (outDevice != null && outDevice.isOpen()) {
            try { outDevice.close(); } catch (Exception ignored) {}
            outDevice = null;
        }

        if (info == null) {
            updateStatus();
            return;
        }

        try {
            outDevice = MidiSystem.getMidiDevice(info);
            outDevice.open();
            receiver = outDevice.getReceiver();
            sendSustainCommand(false);
            sendReverbCommand();
        } catch (Exception e) {
            e.printStackTrace();
            outDevice = null;
            receiver = null;
        }
        updateStatus();
    }

    private void setVirtualPianoKeyMap() {
        // --- VirtualPiano.net Standard 61-Key Base Mapping (MIDI Note 36 ~ 96) ---

        // Octave 1 (C2 ~ B2 : MIDI 36 ~ 47)
        normalKeyMap.put(KeyEvent.VK_1, 36); // 1 = C2
        shiftKeyMap.put(KeyEvent.VK_1, 37);  // ! = C#2
        normalKeyMap.put(KeyEvent.VK_2, 38); // 2 = D2
        shiftKeyMap.put(KeyEvent.VK_2, 39);  // @ = D#2
        normalKeyMap.put(KeyEvent.VK_3, 40); // 3 = E2
        normalKeyMap.put(KeyEvent.VK_4, 41); // 4 = F2
        shiftKeyMap.put(KeyEvent.VK_4, 42);  // $ = F#2
        normalKeyMap.put(KeyEvent.VK_5, 43); // 5 = G2
        shiftKeyMap.put(KeyEvent.VK_5, 44);  // % = G#2
        normalKeyMap.put(KeyEvent.VK_6, 45); // 6 = A2
        shiftKeyMap.put(KeyEvent.VK_6, 46);  // ^ = A#2
        normalKeyMap.put(KeyEvent.VK_7, 47); // 7 = B2

        // Octave 2 (C3 ~ B3 : MIDI 48 ~ 59)
        normalKeyMap.put(KeyEvent.VK_8, 48); // 8 = C3
        shiftKeyMap.put(KeyEvent.VK_8, 49);  // * = C#3
        normalKeyMap.put(KeyEvent.VK_9, 50); // 9 = D3
        shiftKeyMap.put(KeyEvent.VK_9, 51);  // ( = D#3
        normalKeyMap.put(KeyEvent.VK_0, 52); // 0 = E3
        normalKeyMap.put(KeyEvent.VK_Q, 53); // q = F3
        shiftKeyMap.put(KeyEvent.VK_Q, 54);  // Q = F#3
        normalKeyMap.put(KeyEvent.VK_W, 55); // w = G3
        shiftKeyMap.put(KeyEvent.VK_W, 56);  // W = G#3
        normalKeyMap.put(KeyEvent.VK_E, 57); // e = A3
        shiftKeyMap.put(KeyEvent.VK_E, 58);  // E = A#3
        normalKeyMap.put(KeyEvent.VK_R, 59); // r = B3

        // Octave 3 (C4 ~ B4 : MIDI 60 ~ 71) - Middle C
        normalKeyMap.put(KeyEvent.VK_T, 60); // t = C4 (Middle C)
        shiftKeyMap.put(KeyEvent.VK_T, 61);  // T = C#4
        normalKeyMap.put(KeyEvent.VK_Y, 62); // y = D4
        shiftKeyMap.put(KeyEvent.VK_Y, 63);  // Y = D#4
        normalKeyMap.put(KeyEvent.VK_U, 64); // u = E4
        normalKeyMap.put(KeyEvent.VK_I, 65); // i = F4
        shiftKeyMap.put(KeyEvent.VK_I, 66);  // I = F#4
        normalKeyMap.put(KeyEvent.VK_O, 67); // o = G4
        shiftKeyMap.put(KeyEvent.VK_O, 68);  // O = G#4
        normalKeyMap.put(KeyEvent.VK_P, 69); // p = A4
        shiftKeyMap.put(KeyEvent.VK_P, 70);  // P = A#4
        normalKeyMap.put(KeyEvent.VK_A, 71); // a = B4

        // Octave 4 (C5 ~ B5 : MIDI 72 ~ 83)
        normalKeyMap.put(KeyEvent.VK_S, 72); // s = C5
        shiftKeyMap.put(KeyEvent.VK_S, 73);  // S = C#5
        normalKeyMap.put(KeyEvent.VK_D, 74); // d = D5
        shiftKeyMap.put(KeyEvent.VK_D, 75);  // D = D#5
        normalKeyMap.put(KeyEvent.VK_F, 76); // f = E5
        normalKeyMap.put(KeyEvent.VK_G, 77); // g = F5
        shiftKeyMap.put(KeyEvent.VK_G, 78);  // G = F#5
        normalKeyMap.put(KeyEvent.VK_H, 79); // h = G5
        shiftKeyMap.put(KeyEvent.VK_H, 80);  // H = G#5
        normalKeyMap.put(KeyEvent.VK_J, 81); // j = A5
        shiftKeyMap.put(KeyEvent.VK_J, 82);  // J = A#5
        normalKeyMap.put(KeyEvent.VK_K, 83); // k = B5

        // Octave 5 (C6 ~ C7 : MIDI 84 ~ 96)
        normalKeyMap.put(KeyEvent.VK_L, 84); // l = C6
        shiftKeyMap.put(KeyEvent.VK_L, 85);  // L = C#6
        normalKeyMap.put(KeyEvent.VK_Z, 86); // z = D6
        shiftKeyMap.put(KeyEvent.VK_Z, 87);  // Z = D#6
        normalKeyMap.put(KeyEvent.VK_X, 88); // x = E6
        normalKeyMap.put(KeyEvent.VK_C, 89); // c = F6
        shiftKeyMap.put(KeyEvent.VK_C, 90);  // C = F#6
        normalKeyMap.put(KeyEvent.VK_V, 91); // v = G6
        shiftKeyMap.put(KeyEvent.VK_V, 92);  // V = G#6
        normalKeyMap.put(KeyEvent.VK_B, 93); // b = A6
        shiftKeyMap.put(KeyEvent.VK_B, 94);  // B = A#6
        normalKeyMap.put(KeyEvent.VK_N, 95); // n = B6
        normalKeyMap.put(KeyEvent.VK_M, 96); // m = C7
    }

    private void setTextArea() {
        textArea = new JTextArea();
        textArea.setText(
            "=== VirtualPiano.net Keymap Controller ===\n\n" +
            "Octave 1:  1 ! 2 @ 3 4 $ 5 % 6 ^ 7\n" +
            "Octave 2:  8 * 9 ( 0 q Q w W e E r\n" +
            "Octave 3:  t T y Y u i I o O p P a  (Middle C = t)\n" +
            "Octave 4:  s S d D f g G h H j J k\n" +
            "Octave 5:  l L z Z x c C v V b B n m\n\n" +
            "Controls:\n" +
            "- Hold Spacebar / Shift+Backspace: Toggle Sustain Pedal\n" +
            "- Hold Shift + Key: Play Sharp (#) Notes\n" +
            "- Up / Down Arrow: Transpose ±1 Semitone\n" +
            "- Page Up / Page Down: Transpose ±1 Octave\n" +
            "- Home Key: Reset Transpose to 0\n" +
            "- Shift + \\ (|): Reverb On/Off Toggle\n" +
            "- [ / ]: Adjust Reverb Depth (- / +10, Shift: - / +1)\n" +
            "- - / + (=): Adjust Sustain Release Time (- / +50ms, Shift: - / +10ms)\n\n" +
            "Press keys to play..."
        );
        textArea.setEditable(false);

        textArea.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                int keyCode = e.getKeyCode();

                if (keyCode == KeyEvent.VK_SHIFT) {
                    isShiftPressed = true;
                    updateStatus();
                    return;
                }

                // --- Reverb Toggle (Shift + \) ---
                if (keyCode == KeyEvent.VK_BACK_SLASH && isShiftPressed) {
                    isReverbOn = !isReverbOn;
                    sendReverbCommand();
                    updateStatus();
                    return;
                }

                // --- Reverb Depth Controls ([ / ]) ---
                if (keyCode == KeyEvent.VK_OPEN_BRACKET) {
                    int step = isShiftPressed ? 1 : 10;
                    reverbDepth = Math.max(0, reverbDepth - step);
                    sendReverbCommand();
                    updateStatus();
                    return;
                }
                if (keyCode == KeyEvent.VK_CLOSE_BRACKET) {
                    int step = isShiftPressed ? 1 : 10;
                    reverbDepth = Math.min(127, reverbDepth + step);
                    sendReverbCommand();
                    updateStatus();
                    return;
                }

                // --- Sustain Time Controls (- / +) ---
                if (keyCode == KeyEvent.VK_MINUS) {
                    int step = isShiftPressed ? 10 : 50;
                    releaseTime = Math.max(0, releaseTime - step);
                    updateStatus();
                    return;
                }
                if (keyCode == KeyEvent.VK_EQUALS) { // 키보드의 + / = 키
                    int step = isShiftPressed ? 10 : 50;
                    releaseTime = Math.min(2000, releaseTime + step);
                    updateStatus();
                    return;
                }

                if (keyCode == KeyEvent.VK_BACK_SPACE && isShiftPressed) {
                    isSustainToggled = !isSustainToggled;
                    applySustainState();
                    updateStatus();
                    return;
                }

                if (keyCode == KeyEvent.VK_SPACE) {
                    if (!isSpacePressed) {
                        isSpacePressed = true;
                        applySustainState();
                        updateStatus();
                    }
                    return;
                }

                // --- Transpose Controls (Bounded between MIN_TRANSPOSE and MAX_TRANSPOSE) ---
                if (keyCode == KeyEvent.VK_UP) {
                    transposeAmount = Math.min(transposeAmount + 1, MAX_TRANSPOSE);
                    updateStatus();
                    if (pianoPanel != null) pianoPanel.repaint();
                    return;
                }
                if (keyCode == KeyEvent.VK_DOWN) {
                    transposeAmount = Math.max(transposeAmount - 1, MIN_TRANSPOSE);
                    updateStatus();
                    if (pianoPanel != null) pianoPanel.repaint();
                    return;
                }
                if (keyCode == KeyEvent.VK_PAGE_UP) {
                    transposeAmount = Math.min(transposeAmount + OCTAVE_STEP, MAX_TRANSPOSE);
                    updateStatus();
                    if (pianoPanel != null) pianoPanel.repaint();
                    return;
                }
                if (keyCode == KeyEvent.VK_PAGE_DOWN) {
                    transposeAmount = Math.max(transposeAmount - OCTAVE_STEP, MIN_TRANSPOSE);
                    updateStatus();
                    if (pianoPanel != null) pianoPanel.repaint();
                    return;
                }
                if (keyCode == KeyEvent.VK_HOME) {
                    transposeAmount = 0;
                    updateStatus();
                    if (pianoPanel != null) pianoPanel.repaint();
                    return;
                }

                if (physicalKeyToMidi.containsKey(keyCode)) return;

                Integer note = null;
                if (isShiftPressed) {
                    note = shiftKeyMap.get(keyCode);
                }
                if (note == null) {
                    note = normalKeyMap.get(keyCode);
                }

                if (note != null) {
                    int transposedNote = note + transposeAmount;

                    if (transposedNote >= PIANO_START_NOTE && transposedNote <= PIANO_END_NOTE) {
                        physicalKeyToMidi.put(keyCode, transposedNote);
                        activeMidiNotes.add(transposedNote);
                        noteON(transposedNote);
                        if (pianoPanel != null) pianoPanel.repaint();
                    }
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {
                int keyCode = e.getKeyCode();

                if (keyCode == KeyEvent.VK_SHIFT) {
                    isShiftPressed = false;
                    updateStatus();
                    return;
                }

                if (keyCode == KeyEvent.VK_SPACE) {
                    isSpacePressed = false;
                    applySustainState();
                    updateStatus();
                    return;
                }

                Integer playedNote = physicalKeyToMidi.remove(keyCode);
                if (playedNote != null) {
                    activeMidiNotes.remove(playedNote);
                    noteOFF(playedNote);
                    if (pianoPanel != null) pianoPanel.repaint();
                }
            }
        });
    }

    private void applySustainState() {
        sendSustainCommand(isSustainToggled || isSpacePressed);
    }

    private void sendSustainCommand(boolean isOn) {
        try {
            if (receiver != null) {
                ShortMessage msg = new ShortMessage();
                msg.setMessage(ShortMessage.CONTROL_CHANGE, 0, 64, isOn ? 127 : 0);
                receiver.send(msg, -1);
            }
        } catch (Exception e) { 
            e.printStackTrace(); 
        }
    }

    private void sendReverbCommand() {
        try {
            if (receiver != null) {
                ShortMessage msg = new ShortMessage();
                // CC #91: Effect 1 Depth (Reverb Send Level)
                msg.setMessage(ShortMessage.CONTROL_CHANGE, 0, 91, isReverbOn ? reverbDepth : 0);
                receiver.send(msg, -1);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void noteON(int noteNumber) {
        try {
            if (receiver != null) {
                ShortMessage msg = new ShortMessage();
                msg.setMessage(ShortMessage.NOTE_ON, 0, noteNumber, 93);
                receiver.send(msg, -1);
            }
        } catch (Exception e) { 
            e.printStackTrace(); 
        }
    }

    private void noteOFF(int noteNumber) {
        new Thread(() -> {
            try { 
                Thread.sleep(releaseTime); 
            } catch (InterruptedException e) {}
            try {
                if (receiver != null) {
                    ShortMessage msg = new ShortMessage();
                    msg.setMessage(ShortMessage.NOTE_OFF, 0, noteNumber, 93);
                    receiver.send(msg, -1);
                }
            } catch (Exception e) { 
                e.printStackTrace(); 
            }
        }).start();
    }

    private void updateStatus() {
        String sustain = (isSustainToggled || isSpacePressed) ? "ON" : "OFF";
        String shiftState = isShiftPressed ? "Active (Sharps #)" : "Inactive";
        String transposeStr = (transposeAmount >= 0 ? "+" : "") + transposeAmount;
        String reverbStr = isReverbOn ? "ON (" + reverbDepth + ")" : "OFF";
        String devName = (outDevice != null) ? outDevice.getDeviceInfo().getName() : "None";

        SwingUtilities.invokeLater(() -> 
            setTitle("Virtual Piano MIDI Controller - Device: [" + devName + "] | Transpose: " + transposeStr + 
                     " | Sustain: " + sustain + " (" + releaseTime + "ms) | Reverb: " + reverbStr + " | Shift: " + shiftState)
        );
    }

    // --- 88-Key Piano GUI Component (Dimmed Inactive Range) ---
    private class PianoPanel extends JPanel {
        private static final long serialVersionUID = 1L;

        private final boolean[] isBlackKey = {
            false, true, false, true, false, false, true, false, true, false, true, false
        };

        public PianoPanel() {
            setPreferredSize(new Dimension(1100, 160));
            setBackground(new Color(24, 24, 27));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int totalWhiteKeys = 0;
            for (int note = PIANO_START_NOTE; note <= PIANO_END_NOTE; note++) {
                if (!isBlackKey[note % 12]) totalWhiteKeys++;
            }

            int panelWidth = getWidth();
            int panelHeight = getHeight();
            double whiteKeyWidth = (double) panelWidth / totalWhiteKeys;
            double blackKeyWidth = whiteKeyWidth * 0.6;
            int blackKeyHeight = (int) (panelHeight * 0.62);

            // Bounded Playable Range Calculation
            int playableMin = BASE_MIN_NOTE + transposeAmount;
            int playableMax = BASE_MAX_NOTE + transposeAmount;

            // 1. Render White Keys
            double currentX = 0;
            Map<Integer, Double> whiteKeyPositions = new HashMap<>();

            for (int note = PIANO_START_NOTE; note <= PIANO_END_NOTE; note++) {
                if (!isBlackKey[note % 12]) {
                    whiteKeyPositions.put(note, currentX);
                    int x = (int) Math.round(currentX);
                    int w = (int) Math.round(currentX + whiteKeyWidth) - x;

                    boolean isPlayable = (note >= playableMin && note <= playableMax);
                    boolean isActive = activeMidiNotes.contains(note);

                    if (isActive) {
                        g2.setColor(new Color(249, 115, 22)); // 눌린 키: 주황색
                    } else if (isPlayable) {
                        g2.setColor(Color.WHITE); // 활성 영역 흰 건반: 밝은 흰색
                    } else {
                        g2.setColor(new Color(82, 82, 91)); // 비활성 영역 흰 건반: 어두운 회색 (Dimmed)
                    }

                    g2.fillRect(x, 0, w - 1, panelHeight - 1);
                    g2.setColor(new Color(39, 39, 42));
                    g2.drawRect(x, 0, w - 1, panelHeight - 1);

                    // C 노트 라벨링 (C1 ~ C8)
                    if (note % 12 == 0) {
                        g2.setColor(isPlayable ? new Color(39, 39, 42) : new Color(161, 161, 170));
                        g2.setFont(new Font("Consolas", Font.BOLD, 11));
                        g2.drawString("C" + (note / 12 - 1), x + 2, panelHeight - 6);
                    }

                    currentX += whiteKeyWidth;
                }
            }

            // 2. Render Black Keys
            for (int note = PIANO_START_NOTE; note <= PIANO_END_NOTE; note++) {
                if (isBlackKey[note % 12]) {
                    Double prevWhiteX = whiteKeyPositions.get(note - 1);
                    if (prevWhiteX != null) {
                        int x = (int) Math.round(prevWhiteX + whiteKeyWidth - (blackKeyWidth / 2.0));
                        int w = (int) Math.round(blackKeyWidth);

                        boolean isPlayable = (note >= playableMin && note <= playableMax);
                        boolean isActive = activeMidiNotes.contains(note);

                        if (isActive) {
                            g2.setColor(new Color(234, 88, 12)); // 눌린 키: 진한 주황색
                        } else if (isPlayable) {
                            g2.setColor(new Color(24, 24, 27)); // 활성 영역 검은 건반: 기본 검은색
                        } else {
                            g2.setColor(new Color(9, 9, 11)); // 비활성 영역 검은 건반: 짙은 밤색/검은색 (Dimmed)
                        }

                        g2.fillRect(x, 0, w, blackKeyHeight);
                        g2.setColor(Color.BLACK);
                        g2.drawRect(x, 0, w, blackKeyHeight);
                    }
                }
            }
        }
    }

    private void setFrame() {
        setLayout(new BorderLayout());

        // --- Control Panel (Top Bar) ---
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        topPanel.setBackground(new Color(39, 39, 42));

        JLabel label = new JLabel("MIDI Output Device:");
        label.setForeground(Color.WHITE);
        label.setFont(new Font("Consolas", Font.BOLD, 14));

        midiDeviceComboBox = new JComboBox<>();
        midiDeviceComboBox.setPreferredSize(new Dimension(380, 28));
        midiDeviceComboBox.setFocusable(false);

        midiDeviceComboBox.addActionListener(e -> {
            MidiDeviceItem selectedItem = (MidiDeviceItem) midiDeviceComboBox.getSelectedItem();
            if (selectedItem != null) {
                switchMidiDevice(selectedItem.getInfo());
            }
            textArea.requestFocusInWindow();
        });

        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.setFocusable(false);
        refreshBtn.addActionListener(e -> {
            refreshMidiDevices();
            textArea.requestFocusInWindow();
        });

        topPanel.add(label);
        topPanel.add(midiDeviceComboBox);
        topPanel.add(refreshBtn);

        add(topPanel, BorderLayout.NORTH);

        // --- Center Text Area ---
        JScrollPane scrollPane = new JScrollPane(textArea);
        add(scrollPane, BorderLayout.CENTER);
        
        textArea.setBackground(new Color(24, 24, 27));
        textArea.setForeground(new Color(244, 244, 245));
        textArea.setCaretColor(Color.WHITE);
        textArea.setFont(new Font("Consolas", Font.PLAIN, 18));
        
        // --- Bottom Piano Graphics Panel ---
        add(pianoPanel, BorderLayout.SOUTH);

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1150, 720);
        setLocationRelativeTo(null);
        setVisible(true);

        SwingUtilities.invokeLater(() -> textArea.requestFocusInWindow());
    }

    public static void main(String[] args) { 
        SwingUtilities.invokeLater(() -> new Main()); 
    }
}