# RedstonePlus

Forge-Mod für **Minecraft Java 1.21.1** (Forge 52.1.14, Java 21) mit neuen Redstone-Blöcken und -Items.

**Download:** [download/RedstonePlus-1.21.1-1.5.0.jar](download/RedstonePlus-1.21.1-1.5.0.jar) (auf der Seite dann rechts auf den Download-Pfeil).

Die fertige Datei liegt nach dem Bauen unter `build/libs/RedstonePlus-1.21.1-1.5.0.jar` und kommt in den
`mods`-Ordner. Alle Items sind im eigenen Kreativ-Tab **RedstonePlus**, jedes Item erklärt seine Funktion
im Tooltip (Deutsch und Englisch).

## Die gewünschten Items

| Item | Funktion |
|---|---|
| **TnTer** | Jedes Redstone-Signal (egal welches, auch 1-Tick-Impulse) setzt gezündetes TNT vor den Block. Kein TNT nötig. |
| **FreezeTnter** | Wie der TnTer, aber das TNT ist eingefroren: explodiert nie, keine Schwerkraft, Explosionen schieben es nicht. Nur Kolben und Wasserströmung bewegen es. |
| **Aktivator-Netz** | Eingefrorenes TNT, das durch das Netz geht, wird zu normalem TNT (80 Ticks Zündzeit). Alles andere läuft einfach durch. |
| **Entity-Teleporter** | Saugt jedes Entity außer Spielern ein, das sich im Block über ihm befindet, und speichert es exakt so, wie es ist (Zündzeit, Leben, Items, Bewegung, Passagiere). Rechtsklick zeigt die Anzahl, Komparator liest sie aus. |
| **Fernzünder** | Nicht stapelbar. Schleichen + Rechtsklick auf einen Entity-Teleporter verbindet ihn (beliebig viele Fernzünder pro Teleporter). Benutzen holt die nächste gespeicherte Gruppe (älteste zuerst) und setzt sie 10 Blöcke über den Block, den man anschaut. Alles, was fast gleichzeitig (innerhalb einer halben Sekunde) in den Teleporter kam, ist eine Gruppe und kommt zusammen raus, mit denselben Abständen untereinander (bis 256 Blöcke weit). Danach geht der Fernzünder kaputt. Ist der Teleporter leer, passiert nichts und der Fernzünder bleibt ganz. |
| **Nuclear Repeater** | Signal von hinten, Ausgang vorne. Der Redstone-Staub am Ausgang bleibt bis zu 200 Staub-Blöcke weit auf voller Stärke 15. |
| **ArrowShooter** | Nimmt nur Pfeile (auch Spektral- und Trankpfeile) und Uranium Shards. Jedes Redstone-Signal schießt 6 Pfeile mit **100 Blöcken pro Tick** (2000 Blöcke pro Sekunde, wirkt wie Teleportieren) und kostet 1 Uranium Shard. |
| **Uranerz / Uranium Shard** | Uranerz kommt im Endstein im End vor (Hauptinsel und äußere Inseln), braucht mindestens eine Eisenspitzhacke und droppt 1-3 Uranium Shards (Glück wirkt). |

Hinweis zum ArrowShooter: Minecraft läuft mit 20 Ticks pro Sekunde, einen halben Tick gibt es im Spiel nicht.
Die Mod schießt deshalb 2 Pfeile pro Tick und schiebt den jeweils ersten um eine halbe Tick-Flugstrecke nach
vorne. In der Luft liegen die 6 Pfeile damit genau so hintereinander, als wären sie im Halb-Tick-Abstand
abgefeuert worden.

## 53 weitere Items

**Material:** Angereichertes Uran, Redstone-Schaltkreis, Uranblock (leuchtende Redstone-Quelle).

**Logik (flach wie ein Repeater, Ausgang zeigt in Blickrichtung beim Setzen):**
UND-, ODER-, XOR-, NICHT-, NAND-, NOR-, XNOR-Gatter (Eingänge links/rechts, NICHT von hinten),
Signalverstärker, T-Flipflop, RS-Speicher, Pulsbegrenzer, Pulsverlängerer (1-20 s), Verzögerer (1-20 Redstone-Ticks),
Zufallsgenerator, Zähler (0-15), Sequenzer, Redstone-Kreuzung, Taktgeber (1-20 Redstone-Ticks).
Einstellbare Blöcke per Rechtsklick, Schleichen + Rechtsklick zählt rückwärts.

**Quellen & Funk:** Variable Signalquelle (1-15), Verstärkter Redstoneblock (explosionsfest), Funksender,
Funkempfänger (16 Kanäle, auch über Dimensionen), Redstone-Fernbedienung.

**Sensoren:** Spielerdetektor, Monsterdetektor, Wettersensor, Nachtsensor, Laserschranke (32 Blöcke).

**Maschinen:** Blockbrecher, Blockplatzierer, Blitzbeschwörer, Katapultplatte, Ventilator, Item-Magnet,
Feuerzünder, Stachelblock, Phantomblock, Förderband, TNT-Kanone, Uran-Atombombe, Feuerballwerfer,
Ernteautomat, Alarmsirene, Antigravitationsfeld.

**Anzeige & Fallen:** Sofortlampe, Invertierte Lampe, Signalanzeige (zeigt 0-15 als Zahl), Landmine.

**Werkzeuge:** Redstone-Schraubenschlüssel (dreht Blöcke), Multimeter (zeigt Redstone-Werte).

## 20 neue Items

**Logik:** Flankendetektor, Analog-Inverter (15 minus Eingang), Signal-Addierer, Signal-Subtrahierer.
**Sensoren:** Itemdetektor, Lichtsensor, Blockdetektor, Entity-Zähler.
**Maschinen:** Eismaschine, Wasserpumpe, Feuerwerkswerfer, Schneeballgeschütz, Ambosswerfer, Cluster-TnTer (5 TNT),
TNT-Regen (9 TNT aus 20 Blöcken Höhe), Blocktauscher.
**Platten & Effekte:** Heilplatte, Tempoplatte, Rauchgenerator.
**Werkzeug:** TNT-Aktivator (macht alles eingefrorene TNT in 32 Blöcken scharf).

Jeder Block hat eigene Texturen und eine eigene Form (Kanonen mit Rohr, Sensoren mit Kuppel, Sender mit Antenne,
Platten, Stacheln, Magnet, Sirene, Atombombe mit Finnen usw.).

## SuperPiston

Normale und klebrige Variante. Sieht aus wie der Vanilla-Kolben, beim klebrigen ist der Schleim blau.
Schiebt bis zu **50 Blöcke** (Vanilla: 12). Rechtsklick öffnet einen **Regler 1-13**: so weit fährt der Arm aus.
Der Arm fährt Block für Block mit der normalen Kolben-Animation raus und schiebt dabei alles mit.
Der klebrige zieht beim Einfahren wie Vanilla den angeklebten Block (bzw. Schleim-Konstruktionen) mit zurück.

## Bohrer (neu in 1.5.0)

Ein Fahrzeug zum Reinsetzen. Aufstellen mit dem Bohrer-Item, **Rechtsklick** zum Einsteigen.
**W** fährt vorwärts und bohrt dabei einen **3x3-Tunnel**, **S** fährt rückwärts, gelenkt wird mit der Blickrichtung.
Nach unten schauen bohrt schräg nach unten, nach oben schauen bohrt nach oben. Abgebaute Blöcke droppen normal.
**Schleichen** zum Aussteigen, **schlagen** hebt den Bohrer wieder auf.

## Bauen

```bash
./gradlew build
```

Texturen und JSON-Dateien werden von `tools/gen_textures.js` und `tools/gen_data.js` erzeugt
(`node tools/gen_textures.js && node tools/gen_data.js`).

## Lizenz

RedstonePlus steht unter der [Creative Commons Namensnennung - Keine Bearbeitungen 4.0 (CC BY-ND 4.0)](LICENSE).
Du darfst die Mod nutzen und weitergeben (auch in Modpacks), solange der Autor genannt wird.
Veränderte Versionen dürfen nicht verbreitet werden.

