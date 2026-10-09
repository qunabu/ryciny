"""Microphone: a command that writes 48 kHz mono float32 to stdout (parec on HA OS), cut into 3 s windows."""

from __future__ import annotations

import logging
import queue
import shlex
import subprocess
import threading
import time

import numpy as np

from .ml import BirdNet
from .store import now_ms

log = logging.getLogger(__name__)


class Listener:
    def __init__(self, command: str, analyzer, live) -> None:
        self.command = command
        self.analyzer = analyzer
        self.live = live
        self.windows: queue.Queue = queue.Queue(maxsize=1)

    def start(self) -> None:
        threading.Thread(target=self._read, daemon=True, name="audio").start()
        threading.Thread(target=self._analyze, daemon=True, name="analyze").start()

    def _read(self) -> None:
        size = BirdNet.SAMPLES * 4
        while True:
            log.info("audio: %s", self.command)
            try:
                proc = subprocess.Popen(shlex.split(self.command), stdout=subprocess.PIPE, stderr=subprocess.PIPE)
            except OSError as e:
                self.live.listening, self.live.error = False, f"nie mogę uruchomić nagrywania: {e}"
                time.sleep(30)
                continue
            self.live.listening, self.live.error = True, None
            while True:
                raw = proc.stdout.read(size)
                if len(raw) < size:
                    break
                window = np.frombuffer(raw, dtype="<f4").copy()
                if self.windows.full():
                    try:
                        self.windows.get_nowait()  # analysis behind: drop the oldest window
                    except queue.Empty:
                        pass
                self.windows.put((window, now_ms()))
            err = proc.stderr.read().decode(errors="replace")[-300:]
            proc.wait()
            self.live.listening = False
            self.live.error = f"nagrywanie przerwane (kod {proc.returncode}) {err}".strip()
            log.warning("audio stopped: %s", self.live.error)
            time.sleep(5)

    def _analyze(self) -> None:
        while True:
            window, at = self.windows.get()
            try:
                self.analyzer.analyze(window, at)
            except Exception:  # noqa: BLE001
                log.exception("analyze")
