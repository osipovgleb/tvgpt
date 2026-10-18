#!/usr/bin/env python3
"""Reproduce TV GPT's original two-note public cue; no sampled audio is used."""
import math
import struct
import wave
from pathlib import Path

RATE = 48000
DURATION = 0.48


def note(t, start, frequency):
    local = t - start
    length = 0.32
    if not 0 <= local < length:
        return 0.0
    attack = min(1.0, local / 0.012)
    tail = max(0.0, 1.0 - local / length) ** 2
    envelope = attack * tail
    phase = 2 * math.pi * frequency * local
    return envelope * (math.sin(phase) + 0.12 * math.sin(2 * phase))


if __name__ == '__main__':
    destination = Path(__file__).resolve().parents[1] / 'app/assets/voice-start.wav'
    frames = bytearray()
    for i in range(round(RATE * DURATION)):
        t = i / RATE
        value = 0.24 * (note(t, 0, 660) + note(t, 0.16, 880))
        frames.extend(struct.pack('<h', round(value * 32767)))
    with wave.open(str(destination), 'wb') as audio:
        audio.setnchannels(1)
        audio.setsampwidth(2)
        audio.setframerate(RATE)
        audio.writeframes(frames)
    print('Generated original public startup cue.')
