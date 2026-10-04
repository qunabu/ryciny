# Zmiany

Numeracja: `MAJOR.MINOR.PATCH` (semver). Wersja jest w `android/gradle.properties` (`ryciny.version`),
tag w gicie to `vX.Y.Z`, a każdy tag ma release z plikiem APK.

## 0.3.0 - 2026-10-04

- 31 dźwięków okolicy, każdy z własną ryciną: kosiarka, piła łańcuchowa, majsterkowanie, burza, deszcz, wiatr, statek i syrena mgłowa, dzwony kościelne, pociąg, motocykl, helikopter, karetka, policja, straż pożarna, syrena alarmowa, fajerwerki, alarm samochodowy, kot, kogut, koń, krowa, owca, koza, świnia, żaby, świerszcze, pszczoły i osy, komar, muzyka u sąsiadów, dzwonek do drzwi, lodziarz.
- Lista dźwięków, ich progi i prompty rycin są w `shared/sounds.json`, wspólnym z wersją na Pi. Mowa, płacz i inne ludzkie odgłosy są celowo pominięte.
- Dziennik: filtr „dźwięki”.

## 0.2.0 - 2026-10-04

- Kosiarka: YAMNet wykrywa koszenie trawy u sąsiada, a rycina pokazuje je jako tablicę z XIX-wiecznego poradnika ogrodniczego.
- Dziennik: przy każdym samolocie jest przycisk „Rycina”, który pokazuje rycinę tego typu w barwach linii (i ją generuje, jeśli jeszcze jej nie ma).
- Ryciny nie są generowane dwa razy: generowanie dokańcza się nawet po przełączeniu ekranu, oryginał z API jest zapisywany przed wycinaniem tła, a w Ustawieniach widać, ile rycin jest w pamięci.

## 0.1.0 - 2026-10-04

Pierwsza wersja testowa na Androida.

- Nasłuch w tle: BirdNET 6K v2.4 (ptaki, filtr gatunków dla miejsca i tygodnia) i YAMNet (szczekanie, silniki samolotów).
- Samoloty z adsb.lol i adsbdb: typ, linia, rejestracja, trasa, wysokość, odległość, kierunek.
- Psy: wykrywanie szczekania, szacunek wielkości z wysokości głosu, oznaczanie psów z sąsiedztwa („Kto to?”) i rozpoznawanie ich po próbkach.
- Rycina pokazuje jedną rzecz naraz: najnowszą z samolotu nad głową, ostatniego ptaka i ostatniego psa. Dotknięcie przełącza na następną.
- Ryciny ptaków z fugleramme; samoloty i psy generowane przez OpenAI lub Gemini, wycinane z tła i zapisywane w telefonie.
- Dziennik z odsłuchem szczekania, lista psów, ustawienia, eksport ZIP dla wersji na Raspberry Pi.
