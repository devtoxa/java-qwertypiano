

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
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

public class Main extends JFrame {
    private static final long serialVersionUID = 1L;
    private JTextArea textArea = null;
    private Map<Integer, Integer> keyMap = null; // String ��� Integer(KeyCode) ���
    private MidiDevice outDevice = null;
    private Receiver receiver = null;
    private String targetPortName = "loopMIDI Port";

    private Map<Integer, Integer> physicalKeyToMidi = new ConcurrentHashMap<>();

    private int releaseTime = 100; 
    private boolean isSpacePressed = false;
    private boolean isShiftPressed = false;
    private boolean isSustainToggled = false;

    public Main() {
        initMidiPort();
        setKeyMap(); // 
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
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void setKeyMap() {
        keyMap = new HashMap<>();
        // �������� ���� KeyEvent ����� ����Ͽ� ���� Ű ��ġ�� �����մϴ�.
        // --- �ϴ� ���̾� ---
        keyMap.put(KeyEvent.VK_A, 44); keyMap.put(KeyEvent.VK_Z, 45); 
        keyMap.put(KeyEvent.VK_S, 46); keyMap.put(KeyEvent.VK_X, 47);
        keyMap.put(KeyEvent.VK_C, 48); keyMap.put(KeyEvent.VK_F, 49); 
        keyMap.put(KeyEvent.VK_V, 50); keyMap.put(KeyEvent.VK_G, 51);
        keyMap.put(KeyEvent.VK_B, 52); keyMap.put(KeyEvent.VK_N, 53); 
        keyMap.put(KeyEvent.VK_J, 54); keyMap.put(KeyEvent.VK_M, 55);
        keyMap.put(KeyEvent.VK_K, 56); keyMap.put(KeyEvent.VK_COMMA, 57); 
        keyMap.put(KeyEvent.VK_L, 58); keyMap.put(KeyEvent.VK_PERIOD, 59);
        keyMap.put(KeyEvent.VK_SLASH, 60); keyMap.put(KeyEvent.VK_QUOTE, 61);

        // --- ��� ���̾� (����Ű ��) ---
        keyMap.put(KeyEvent.VK_1, 56); keyMap.put(KeyEvent.VK_Q, 57);
        keyMap.put(KeyEvent.VK_2, 58); keyMap.put(KeyEvent.VK_W, 59);
        keyMap.put(KeyEvent.VK_E, 60); keyMap.put(KeyEvent.VK_4, 61);
        keyMap.put(KeyEvent.VK_R, 62); keyMap.put(KeyEvent.VK_5, 63);
        keyMap.put(KeyEvent.VK_T, 64); keyMap.put(KeyEvent.VK_Y, 65);
        keyMap.put(KeyEvent.VK_7, 66); keyMap.put(KeyEvent.VK_U, 67);
        keyMap.put(KeyEvent.VK_8, 68); keyMap.put(KeyEvent.VK_I, 69);
        keyMap.put(KeyEvent.VK_9, 70); keyMap.put(KeyEvent.VK_O, 71);
        keyMap.put(KeyEvent.VK_P, 72); keyMap.put(KeyEvent.VK_MINUS, 73);
        keyMap.put(KeyEvent.VK_OPEN_BRACKET, 74); keyMap.put(KeyEvent.VK_EQUALS, 75);
        keyMap.put(KeyEvent.VK_CLOSE_BRACKET, 76);
    }

    private void setTextArea() {
        textArea = new JTextArea();
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

                // ���� Ű���� �ڵ�(keyCode)�� ��Ʈ�� ã��
                Integer baseNote = keyMap.get(keyCode);
                if (baseNote != null) {
                    // �̹� �����ִ� Ű��� �ߺ� ���� ����
                    if (physicalKeyToMidi.containsKey(keyCode)) return;

                    // Shift�� �������� ���� +12(1��Ÿ��) ����
                    int finalNote = baseNote + (isShiftPressed ? 12 : 0);
                    
                    if (finalNote >= 0 && finalNote <= 127) {
                        physicalKeyToMidi.put(keyCode, finalNote);
                        noteON(finalNote);
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

                // ���� �� �����ߴ� finalNote�� �����ͼ� ��
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
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void noteON(int noteNumber) {
        try {
            if (receiver != null) {
                ShortMessage msg = new ShortMessage();
                msg.setMessage(ShortMessage.NOTE_ON, 0, noteNumber, 93);
                receiver.send(msg, -1);
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void noteOFF(int noteNumber) {
        new Thread(() -> {
            try { Thread.sleep(releaseTime); } catch (InterruptedException e) {}
            try {
                if (receiver != null) {
                    ShortMessage msg = new ShortMessage();
                    msg.setMessage(ShortMessage.NOTE_OFF, 0, noteNumber, 93);
                    receiver.send(msg, -1);
                }
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }

    private void updateStatus() {
        String sustain = (isSustainToggled || isSpacePressed) ? "ON" : "OFF";
        String octave = isShiftPressed ? "+1 Octave" : "Normal";
        SwingUtilities.invokeLater(() -> setTitle("MIDI Controller - Sustain: " + sustain + " | Octave: " + octave));
    }

    private void setFrame() {
        setLayout(new BorderLayout());
        add(textArea, BorderLayout.CENTER);
        textArea.setBackground(Color.black);
        textArea.setForeground(Color.white);
        textArea.setFont(new Font("Consolas", Font.PLAIN, 24));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(900, 500);
        setLocationRelativeTo(null);
        setVisible(true);
    }

    public static void main(String[] args) { new Main(); }
}