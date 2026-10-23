# com.dan.beat – Musik fühlen (Avatar Phase 17, Kür)

Takt, Stimmung und Liedtext einer Tonspur für Szene und Person. Das Package kennt weder Avatar noch Ton-Ausgabe: es
liest eine WAV-Datei, rechnet je 10 ms Einsätze, Lautheit, Helligkeit und Ruhe, findet Tempo, Schläge und die Eins und
liest Liedtexte mit Zeitmarken (LRC). Nur Java 21, ohne Grafikkarte, ohne Fremdbibliothek.

| Teil | Aufgabe |
|---|---|
| `Track` | WAV lesen (PCM 8/16/24/32 Bit, Gleitkomma, mehrere Kanäle gemittelt), Ausschnitt, PCM 16 Bit, WAV schreiben. MP3/M4A werden erkannt und mit Hinweis abgelehnt. |
| `Rhythm` | Spektraler Fluss in 24 Bändern (FFT), Tempo aus der Autokorrelation (Vorliebe 110/min), Schläge per dynamischer Programmierung (Ellis 2007), Eins im 4/4 aus den tiefen Einsätzen; Lautheit, Helligkeit, Ruhe (0 … 1); `beatAt(t)` zählt die Schläge stetig, `pulse(t)` für Licht. |
| `Lyrics` | LRC: `[mm:ss.xx]Text`, mehrere Marken je Zeile, leere Marke beendet die Zeile, `[ti:] [ar:] [la:] [offset:]`, Bemerkungen mit `#`. Erweiterung `Text | Lautung`: links der Untertitel, rechts wie es klingt (deutsche Schreibung, für den Mund). |

## Benutzung

```java
Track lied = Track.read(new File("daten/musik/hush_little_baby.wav"));
Rhythm r = Rhythm.analyse(lied);                  // 113,0 Schläge/min, Eins, Lautheit, Ruhe
Lyrics text = Lyrics.read(new File("daten/musik/hush_little_baby.lrc"));
behavior.musicBeat = r.beatAt(t);                 // com.dan.avatar.Behavior: Nicken und Wiegen im Takt
behavior.groove = 0.35 + 0.65 * r.energy(t);
behavior.musicCalm = r.calm(t);                   // ruhige Stellen: Augen zu
// Singen: com.dan.voice.Speech.sung(Lautungen, Anfänge, Enden)
```

Im Studio: Block MUSIK (Lied aus `daten/musik`, „Abspielen“, „Musik-Visual“ im Kino mit Porträt, Regen, Lichtern im
Takt und Schnitten auf der Eins). Statusbild und Video mit Ton: `ant statusbild`.

## Grenzen

- Das Tempo darf nicht stark schwanken (ein Tempo je Lied, Schläge folgen kleinen Schwankungen).
- Takt immer 4/4; Dreiertakt (Walzer) wird als 4/4 gezählt.
- Wo im Lied gesungen wird, sagt die LRC; den Gesang selbst hört das Package nicht heraus.
