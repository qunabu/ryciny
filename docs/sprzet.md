# Sprzęt

## Zamówione (4 października 2026, AliExpress)

| Co | Model | Sprzedawca | Cena | Razem z dostawą |
| --- | --- | --- | --- | --- |
| Mikrofon | [Primo EM272Z1, krawatowy, ręcznie robiony, kabel 1,5 m, wtyczka 3,5 mm TRS](https://pl.aliexpress.com/item/1005009770962671.html) | Micverve Store | 174,59 zł | 206,57 zł |
| Przedłużacz | [CableCreation 3,5 mm TRS, męski → żeński, 4,5 m](https://pl.aliexpress.com/item/1005004731100981.html) | FengMei Store | 30,19 zł | 50,01 zł |

- Mikrofon ma kapsułę Primo EM272Z1, tę samą co Clippy EM272 polecany przez BirdNET-Pi (szum własny ok. 14 dB(A)).
  W zestawie są osłony od wiatru: piankowa i futrzana.
- Wtyczki są TRS (stereo, bez mikrofonowego pierścienia telefonu), więc mikrofon z przedłużaczem wchodzi prosto
  do karty dźwiękowej USB. Do tabletu albo telefonu potrzebna byłaby jeszcze przejściówka TRS → TRRS (np. Rode SC4).
- Złączkę mikrofonu z przedłużaczem trzymaj w środku, nie za oknem, a kabla nie zwijaj przy zasilaczach,
  bo 4,5 m nieekranowanego kabla łapie przydźwięk.

## Następny etap: podłączenie do Home Assistant

Mikrofon trafi do komputera z Home Assistant (HA OS), który będzie zbierał dźwięki 24/7, a telefon czy tablet
tylko je wyświetlał. Plan: [plan.md](../plan.md).

Do dokupienia:

| Co | Po co | Ile |
| --- | --- | --- |
| Karta dźwiękowa USB z wejściem mikrofonowym (np. UGREEN) | daje mikrofonowi zasilanie (plug-in power) i wpuszcza dźwięk do HA | ok. 40 zł |
| Przedłużacz USB (opcjonalnie) | jeśli komputer z HA stoi dalej niż 4,5 m od okna: karta przy oknie, dźwięk po USB cyfrowo, bez szumu | ok. 20–40 zł |

## Później: kolorowy ekran e-ink (jak w fugleramme)

Ten sam panel co w Pimoroni Inky Impression (E Ink Spectra 6, 6 kolorów: czarny, biały, czerwony, zielony,
niebieski, żółty), od Waveshare, z nakładką HAT dla Raspberry Pi. Samego Inky Impression w polskich sklepach
nie ma; Waveshare jest w [Kamami](https://kamami.pl/szukaj?controller=search&s=spectra+6). Ceny z 5 października 2026.

| Rozmiar | Model | Cena | Dostępność |
| --- | --- | --- | --- |
| 4" 600×400 | Waveshare 27367, z HAT | 226,69 zł | ok. 4 tygodnie |
| **7,3" 800×480 (polecany)** | [Waveshare, z HAT](https://kamami.pl/en/e-paper-displays/1197403-7-3inch-e-ink-spectra-6-e6-full-color-e-paper-display-e-ink-display-low-power-consumption-800-4-5902186321373.html) | **385,17 zł** | ok. 4 tygodnie |
| 13,3" 1600×1200 (jak ramka fugleramme) | Waveshare 29355, z HAT | 1 318,77 zł | od ręki |

- 4" jest za mały na ramkę (drobne podpisy), 13,3" wygląda najlepiej, ale kosztuje ponad 1300 zł. 7,3" to rozsądny środek.
- Bierz wersję **z HAT**: sam panel (np. EL073TF1 z AliExpressu) nie ma płytki sterującej i nie nałoży się na Raspberry Pi.
  Starsza wersja 7,3" (F) w kolorach ACeP na Allegro (ok. 830 zł) jest droższa i ma bledsze kolory.
- Waveshare to nie Pimoroni: panel ten sam, ale bez przycisków z boku i z innym sterownikiem. fugleramme obsługuje
  tylko Inky, więc sterownik Waveshare trzeba dopisać (opis w [plan.md](../plan.md)).
- **Gotowa ramka bez Raspberry Pi:** Seeed reTerminal E1002 (7,3" Spectra 6 + ESP32-S3 w obudowie, bateria,
  ESPHome) za ok. 94 USD na AliExpress. Inne możliwe urządzenia ramki i ich porównanie: [plan.md](../plan.md#urządzenie-ramki).
- Ekran nakłada się na Raspberry Pi. Jeśli Home Assistant działa na Raspberry Pi przy ścianie, ekran może siedzieć
  na nim; jeśli HA stoi gdzie indziej, przy ramce potrzebne jest osobne **Raspberry Pi Zero 2 W** (ok. 80–100 zł)
  z kartą microSD i zasilaczem, które pobiera rycinę z HA.

## Wariant bez Home Assistant: tablet jako ramka

Używany tablet z Androidem i zewnętrzny mikrofon za oknem, z aplikacją nasłuchującą na tablecie. Ceny z października 2026.

| Co | Model | Gdzie | Ile |
| --- | --- | --- | --- |
| Tablet | Samsung Galaxy Tab A7 10.4 (SM-T500 / SM-T505), używany | [OLX](https://www.olx.pl/elektronika/komputery/tablety/q-samsung-galaxy-tab-a7/) | ok. 300–450 zł |
| Mikrofon | Clippy EM272, wersja **TRRS** (do telefonu) | [Micbooster](https://micbooster.com) | ok. 60 USD |
| Osłona od wiatru | futrzana osłona do Clippy EM272 | Micbooster | ok. 25 USD |

Razem: ok. 650–800 zł.

## Tablet: Samsung Galaxy Tab A7 10.4

- Android 10–12. Aplikacja wymaga Androida 10 lub nowszego i 64-bitowego procesora, a Tab A7 spełnia oba warunki.
- Ekran 10,4", czyli dobry rozmiar na ramkę.
- **Gniazdo słuchawkowe 3,5 mm.** To najważniejsza cecha: mikrofon wchodzi do gniazda, a USB-C zostaje wolne
  do ładowania 24/7, bez przejściówek i hubów.
- W ustawieniach baterii włącz ochronę baterii (ładowanie do 85%). Przy pracy non stop wydłuża to jej życie.
- Na OLX jest zwykle sporo ofert po 300–450 zł, także z Gdańska. Wersja 32 GB wystarczy.

Tańsza alternatywa: Lenovo Tab M10 HD 2. generacji. Też ma gniazdo 3,5 mm, ale słabszy procesor.

Na co uważać przy zakupie:
- bateria nie powinna puchnąć (ekran nie może odstawać od obudowy);
- tablet musi być wylogowany z konta Google sprzedawcy (bez blokady FRP);
- port USB-C musi ładować.

## Mikrofon: Clippy EM272 TRRS

- Kapsuła Primo EM272: lepsza czułość i mniej szumu niż typowe mikrofony krawatowe. To popularny wybór do
  nagrywania ptaków ([forum iNaturalist](https://forum.inaturalist.org/t/clip-on-directional-microphone-for-android-phone/29112),
  [nagrania na xeno-canto](https://xeno-canto.org/736683)).
- Dookólny: łapie ptaki, psy, kosiarkę i samoloty ze wszystkich stron.
- Zamów wersję **TRRS** (wtyczka do telefonu), nie TRS ani XLR. Android sam przełącza nagrywanie na mikrofon
  podłączony do gniazda.

Tańsza alternatywa: dowolny mikrofon krawatowy z wtyczką TRRS, np. Boya BY-M1 (ok. 50 zł). Zadziała, ale ciche
ptaki z daleka będzie łapał gorzej.

## Montaż

- **Mikrofon na zewnątrz**, np. pod parapetem albo na ramie okna, w osłonie od wiatru i osłonięty od deszczu.
  Przez szybę ptaki są prawie niesłyszalne.
- Kabel przejdzie przez uszczelkę zamkniętego okna. Jeśli jest za krótki, dokup przedłużacz TRRS 3,5 mm.
- Tablet zostaje w środku, podłączony do ładowarki. W aplikacji zostaw włączone „Nie wygaszaj ekranu (tryb ramki)”.

Aplikacji nie testowano jeszcze ani na Tab A7, ani z zewnętrznym mikrofonem, tylko na Pixelu 9 z wbudowanym
mikrofonem. Jeśli coś nie zadziała, najpierw sprawdź na pasku na dole ryciny, czy aplikacja słyszy dźwięk
(„słychać: …”).
