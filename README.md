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

## Das Raster, Lichträder und die Versiegelte Weite (neu)

**Das Raster:** Leuchtende **Lichtbahnen** ziehen sich alle 256 Blöcke von Nord nach Süd und von Ost nach West durch das
Reich. An jeder Kreuzung steht ein **Knoten**: ein Raddepot (überdachte Halle mit Kreisverkehr, Leuchtfeuer und Vorratskiste),
ein Kreuzungsturm (Turm auf vier Beinen über der Kreuzung), ein Bahnwerk (ummauerter Hof mit Halle, Kran und Bahnwerkern) oder
ein Glockentor (Bögen mit Glocken über den Einfahrten). Alle 48 Blöcke steht ein Leuchtmast an der Bahn; über flaches Wasser
führt ein Damm. Viele Bahnen sind gebrochen.

**Bahnwerker** (eigenes Blender-Modell) sind die Straßenbauer des Rasters: Sie laufen zu den Bruchstellen und legen Kachel um
Kachel, bis die Bahn wieder ganz ist, und sie beginnen **neue Abzweige** im rechten Winkel, die an einer Haltestelle mit
Leuchtfeuer enden. Das Raster wächst also, während man spielt. Mit der Spielregel `mobGriefing` lässt sich das abschalten.

**Lichtrad:** Beim ersten Betreten des Reichs materialisiert neben dem Spieler ein **Lichtrad** (Blender-Modell, leuchtende
Felgen, Lichtwand dahinter), dazu der **Lichtrad-Schlüssel**. Rechtsklick zum Aufsteigen. Abseits der Bahnen fährt es wie ein
schneller Wagen (W/S, lenkt in Blickrichtung). Auf einer Lichtbahn **rastet es ein**: es folgt der Bahn von selbst, bleibt
mittig, fährt bis 28 Blöcke/s (**Springen halten**: Schub bis 44 Blöcke/s) und gleitet mit seinem Gravlift über Stufen bis
6 Blöcke. Vor einer Kreuzung **A oder D tippen** zum Abbiegen; ohne Eingabe geht es geradeaus, an einer reinen Abzweigung in
Blickrichtung. **S** bremst, S im Stand wendet. Vor dem Schlag der Großen Glocke bremst es von selbst und hält still (sonst
wäre die dritte Regel gebrochen). Fehlen die Chunks voraus noch, wartet es.

Das Reich **hält das Rad immer beim Spieler**: Jede Sekunde wird geprüft, ob es existiert, in derselben Welt und in Reichweite
ist; fehlt es (zurückgelassen, aus der Welt gefallen, entladen, kaputt), wird es nach ein paar Sekunden neben dem Spieler neu
gebaut. Es gibt immer nur eins. Der Schlüssel ruft es jederzeit (auch in anderen Dimensionen), Schleichen + Benutzen schaltet die
automatische Rückkehr an/aus, Schleichen + Schlagen auf das eigene Rad stellt es weg. Der Bund überlebt den Tod.

**Anzeige:** Beim Fahren zeigt links oben eine Karte die Bahnen in der Nähe (Norden oben, Raster schwach dahinter, Bahnwerker
bernsteinfarben, andere Räder rot), dazu Tempo, ob das Rad eingerastet ist, die nächste Kreuzung mit ihren Richtungen, die
vorgemerkte Abbiegung und die Glockenwarnung. **M** öffnet die **Rasterkarte** (zoomen mit dem Mausrad, ziehen zum Umsehen).

**Die Versiegelte Weite:** Ein neues Biom auf großen Flächen im Landesinneren, rundum von einer **Mauer aus Quarantäneplatten** mit
Warnlampen umschlossen; wo Bahnen auf die Mauer treffen, sind die Tore versiegelt, außen stehen **Warnschilder** und Dornen.
Drinnen: blutroter Himmel, Nebel, Fäulniskruste, und zwei Kreaturen mit eigenen Blender-Modellen, die die Eintracht nie hielten:
das **Drahtgespenst** (Stelzengänger aus Draht mit Glockenkopf; sein Schrei verdunkelt und zieht heran) und die
**Schlundmaschine** (Ofen auf vier Beinen mit riesigem Maul; springt und beißt). Sie jagen jeden, sie zu bekämpfen bricht keine
Regel, und sie verlassen die Mauern nicht.

**Farben:** Himmel, Wasser, Nebel, Pflanzen und Böden des ganzen Reichs bleiben jetzt bei Rot, Orange und Gelb (keine blauen
Meere oder Himmel mehr, kein Türkis, kein Grün, keine blauen Eisflächen). `python tools/realm_palette.py` prüft das.

**Regeln fertig:** Der Tod setzt die Zählung jetzt verlässlich zurück (die Rückkehr aus dem End nicht), der Balken bleibt nach dem
Tod nicht mehr hängen, Sühne ruft die jagenden Kreaturen zurück, eine Lampe zählt nur einmal (und höchstens eine pro Minute),
der Balken zählt die Sekunden bis zum Glockenschlag herunter, und Lichtbahnen und Quarantänemauern gelten als gebaut.

**Blender ohne Oberfläche:** `pip install bpy` (Python 3.11) genügt; dann laufen `python tools/blender/build_mobs.py -- NAME`
und `python tools/blender/export_mobs.py -- NAME` wie mit `blender -b -P`. Für die Vorschaubilder braucht Linux `libegl1`.

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

