"""Stand-in microphone for local runs: plays WAV files as 48 kHz mono float32 on stdout, in real time, looping.

RYCINY_AUDIO="python tests/feed.py a.wav b.wav" python -m ryciny
"""

import sys
import time
import wave

import numpy as np

RATE = 48_000


def load(path: str) -> np.ndarray:
    with wave.open(path) as w:
        x = np.frombuffer(w.readframes(w.getnframes()), dtype="<i2").astype(np.float32) / 32768
        x = x.reshape(-1, w.getnchannels()).mean(axis=1)
        src = w.getframerate()
    n = int(len(x) * RATE / src)
    return np.interp(np.linspace(0, len(x) - 1, n), np.arange(len(x)), x).astype("<f4")


clips = [load(p) for p in sys.argv[1:]]
silence = np.zeros(RATE * 3, dtype="<f4")
while True:
    for clip in clips + [silence]:
        for i in range(0, len(clip), RATE // 2):
            sys.stdout.buffer.write(clip[i:i + RATE // 2].tobytes())
            sys.stdout.buffer.flush()
            time.sleep(0.5)
