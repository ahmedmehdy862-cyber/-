"""Generate the short, distinctive notification chime shipped in res/raw.

No third-party audio is needed for this sound: it is a deterministic three-note
sine chime with a soft attack/release, so the repository can regenerate it any time.
"""

import math
import struct
import wave
from pathlib import Path

OUTPUT = Path(__file__).resolve().parents[1] / "app/src/main/res/raw/notification_chime.wav"
SAMPLE_RATE = 44100
NOTES = [(880.0, 0.30), (1174.66, 0.30), (1567.98, 0.42)]
ATTACK = 0.012
RELEASE = 0.10


def envelope(index: int, total: int) -> float:
    t = index / SAMPLE_RATE
    attack = min(1.0, t / ATTACK)
    release = min(1.0, (total - index) / SAMPLE_RATE / RELEASE)
    return max(0.0, min(attack, release))


def main() -> None:
    frames = []
    for frequency, duration in NOTES:
        for index in range(int(SAMPLE_RATE * duration)):
            t = index / SAMPLE_RATE
            # A quiet octave overtone keeps the chime bright without sounding harsh.
            sample = 0.78 * math.sin(2 * math.pi * frequency * t)
            sample += 0.18 * math.sin(4 * math.pi * frequency * t)
            sample *= envelope(index, int(SAMPLE_RATE * duration)) * 0.5
            frames.append(struct.pack("<h", int(max(-1.0, min(1.0, sample)) * 24000)))

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    with wave.open(str(OUTPUT), "wb") as wav:
        wav.setnchannels(1)
        wav.setsampwidth(2)
        wav.setframerate(SAMPLE_RATE)
        wav.writeframes(b"".join(frames))
    print(f"wrote {OUTPUT} ({len(frames) / SAMPLE_RATE:.2f}s)")


if __name__ == "__main__":
    main()
