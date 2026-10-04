# Zmiany

Numeracja: `MAJOR.MINOR.PATCH` (semver). Wersja jest w `android/gradle.properties` (`ryciny.version`),
tag w gicie to `vX.Y.Z`, a każdy tag ma release z plikiem APK.

## 0.5.1 - 2026-10-04

- Pociągi usłyszane, zanim rozkład się wczytał, dostają trasę wstecz, gdy tylko rozkład jest gotowy (wcześniej zostawały jako sam „Przejechał pociąg”).
- Rozkład przelicza się od razu, gdy zmieni się lokalizacja; wcześniej po starcie przez pół godziny mógł być liczony dla centrum Gdańska.
- Gdy pociąg nie ma trasy, rycina mówi dlaczego (rozkład się wczytuje, brak rozkładu, brak pociągu ±5 min).

## 0.5.0 - 2026-10-04

- Pociągi: gdy mikrofon usłyszy pociąg, aplikacja dopasowuje go do rozkładu wszystkich polskich pociągów (SKM Trójmiasto, PolRegio, Intercity…; GTFS z mkuran.pl na danych PKP PLK) i pokazuje przewoźnika, numer, skąd i dokąd jedzie oraz planową godzinę. Rozkład (30 MB) pobiera się przez Wi-Fi raz na 3 dni; opóźnienia nie są uwzględniane.
- Ryciny dźwięków i psów generują się w tle, gdy dany dźwięk lub pies pojawi się pierwszy raz, więc Dziennik od razu ma ich miniatury (np. koń). Samoloty dalej na dotknięcie.

## 0.4.0 - 2026-10-04

- Dziennik: miniatura ryciny przy każdym wpisie (ptak, samolot, pies, dźwięk). Miniatury pokazują tylko ryciny zapisane w telefonie, więc przewijanie niczego nie generuje; płatną rycinę samolotu zamawia się dotknięciem wpisu. Darmowe tablice ptaków dociągają się same.

## 0.3.1 - 2026-10-04

- Dolne menu ma trzy zakładki: lista znanych psów przeszła do Dziennika (filtr „psy”), nad szczekania.
- Kosiarka łapie się z większej odległości: próg z 0,3 na 0,15 (piła łańcuchowa z 0,35 na 0,25). Kosiarka sąsiada przy progu 0,3 nie została zapisana.

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
