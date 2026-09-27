# RedstonePlus

Forge-Mod für **Minecraft Java 1.21.1** (Forge 52.1.14, Java 21) mit neuen Redstone-Blöcken und -Items.

**Benötigt [TerraBlender](https://modrinth.com/mod/terrablender) (Forge 1.21.1)** für die neuen Biome.

**Download:** [download/RedstonePlus-1.21.1-1.6.0.jar](download/RedstonePlus-1.21.1-1.6.0.jar) (auf der Seite dann rechts auf den Download-Pfeil).

Die fertige Datei liegt nach dem Bauen unter `build/libs/RedstonePlus-1.21.1-1.6.0.jar` und kommt in den
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

Ein Fahrzeug zum Reinsetzen, zwei Blöcke groß. Aufstellen mit dem Bohrer-Item, **Rechtsklick** zum Einsteigen.
**W** fährt vorwärts und bohrt dabei einen **5x5-Tunnel**, **S** fährt rückwärts, gelenkt wird mit der Blickrichtung.
Nach unten schauen bohrt schräg nach unten, nach oben schauen bohrt nach oben. Abgebaute Blöcke droppen normal.
**Schleichen** zum Aussteigen, **schlagen** hebt den Bohrer wieder auf.

## Erze, Rüstung, Biome und Mobs (neu in 1.6.0)

**6 neue Erze** mit Rohstoff, Speicherblöcken, 5 Werkzeugen und kompletter Rüstung:
Rubin, Saphir, Titan, Kobalt, Mithril (tief unten) und Voidium (im End). Dazu Werkzeuge und Rüstung aus Uran.

**3 neue Overworld-Biome:** Redstone-Felder (rotes Gras, Redstone-Brocken, viel Rubin), Kristallwald
(türkis, Amethyst-Kristalle, viel Saphir), Titan-Hochland (graue Hügel, Titan-Brocken).
**2 neue End-Biome:** Leerenwüste (Boden aus Leerenstein, Leerenkristalle) und Kristalltürme (Kristallsäulen).

**8 neue Mobs** mit Spawn-Eiern: Uran-Zombie (vergiftet, brennt nicht), Redstone-Creeper (doppelte Explosion),
Kristallspinne (verlangsamt), Magmaskelett (Feuerpfeile), Rubinschleim (droppt Rubine), Leeren-Enderman
(greift sofort an), Glutschwein und Redstone-Golem.

Außerdem Deko-Blöcke (Leerenstein, Rubin-/Saphir-/Kobaltziegel, Titanplatten, Leerenkristall),
Mob-Drops (Leerenperle, Kristallseide, Magmaknochen, geladenes Schwarzpulver, Uranfleisch),
Glutfleisch sowie Rubin- und Mithrilapfel.

## Redstone-Reich (neu)

Eine eigene Dimension. Das **Reichstor** (Craften: Eisen, Redstone-Blöcke, Redstone-Schaltkreis) braucht ein
Redstone-Signal, dann Rechtsklick. Beim ersten Besuch gibt es einen **Feldführer** und eine Ankunftsplattform
mit Rückkehr-Tor, Starterkiste und 10 funktionierenden **Beispielschaltungen** aus den Blöcken dieser Mod
(Taktgeber, UND-Gatter, T-Flipflop, Zähler, Sequenzer, Funk, Detektor, Laserzaun, Reichsfalle, Auslöseschiene),
jede mit Schild, das sie erklärt.

| Biom | Kreatur | Auslöser | Reaktion | Gegenmittel |
|---|---|---|---|---|
| Kolbenkarst | Karst-Koloss | Druckplatte | Quetschgang (Zermalmer) | Kolbenstrebe |
| Weichenebene | Weichenkriecher | Auslöseschiene | Umleitung in Stachelgrube (Gefahrenweiche) | Impulsinjektor |
| Resonanzhöhlen (unter Tage) | Glockenpirscher | Sculk-Sensor | Abriegelung (Sperrgitter) | Köderleuchtfeuer |
| Schleusengärten | Schleusen-Kettenkiefer | Stolperdraht auf der Brücke | Flutwelle (Flutschleuse) | Verstärkerschlüssel |
| Brennofen-Öde | Brennofen-Rohling | Druckplatte | Feuerkugel-Salve (Brennofen-Geschütz) | Signalstörer |
| Stolperdraht-Dickicht | Spulenweber | Stolperdraht | Pfeilsalve (Salvenwerfer) | Isolierte Zange |

Die Fallen sind normale Redstone-Bauteile: jedes Signal löst sie aus, angrenzende Fallen derselben Art folgen
nacheinander. Dazu 8 **Maschinengebundene** (Leckende Zelle, Zünderhülle, Stolperdraht-Brut, Ofengebundener,
Lebender Kondensator, Relais-Schreiter, Blasebalg-Keiler, Fleischpresse) als eigene Reichs-Kreaturen; die
bisherigen Mobs bleiben unverändert. Alle 14 Kreaturen haben eigene Modelle, Animationen und Geräusche.

**Kreaturen bearbeiten:** Modelle und Animationen liegen als Blender-Dateien in `tools/blender/NAME.blend`
(Teile = Empties, Clips `idle`/`walk`/`attack`/`ability` zwischen Timeline-Markern). Nach dem Bearbeiten:
`blender -b -P tools/blender/export_mobs.py` schreibt `assets/redstoneplus/realm_models/NAME.json`.
Neu aufbauen aus `tools/realm_mobs.py`: `python tools/gen_realm_mobs.py`, dann
`blender -b -P tools/blender/build_mobs.py -- --rebuild NAME`. Texte, Blöcke und Weltgenerierung:
`python tools/gen_realm.py`, Geräusche: `python tools/gen_realm_sounds.py`.

## Handbuch im Spiel

Das **RedstonePlus-Handbuch** (Kreativ-Tab, oder Buch + Redstone craften; jeder Spieler bekommt es beim ersten Betreten)
erklärt jedes Item, jeden Block, jede Kreatur und jedes Biom. Die Seiten entstehen beim Öffnen aus den registrierten
Inhalten und ihren `.desc`-Texten, neue Inhalte erscheinen also von selbst. Nach jeder Änderung
`python tools/gen_guide.py` ausführen: es schreibt die Kapiteltexte und meldet alles, was noch keine Beschreibung hat.

## Bauen

```bash
./gradlew build
```

Texturen und JSON-Dateien werden von `tools/gen_textures.js` und `tools/gen_data.js` erzeugt
(`node tools/gen_textures.js && node tools/gen_data.js && python tools/gen_content.py`).
`gen_content.py` färbt dafür Vanilla-Texturen aus dem Minecraft-Jar im Gradle-Cache um.

## Lizenz

RedstonePlus steht unter der [Creative Commons Namensnennung - Keine Bearbeitungen 4.0 (CC BY-ND 4.0)](LICENSE).
Du darfst die Mod nutzen und weitergeben (auch in Modpacks), solange der Autor genannt wird.
Veränderte Versionen dürfen nicht verbreitet werden.

