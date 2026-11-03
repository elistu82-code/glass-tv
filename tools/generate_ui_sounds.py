#!/usr/bin/env python3
"""Original procedural UI samples, CC0-1.0. No recordings or third-party assets."""
import math
import struct
import wave
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / 'app/src/main/res/raw'

def render(name, duration, frequency, decay):
    rate = 22050
    samples = []
    for index in range(round(duration * rate)):
        t = index / rate
        attack = min(1., t / .0015)
        release = min(1., (duration - t) / .006)
        body = math.sin(2 * math.pi * frequency * t) + .22 * math.sin(2 * math.pi * frequency * 2.13 * t)
        value = .22 * body * attack * release * math.exp(-t / decay)
        samples.append(round(32767 * value))
    ROOT.mkdir(parents=True, exist_ok=True)
    with wave.open(str(ROOT / name), 'wb') as output:
        output.setnchannels(1)
        output.setsampwidth(2)
        output.setframerate(rate)
        output.writeframes(struct.pack('<' + 'h' * len(samples), *samples))

if __name__ == '__main__':
    render('ui_navigation.wav', .038, 980., .007)
    render('ui_confirm.wav', .058, 1240., .012)
