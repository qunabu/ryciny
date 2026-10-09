"""Start: python -m ryciny (Home Assistant runs it in the add-on; locally with RYCINY_DATA / RYCINY_ASSETS / RYCINY_AUDIO)."""

from __future__ import annotations

import json
import logging
import signal
import sys

from . import config
from .analyzer import Analyzer, Live
from .api import App
from .art import Art
from .assets import Assets
from .audio import Listener
from .rails import Rails
from .sky import Sky
from .store import Store


def main() -> None:
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")
    cfg = config.load()
    assets = Assets(cfg.data, cfg.assets)
    store = Store(cfg.data)

    def stop(*_):
        # Home Assistant stops add-ons with SIGTERM (updates, restarts): keep the last seconds of the journal.
        store.flush()
        sys.exit(0)

    signal.signal(signal.SIGTERM, stop)
    signal.signal(signal.SIGINT, stop)
    sky = Sky(cfg, store, json.loads(assets.text("aircraft_types.json")))
    rails = Rails(cfg, store)
    art = Art(cfg, assets)
    live = Live()
    analyzer = Analyzer(cfg, assets, store, sky, rails, live)
    sky.start()
    rails.start()
    Listener(cfg.audio_command, analyzer, live).start()
    App(cfg, assets, store, sky, rails, art, live).serve()


if __name__ == "__main__":
    main()
