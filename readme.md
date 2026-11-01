# Virtual Piano MIDI Controller

Java Swing과 Java Sound API(MIDI)를 기반으로 제작된 가상 피아노 MIDI 컨트롤러입니다.  
`VirtualPiano.net` 레이아웃 방식의 키보드 매핑을 지원하며, 가상 MIDI 포트(예: loopMIDI)를 통해 DAWs나 음원 모듈로 MIDI 신호를 전송할 수 있습니다.

---

## 💡 주요 기능

1. **VirtualPiano.net 표준 61키 매핑**
   - 숫자, 알파벳, 특수문자를 이용해 5개 옥타브(MIDI Note 36 ~ 96)를 연주할 수 있습니다.
   - `Shift` 키 조합을 통해 샵(#) 노트(검은 건반)를 연주합니다.

2. **MIDI 디바이스 선택 및 가상 포트 연동**
   - 시스템에 연결된 MIDI 출력 장치 리스트를 실시간으로 탐색 및 선택할 수 있습니다.
   - `loopMIDI Port`가 존재하는 경우 자동으로 인식하여 우선 연결합니다.

3. **이조(Transpose) 및 옥타브 이동**
   - 반음 및 옥타브 단위로 키 스케일을 실시간 조절할 수 있습니다.
   - 88건반 범위(A0 ~ C8 / MIDI Note 21 ~ 108) 내에서 안전하게 제한됩니다.

4. **서스테인 페달 (Sustain Pedal) 제어**
   - `Spacebar` 홀드 또는 `Shift + Backspace` 토글을 통해 서스테인 페달(CC #64) 효과를 제어합니다.
   - 노트 오프 시 여운 시간을 설정하는 **Release Time** 조절 기능이 포함되어 있습니다.

5. **리버브 효과 (Reverb Effect) 제어**
   - `Shift + \` 단축키로 리버브(CC #91)를 On/Off 제어합니다.
   - 대괄호(`[`, `]`) 키를 통해 리버브 깊이(Depth)를 조절할 수 있습니다.

6. **88건반 GUI 시각화 (`PianoPanel`)**
   - 실제 피아노와 동일한 88건반 그래픽을 화면 하단에 렌더링합니다.
   - 트랜스포즈 상태에 따른 연주 가능 영역 및 비활성 영역(Dimmed 처리)을 명확하게 구분합니다.
   - 연주 중인 건반은 주황색(Active)으로 강조 표시됩니다.

---

## ⌨️ 키 매핑 및 컨트롤 가이드

### 1. 연주 키 매핑 (61 기본 키)

| 옥타브 | 범위 | 흰 건반 매핑 | 샵(#) 건반 매핑 (Shift 조합) |
| :--- | :--- | :--- | :--- |
| **Octave 1** | C2 ~ B2 | `1`, `2`, `3`, `4`, `5`, `6`, `7` | `!`, `@`, `$`, `%`, `^` |
| **Octave 2** | C3 ~ B3 | `8`, `9`, `0`, `q`, `w`, `e`, `r` | `*`, `(`, `Q`, `W`, `E` |
| **Octave 3** | C4 ~ B4 | `t`, `y`, `u`, `i`, `o`, `p`, `a` *(Middle C = `t`)* | `T`, `Y`, `I`, `O`, `P` |
| **Octave 4** | C5 ~ B5 | `s`, `d`, `f`, `g`, `h`, `j`, `k` | `S`, `D`, `G`, `H`, `J` |
| **Octave 5** | C6 ~ C7 | `l`, `z`, `x`, `c`, `v`, `b`, `n`, `m` | `L`, `Z`, `C`, `V`, `B` |

---

### 2. 컨트롤 단축키

| 기능 | 단축키 | 설명 |
| :--- | :--- | :--- |
| **Sustain Pedal** | `Spacebar` (Hold) | 누르고 있는 동안 서스테인 유지 |
| **Sustain Toggle** | `Shift + Backspace` | 서스테인 페달 상태 토글 (ON / OFF) |
| **Transpose (반음)** | `Up` / `Down` Arrow | 반음 단위 이조 ($\pm 1$) |
| **Transpose (옥타브)**| `Page Up` / `Page Down` | 옥타브 단위 이조 ($\pm 12$) |
| **Transpose Reset** | `Home` | 트랜스포즈 0 초기화 |
| **Reverb On/Off** | `Shift + \` | 리버브 효과(CC #91) 토글 |
| **Reverb Depth** | `[` / `]` | 리버브 깊이 조절 ($\pm 10$, Shift 조합 시 $\pm 1$) |
| **Release Time** | `-` / `=` (`+`) | 서스테인 릴리즈 시간 조절 ($\pm 50\text{ms}$, Shift 조합 시 $\pm 10\text{ms}$) |

---

## 🏗️ 코드 구조

- **`Main` (`JFrame`)**: 메인 애플리케이션 프레임워크.
  - `setVirtualPianoKeyMap()`: VirtualPiano 키-MIDI 노트 번호 매핑 등록.
  - `switchMidiDevice()` / `refreshMidiDevices()`: MIDI 출력 장치 검색 및 동적 연결 관리.
  - `keyPressed()` / `keyReleased()`: 키보드 입력 이벤트를 감지하여 MIDI `NoteOn`, `NoteOff`, Control Change 메시지 전송.
- **`PianoPanel` (`JPanel`)**: 하단 88건반 GUI 커스텀 렌더링 컴포넌트.
  - `paintComponent()`: 88개 건반의 위치, 옥타브 라벨(C1~C8), 활성화/비활성화 색상 및 눌림 효과 그래픽 렌더링.
- **`MidiDeviceItem`**: MIDI 장치 정보를 `JComboBox`에 표시하기 위한 래퍼(Wrapper) 클래스.

---

## 🛠️ 실행 환경 및 요구 사항

- **Java SE 8** 이상 (Swing & javax.sound.midi 활용)
- **가상 MIDI 드라이버** (선택 사항, 추천: [loopMIDI](https://www.tobias-erichsen.de/software/loopmidi.html))
  - DAW(예: Ableton, FL Studio, Cubase 등)나 가상 악기(VSTi)로 MIDI 신호를 보낼 때 필요합니다.
```
