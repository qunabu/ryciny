# Zmiany

Numeracja: `MAJOR.MINOR.PATCH` (semver). Wersja jest w `android/gradle.properties` (`ryciny.version`),
tag w gicie to `vX.Y.Z`, a każdy tag ma release z plikiem APK.

## 0.1.0 - 2026-10-04

Pierwsza wersja testowa na Androida.

- Nasłuch w tle: BirdNET 6K v2.4 (ptaki, filtr gatunków dla miejsca i tygodnia) i YAMNet (szczekanie, silniki samolotów).
- Samoloty z adsb.lol i adsbdb: typ, linia, rejestracja, trasa, wysokość, odległość, kierunek.
- Psy: wykrywanie szczekania, szacunek wielkości z wysokości głosu, oznaczanie psów z sąsiedztwa („Kto to?”) i rozpoznawanie ich po próbkach.
- Rycina pokazuje jedną rzecz naraz: najnowszą z samolotu nad głową, ostatniego ptaka i ostatniego psa. Dotknięcie przełącza na następną.
- Ryciny ptaków z fugleramme; samoloty i psy generowane przez OpenAI lub Gemini, wycinane z tła i zapisywane w telefonie.
- Dziennik z odsłuchem szczekania, lista psów, ustawienia, eksport ZIP dla wersji na Raspberry Pi.
