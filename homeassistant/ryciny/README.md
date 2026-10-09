# Ryciny (dodatek Home Assistant)

Nasłuch, rozpoznawanie i Dziennik Rycin na komputerze z Home Assistant; telefon tylko wyświetla. Opis: [DOCS.md](DOCS.md).

Lokalnie, bez Home Assistant:

```bash
python3 -m venv .venv && .venv/bin/pip install -r app/requirements.txt pytest
cd app && RYCINY_DATA=/tmp/ryciny RYCINY_ASSETS=../../.. \
  RYCINY_AUDIO="../.venv/bin/python ../tests/feed.py nagranie.wav" ../.venv/bin/python -m ryciny
# http://localhost:8099
.venv/bin/python -m pytest tests
```
