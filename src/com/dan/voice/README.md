# com.dan.voice – Sprechen (Avatar Phase 16, Kür)

Ein gesprochener Satz als Spur der Mundbilder: aus deutschem Text, aus einer Tonspur oder aus beidem. Das Package kennt
keinen Avatar: Es liefert je Zeitpunkt die Gewichte der 15 Viseme (Oculus-Satz, `visem.viseme_PP` …), dazu wann
eingeatmet wird, wo Phrasen enden und ob gerade gesprochen wird. Nur Java 21, ohne Grafikkarte, ohne Fremdbibliothek.

| Teil | Aufgabe |
|---|---|
| `Viseme` | Die 15 Mundbilder mit deutschen Lauten und Öffnung. |
| `Phon` | Ein Laut: Zeichen, Art, Mundbild (bei Doppellauten zwei), Dauer, Betonung. |
| `G2P` | Deutscher Text → Laute nach Regeln (sch, ch nach a/o/u als ach-Laut, sp/st am Wortanfang, Doppellaute, Dehnungs-h, offene Silben lang, Schwa und vokalisches r in Endsilben, Betonung auf der ersten Silbe nach Vorsilben, Zahlen bis 999, Pausen an Satzzeichen). |
| `Speech` | Die Spur (Phase 17 auch gesungen: `Speech.sung` mit gehaltenen Vokalen und Atem vor jeder Zeile): Laute mit Zeiten, Viseme je Zeitpunkt mit Übergängen, Lippenschluss bei p/b/m, Unterlippe bei f/v/w, Vorausrundung vor o/u, Betonung; Atem vor Sätzen, Phrasenenden, „spricht gerade“. |
| `Audio` | WAV lesen (PCM 8/16/24 Bit), je 10 ms Pegel, Nulldurchgänge, hohe Frequenzen, Stimmhaftigkeit, F1/F2 (LPC 12); Spur aus dem Ton allein oder Text an den Ton angelegt (Segment-DP: Kosten je Lautart plus Abweichung von der erwarteten Dauer, Pausen im Satz dürfen ausfallen). |

## Benutzung

```java
Speech s = Speech.fromText("Oben im Fischerboot bin ich frei.", 1.0);
Map<String, Double> mund = s.forms(t);          // je Bild: visem.viseme_… → Gewicht
behavior.speech = mund;                          // com.dan.avatar.Behavior mischt sie in die Gesichtsformen
behavior.talking = s.talking(t);
// Atem: bei s.breaths behavior.inhale(), an s.phraseEnds behavior.blinkSoon(0.15)

Audio a = Audio.read(new File("satz.wav"));
Speech mitTon = a.speech("Oben im Fischerboot bin ich frei.");   // Text an den Ton angelegt
Speech nurTon = a.speech();                                      // ohne Text: Mundbilder aus dem Ton
```

Im Studio: Block SPRECHEN (Text sprechen, „WAV …“ mit gleichnamiger .txt daneben; der Ton läuft mit und gibt die Zeit
vor). Statusbild und Video mit Ton: `ant statusbild` (eine eigene Aufnahme in `daten/sprache`, WAV + TXT, geht der
Sprachausgabe `hedda_…` vor). In Lighthouse spricht der Wärter `sage` mit Mund (stumm).

Im Avatar führt die Szene die Spur: `Actor.Host.say(text)` gibt die Dauer zurück, `sage` in einem Regiebuch bewegt so
den Mund (Studio: `StudioScene.Figure.say`).

## Grenzen

- Regeln statt Wörterbuch: Fremdwörter, Abkürzungen und Wörter mit falscher Betonung (gestern, Familie) sehen etwas
  anders aus, als sie klingen. Die Mundbilder bleiben meist richtig, die Dauern sind Schätzungen [G].
- Aus dem Ton allein lassen sich p/t/k und n/l nicht unterscheiden (alle Verschlüsse sehen wie t aus, Nasale wie n);
  mit Text kommen die Mundbilder aus dem Text, nur die Zeiten aus dem Ton.
- Kein Sprechen nach Text als Ton (keine Sprachausgabe); der Text bewegt den Mund stumm, zum Ton braucht es eine Aufnahme.
