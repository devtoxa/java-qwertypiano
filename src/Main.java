import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Receiver;
import javax.sound.midi.ShortMessage;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

public class Main extends JFrame {
    private static final long serialVersionUID = 1L;
    private JTextArea textArea = null;
    
    // VirtualPiano normal keys (white keys / default notes)
    private Map<Integer, Integer> normalKeyMap = new HashMap<>();
    // VirtualPiano shifted keys (black keys / sharp notes)
    private Map<Integer, Integer> shiftKeyMap = new HashMap<>();
    
    private MidiDevice outDevice = null;
    private Receiver receiver = null;
    private String targetPortName = "loopMIDI Port";

    private Map<Integer, Integer> physicalKeyToMidi = new ConcurrentHashMap<>();

    private int releaseTime = 100; 
    private boolean isSpacePressed = false;
    private boolean isShiftPressed = false;
    private boolean isSustainToggled = false;

    // --- Transpose Settings ---
    private int transposeAmount = 0; // 반음 단위 (예: +2 = D key, -1 = B key)
    private static final int OCTAVE_STEP = 12;

    public Main() {
        initMidiPort();
        setVirtualPianoKeyMap();
        setTextArea();
        setFrame();
        updateStatus();
    }

    private void initMidiPort() {
        try {
            MidiDevice.Info[] infos = MidiSystem.getMidiDeviceInfo();
            for (MidiDevice.Info info : infos) {
                if (info.getName().contains(targetPortName)) {
                    MidiDevice device = MidiSystem.getMidiDevice(info);
                    if (device.getMaxReceivers() != 0) {
                        outDevice = device;
                        outDevice.open();
                        receiver = outDevice.getReceiver();
                        sendSustainCommand(false);
                        break;
                    }
                }
            }
        } catch (Exception e) { 
            e.printStackTrace(); 
        }
    }

    private void setVirtualPianoKeyMap() {
        // --- VirtualPiano.net Standard 61-Key Mapping (MIDI Note 36 ~ 96) ---

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
            "- Home Key: Reset Transpose to 0\n\n" +
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

                // --- Transpose Controls ---
                if (keyCode == KeyEvent.VK_UP) {
                    transposeAmount++;
                    updateStatus();
                    return;
                }
                if (keyCode == KeyEvent.VK_DOWN) {
                    transposeAmount--;
                    updateStatus();
                    return;
                }
                if (keyCode == KeyEvent.VK_PAGE_UP) {
                    transposeAmount += OCTAVE_STEP;
                    updateStatus();
                    return;
                }
                if (keyCode == KeyEvent.VK_PAGE_DOWN) {
                    transposeAmount -= OCTAVE_STEP;
                    updateStatus();
                    return;
                }
                if (keyCode == KeyEvent.VK_HOME) {
                    transposeAmount = 0;
                    updateStatus();
                    return;
                }

                // Prevent key repeat triggering multiple noteOn events
                if (physicalKeyToMidi.containsKey(keyCode)) return;

                // Virtual Piano lookup logic:
                // Shift down -> lookup sharp map first, fallback to normal map if no sharp exists
                Integer note = null;
                if (isShiftPressed) {
                    note = shiftKeyMap.get(keyCode);
                }
                if (note == null) {
                    note = normalKeyMap.get(keyCode);
                }

                if (note != null) {
                    // Apply Transpose Offset
                    int transposedNote = note + transposeAmount;

                    // MIDI standard note range protection (0 ~ 127)
                    if (transposedNote >= 0 && transposedNote <= 127) {
                        physicalKeyToMidi.put(keyCode, transposedNote);
                        noteON(transposedNote);
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
                    noteOFF(playedNote);
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

        SwingUtilities.invokeLater(() -> 
            setTitle("Virtual Piano MIDI Controller - Transpose: " + transposeStr + " | Sustain: " + sustain + " | Shift: " + shiftState)
        );
    }

    private void setFrame() {
        setLayout(new BorderLayout());
        JScrollPane scrollPane = new JScrollPane(textArea);
        add(scrollPane, BorderLayout.CENTER);
        
        textArea.setBackground(new Color(24, 24, 27));
        textArea.setForeground(new Color(244, 244, 245));
        textArea.setCaretColor(Color.WHITE);
        textArea.setFont(new Font("Consolas", Font.PLAIN, 18));
        
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(950, 550);
        setLocationRelativeTo(null);
        setVisible(true);
    }

    public static void main(String[] args) { 
        SwingUtilities.invokeLater(() -> new Main()); 
    }
}