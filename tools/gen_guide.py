"""
Texts, texture, model and recipe of the RedstonePlus Guide (the in-game book that explains the whole mod),
and a check that everything in the mod is explained in it.

The book builds its pages in the game from the registered items, blocks, creatures and biomes, using their
".desc" texts. This script writes the chapter texts and the creature/biome descriptions, then lists anything
that still has no description.

    python tools/gen_guide.py          (run it after every change to the mod; it exits with 1 if texts are missing)
"""
import glob
import io
import json
import os
import sys
import zipfile

from PIL import Image

NS = 'redstoneplus'
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, '..', 'src', 'main', 'resources')
ASSETS = os.path.join(ROOT, 'assets', NS)
DATA = os.path.join(ROOT, 'data', NS)
en, de = {}, {}


def name(key, e, g):
    en[key] = e
    de[key] = g


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write('\n')


# ------------------------------------------------------------------------------------------ the book item
name(f'item.{NS}.guide_book', 'RedstonePlus Guide', 'RedstonePlus-Handbuch')
name(f'item.{NS}.guide_book.desc', 'Right click to read: every item, block, creature and biome of RedstonePlus explained.',
     'Rechtsklick zum Lesen: jedes Item, jeder Block, jede Kreatur und jedes Biom von RedstonePlus erklärt.')
jars = glob.glob(os.path.expanduser('~/.gradle/caches/minecraftforge/forgegradle/mavenizer/caches/minecraft_tasks/1.21.1/client.jar'))
if jars:
    jar = zipfile.ZipFile(jars[0])
    book = Image.open(io.BytesIO(jar.read('assets/minecraft/textures/item/written_book.png'))).convert('RGBA')
    px = book.load()
    for y in range(book.height):
        for x in range(book.width):
            r, g, b, a = px[x, y]
            if a and r > g + 20:  # the cover: make it redstone red with a glowing dust mark
                px[x, y] = (min(255, r + 40), g // 3, b // 3, a)
    for x, y in ((7, 6), (8, 6), (7, 7), (8, 7), (6, 8), (9, 8)):
        px[x, y] = (255, 80, 60, 255)
    os.makedirs(os.path.join(ASSETS, 'textures', 'item'), exist_ok=True)
    book.save(os.path.join(ASSETS, 'textures', 'item', 'guide_book.png'))
write(os.path.join(ASSETS, 'models', 'item', 'guide_book.json'),
      {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{NS}:item/guide_book'}})
write(os.path.join(DATA, 'recipe', 'guide_book.json'), {
    'type': 'minecraft:crafting_shapeless', 'category': 'misc',
    'ingredients': [{'item': 'minecraft:book'}, {'item': 'minecraft:redstone'}],
    'result': {'id': f'{NS}:guide_book', 'count': 1}})

# ------------------------------------------------------------------------------------------ cover and chapters
name(f'guide.{NS}.title', 'RedstonePlus Guide', 'RedstonePlus-Handbuch')
name(f'guide.{NS}.cover',
     'Everything this mod adds, explained. The next page lists the chapters: click one to jump there.\n\n'
     'All blocks and items are in the RedstonePlus and Redstone Realm creative tabs. Their tooltips say the same as this book.',
     'Alles, was diese Mod hinzufügt, erklärt. Die nächste Seite zeigt die Kapitel: anklicken, um hinzuspringen.\n\n'
     'Alle Blöcke und Items sind in den Kreativ-Tabs RedstonePlus und Redstone-Reich. Ihre Tooltips sagen dasselbe wie dieses Buch.')
name(f'guide.{NS}.contents', 'Contents', 'Inhalt')

CHAPTERS = {
    'core': ('The core items', 'Die Kern-Items',
             'The items RedstonePlus started with: TNT machines, frozen TNT, the Entity Teleporter with its Remote Detonator, the Nuclear Repeater, the ArrowShooter and uranium.',
             'Die Items, mit denen RedstonePlus begann: TNT-Maschinen, eingefrorenes TNT, der Entity-Teleporter mit Fernzünder, der Nuclear Repeater, der ArrowShooter und Uran.'),
    'logic': ('Logic gates', 'Logik-Gatter',
              'Flat like a repeater. They output towards the way you looked when placing them. Two-input gates read left and right, the rest read the back. Right click adjustable ones; sneak to count down.',
              'Flach wie ein Repeater. Ausgang in Blickrichtung beim Setzen. Zwei-Eingang-Gatter lesen links und rechts, die anderen hinten. Einstellbare per Rechtsklick, Schleichen zählt rückwärts.'),
    'sources': ('Sources and wireless', 'Quellen und Funk',
                'Blocks that give a signal, and wireless: a transmitter powers every receiver on the same channel (16 channels, even across dimensions). Right click to change the channel.',
                'Blöcke, die ein Signal geben, und Funk: ein Sender schaltet alle Empfänger auf demselben Kanal (16 Kanäle, auch über Dimensionen). Rechtsklick wechselt den Kanal.'),
    'sensors': ('Sensors', 'Sensoren',
                'Sensors watch something and send a redstone signal to every side while it is true. Combine them with gates and lamps.',
                'Sensoren beobachten etwas und senden ein Redstone-Signal in alle Richtungen, solange es zutrifft. Kombiniere sie mit Gattern und Lampen.'),
    'machines': ('Machines', 'Maschinen',
                 'Machines act on a redstone signal. Most face the way they point when placed (like a dispenser).',
                 'Maschinen reagieren auf ein Redstone-Signal. Die meisten zeigen beim Setzen in Blickrichtung (wie ein Werfer).'),
    'display': ('Lamps, displays and traps', 'Lampen, Anzeigen und Fallen', 'Show what your circuits are doing, or surprise whoever walks by.',
                'Zeige, was deine Schaltungen tun, oder überrasche, wer vorbeikommt.'),
    'tools': ('Tools, Super Piston and Drill', 'Werkzeuge, Superkolben und Bohrer',
              'Helpers for building and testing circuits, the Super Piston that pushes up to 50 blocks, and the Drill you can ride through rock.',
              'Helfer zum Bauen und Testen, der Superkolben, der bis zu 50 Blöcke schiebt, und der Bohrer, mit dem du durch Fels fährst.'),
    'ores': ('Ores, tools and armor', 'Erze, Werkzeuge und Rüstung',
             'Six ores: Ruby, Sapphire, Titanium, Cobalt, Mythril (deep down) and Voidium (in the End). Each makes storage blocks, five tools and full armor. Uranium makes tools and armor too.',
             'Sechs Erze: Rubin, Saphir, Titan, Kobalt, Mithril (tief unten) und Voidium (im End). Jedes ergibt Speicherblöcke, fünf Werkzeuge und Rüstung. Aus Uran gibt es ebenfalls Werkzeuge und Rüstung.'),
    'extras': ('Decoration, drops and food', 'Deko, Beute und Essen', 'Building blocks, what the new mobs drop, and food.',
               'Baublöcke, was die neuen Mobs fallen lassen, und Essen.'),
    'world': ('Mobs and biomes', 'Mobs und Biome',
              'New creatures of the Overworld and the End, and the biomes they live in. The Overworld biomes need TerraBlender.',
              'Neue Kreaturen der Oberwelt und des End und ihre Biome. Die Oberwelt-Biome brauchen TerraBlender.'),
    'realm': ('The Redstone Realm', 'Das Redstone-Reich',
              'A dimension of machines. Build a frame of redstone blocks like a Nether portal and light it with flint and steel. Its creatures leave you alone unless you break the rules of the realm (see the field guide); a bar at the top counts them. It is the ruin of a world that once ran on redstone: glowing veins run through every biome, old roads cross it with lamps that still burn where power remains, and its great buildings (generator halls, relay spires, circuit temples) stand either humming or in ruins. The Grid of glowing lightlines crosses it all: ride it on the light cycle the realm gives you (M opens the Grid Map). The pages after this are the realm\'s own field guide, then its blocks, tools, creatures and biomes.',
              'Eine Dimension der Maschinen. Baue einen Rahmen aus Redstone-Blöcken wie ein Netherportal und entzünde ihn mit Feuerzeug. Seine Kreaturen lassen dich in Ruhe, solange du die Regeln des Reichs nicht brichst (siehe Feldführer); ein Balken oben zählt sie. Es ist die Ruine einer Welt, die einst mit Redstone lief: Leuchtende Adern ziehen durch jedes Biom, alte Straßen durchqueren es, deren Lampen noch brennen, wo Strom übrig ist, und seine großen Bauten (Generatorhallen, Relaistürme, Schaltkreistempel) stehen summend da oder liegen in Trümmern. Das Raster aus leuchtenden Lichtbahnen durchzieht alles: fahr es mit dem Lichtrad, das dir das Reich gibt (M öffnet die Rasterkarte). Danach folgen der Feldführer des Reichs, seine Blöcke, Werkzeuge, Kreaturen und Biome.'),
    'other': ('More', 'Weiteres', 'Everything else.', 'Alles Weitere.'),
}
for cid, (e, g, ie, ig) in CHAPTERS.items():
    name(f'guide.{NS}.chapter.{cid}', e, g)
    name(f'guide.{NS}.chapter.{cid}.intro', ie, ig)

# ------------------------------------------------------------------------------------------ creatures
MOBS = {
    'uranium_zombie': ('Glowing zombie that never burns in daylight. Its hits poison. Drops Uranium Flesh.',
                       'Leuchtender Zombie, der im Tageslicht nicht brennt. Seine Schläge vergiften. Lässt Uranfleisch fallen.'),
    'redstone_creeper': ('Red creeper with twice the blast. Drops Charged Gunpowder. Lives in the Redstone Fields.',
                         'Roter Creeper mit doppelter Explosion. Lässt geladenes Schwarzpulver fallen. Lebt in den Redstone-Feldern.'),
    'crystal_spider': ('Fast cyan spider that slows what it bites. Drops Crystal Silk.', 'Schnelle türkise Spinne, deren Biss verlangsamt. Lässt Kristallseide fallen.'),
    'magma_skeleton': ('Fire proof skeleton whose arrows set you on fire. Drops Magma Bones.', 'Feuerfestes Skelett, dessen Pfeile anzünden. Lässt Magmaknochen fallen.'),
    'ruby_slime': ('Red slime that sometimes drops rubies.', 'Roter Schleim, der manchmal Rubine fallen lässt.'),
    'void_enderman': ('Purple enderman of the new End biomes: stronger, and it attacks on sight. Drops Void Pearls.',
                      'Lila Enderman der neuen End-Biome: stärker und greift sofort an. Lässt Leerenperlen fallen.'),
    'ember_pig': ('Fire proof orange pig. Drops Ember Pork.', 'Feuerfestes oranges Schwein. Lässt Glutfleisch fallen.'),
    'redstone_golem': ('Red iron golem that guards the Redstone Fields. Drops redstone and rubies.', 'Roter Eisengolem, der die Redstone-Felder bewacht. Lässt Redstone und Rubine fallen.'),
    'karst_colossus': ('Piston Karst. Walking stone engine: its punches launch you, and its ground slam sets off every realm trap nearby. Left alone it walks to crushers and stamps them into motion.',
                       'Kolbenkarst. Wandelnde Steinmaschine: seine Schläge schleudern, sein Stampfen löst alle Reichsfallen in der Nähe aus. In Ruhe gelassen stampft es Zermalmer in Gang.'),
    'switchback_crawler': ('Switchyard Flats. Rail centipede: twice as fast on rails, charges in straight lines and drags minecarts along. It patrols the tracks, screeching along the rails.',
                           'Weichenebene. Schienen-Tausendfüßer: doppelt so schnell auf Schienen, stürmt geradeaus und zieht Loren mit. Es patrouilliert kreischend die Gleise entlang.'),
    'bell_stalker': ('Resonance Hollows. Blind: it hunts by footsteps, so sneak. Its toll blinds you and trips sculk sensors. A Decoy Beacon lures it away. It seeks out bells and rings them.',
                     'Resonanzhöhlen. Blind: jagt nach Schritten, also schleichen. Sein Läuten blendet und weckt Sculk-Sensoren. Ein Köderleuchtfeuer lockt es weg. Es sucht Glocken und läutet sie.'),
    'sluice_chainjaw': ('Sluice Gardens. Chained copper gator: its bite drags you in, in water it whips up a whirlpool. It works the floodgates, making them surge.',
                        'Schleusengärten. Kupfer-Echse in Ketten: ihr Biss zieht dich heran, im Wasser macht sie einen Strudel. Sie bedient die Fluttore und lässt sie schwallen.'),
    'kiln_brute': ('Kiln Barrens. Walking furnace: burning punches, fire-charge volleys (a Signal Jammer stops them), and it smelts items dropped near it. It stokes campfires and magma back to life.',
                   'Brennofen-Öde. Wandelnder Ofen: brennende Schläge, Feuerkugel-Salven (ein Signalstörer stoppt sie), und er schmilzt Items neben sich. Er facht Lagerfeuer und Magma wieder an.'),
    'spool_weaver': ('Tripwire Briar. Wall-climbing spider that lashes wire to root you. Insulated Cutters hurt it badly; shear it for string. It strings webs around the lamp posts.',
                     'Stolperdraht-Dickicht. Kletternde Spinne, die dich mit Draht fesselt. Die Isolierte Zange schadet ihr stark; scheren gibt Faden. Sie spinnt Netze um die Lampenmasten.'),
    'leaking_cell': ('Zombie in a leaking uranium cage. Poisons, spills toxic puddles when hurt, and the spill makes crops grow. It drinks from exposed redstone veins to heal.',
                     'Zombie in einem lecken Urankäfig. Vergiftet, verschüttet giftige Pfützen, wenn er getroffen wird, und die Pfütze lässt Pflanzen wachsen. Er trinkt aus offenen Redstone-Adern, um zu heilen.'),
    'detonator_husk': ('Creeper wired to a detonator: when one is about to blow, every Husk nearby starts its fuse, and the blast sets off realm traps. It feeds on the power of redstone blocks.',
                       'Creeper mit Zünder: kurz vor der Explosion zünden alle Hüllen in der Nähe mit, und die Explosion löst Reichsfallen aus. Sie zehrt vom Strom der Redstone-Blöcke.'),
    'tripwire_brood': ('Spider with a crystal egg sack. Its bite roots you in place; when it dies, cave spiders burst out. It webs up tripwires and thorn thickets.',
                       'Spinne mit Kristall-Eiersack. Ihr Biss hält dich fest; stirbt sie, platzen Höhlenspinnen heraus. Sie spinnt Stolperdrähte und Dornen ein.'),
    'kilnbound': ('Skeleton in a furnace frame. Fire proof, burning arrows, and every shot fires the Kiln Turrets around it. It tends the Kiln Turrets and the fires near them.',
                  'Skelett im Ofengestell. Feuerfest, brennende Pfeile, und jeder Schuss zündet die Brennofen-Geschütze um es herum. Es pflegt Brennofen-Geschütze und die Feuer dabei.'),
    'living_capacitor': ('Slime in a capacitor frame. Every hit it takes discharges a shock into everything close and sets off traps. It arcs power into dead lamps nearby and brings them back on.',
                         'Schleim im Kondensatorgestell. Jeder Treffer entlädt einen Schock in alles Nahe und löst Fallen aus. Es schlägt Strom in tote Lampen und bringt sie wieder zum Leuchten.'),
    'relay_strider': ('Enderman strung with relay cables: hunts rule-breakers on sight, and its hits can relay you a few blocks away. It climbs to lightning rods and charges the air.',
                      'Enderman voller Relaiskabel: jagt Regelbrecher sofort, und seine Schläge können dich ein Stück wegversetzen. Er steigt zu Blitzableitern und lädt die Luft auf.'),
    'bellows_hog': ('Pig with bellows: when hurt it blasts hot air. Feed it coal and for a minute it smelts items dropped around it. It blows campfires back into flame.',
                    'Schwein mit Blasebalg: getroffen bläst es heiße Luft. Mit Kohle gefüttert schmilzt es eine Minute lang Items um sich herum. Es bläst Lagerfeuer wieder an.'),
    'flesh_press': ('Golem made of a flesh press. Crushing blows. Give it a redstone block and it becomes your guardian. It presses the rubble of ruins back into bricks.',
                    'Golem aus einer Fleischpresse. Zermalmende Schläge. Gib ihr einen Redstone-Block, dann beschützt sie dich. Sie presst den Schutt von Ruinen wieder zu Ziegeln.'),
    'spark_mite': ('Everywhere. Copper beetle that grazes on glowing redstone veins in herds and flees from jackals and players.',
                   'Überall. Kupferkäfer, der in Herden an leuchtenden Redstone-Adern grast und vor Schakalen und Spielern flieht.'),
    'lamp_moth': ('Everywhere. Foil moth drawn to anything still giving light: flocks circle the lit lamps of the old roads and ruins.',
                  'Überall. Folienmotte, angezogen von allem, was noch leuchtet: Schwärme umkreisen die brennenden Lampen der alten Straßen und Ruinen.'),
    'scrap_jackal': ('Everywhere. Pack hunter of scrap metal: it chases Spark Mites and Lamp Moths and digs through ruin rubble for iron, redstone and cogs. It leaves you alone unless you hurt the pack.',
                     'Überall. Rudeljäger aus Schrott: jagt Funkenmilben und Lampenmotten und gräbt im Ruinenschutt nach Eisen, Redstone und Zahnrädern. Lässt dich in Ruhe, solange du das Rudel nicht angreifst.'),
    'light_cycle': ('The realm\'s racing cycle, given to every visitor and kept at their side. Rides the Grid\'s lightlines locked on, at up to 44 blocks a second.',
                    'Das Rennrad des Reichs, jedem Besucher gegeben und an seiner Seite gehalten. Fährt eingerastet auf den Lichtbahnen des Rasters, mit bis zu 44 Blöcken pro Sekunde.'),
    'trackwright': ('Everywhere, and at the trackworks. The Grid\'s road builder: it mends broken lightlines and lays new branch lines, each ending at a waystop beacon. Keeps the Concordance; leave its lines whole.',
                    'Überall, und in den Bahnwerken. Der Straßenbauer des Rasters: flickt gebrochene Lichtbahnen und legt neue Abzweige, die an einem Haltestellen-Leuchtfeuer enden. Hält die Eintracht; lass seine Bahnen ganz.'),
    'wirewraith': ('The Sealed Reach. A stilt-walker of tangled wire with a hollow bell for a head. Its scream darkens and slows everyone near and drags its prey closer. Outside the Concordance: it hunts everyone.',
                   'Die Versiegelte Weite. Ein Stelzengänger aus verknäultem Draht mit einer hohlen Glocke als Kopf. Sein Schrei verdunkelt und verlangsamt alle in der Nähe und zieht seine Beute heran. Außerhalb der Eintracht: jagt jeden.'),
    'maw_engine': ('The Sealed Reach. A furnace on four legs that is mostly mouth. It gapes, lunges, and its burning bite holds you fast. Outside the Concordance: it hunts everyone.',
                   'Die Versiegelte Weite. Ein Ofen auf vier Beinen, der vor allem Maul ist. Er reißt es auf, springt, und sein glühender Biss hält dich fest. Außerhalb der Eintracht: jagt jeden.'),
    'echo_force': ('Boss. The Pistonarch, the Echo of Force, at the Press Throne. Its rams hurl you away; its Slam throws everyone near off their feet and sets off every trap around. At half strength it calls Detonator Husks. Drops the Core of Force.',
                   'Boss. Der Kolbenfürst, das Echo der Kraft, am Pressenthron. Seine Rammen schleudern dich fort; sein Stampfer wirft alle in der Nähe um und löst jede Falle ringsum aus. Bei halber Kraft ruft er Zünderhüllen. Lässt den Kern der Kraft fallen.'),
    'echo_signal': ('Boss. The Current, the Echo of Signal, over the Switchboard. It dashes straight through you and calls lightning down around you. At half strength it calls Spark Mites and a Relay Strider. Drops the Core of Signal.',
                    'Boss. Der Strom, das Echo des Signals, über der Schaltwarte. Er rast mitten durch dich hindurch und ruft Blitze um dich herab. Bei halber Kraft ruft er Funkenmilben und einen Relais-Schreiter. Lässt den Kern des Signals fallen.'),
    'echo_resonance': ('Boss. The Choir, the Echo of Resonance, above the Belfry Hollow. Its sound strikes through armour; its Chorus lifts everyone near and takes their sight. At half strength it calls Bell Stalkers. Drops the Core of Resonance.',
                       'Boss. Der Chor, das Echo der Resonanz, über der Glockenmulde. Sein Klang trifft durch jede Rüstung; sein Choral hebt alle in der Nähe an und nimmt ihnen die Sicht. Bei halber Kraft ruft er Glockenpirscher. Lässt den Kern der Resonanz fallen.'),
    'echo_heat': ('Boss. The Kilnheart, the Echo of Heat, over the Furnace Crown. Volleys of fire, and its Bloom sets a ring of flame and burns everyone close. At half strength it calls the Kilnbound. Drops the Core of Heat.',
                  'Boss. Das Ofenherz, das Echo der Hitze, über der Ofenkrone. Feuersalven, und seine Blüte legt einen Flammenring und verbrennt alle in der Nähe. Bei halber Kraft ruft es Ofengebundene. Lässt den Kern der Hitze fallen.'),
    'echo_flow': ('Boss. The Sluicemother, the Echo of Flow, in the Sluice Basin. It walks through molten redstone, drags everyone towards it, and its Floodtide pours molten redstone around it. At half strength it calls Chainjaws. Drops the Core of Flow.',
                  'Boss. Die Schleusenmutter, das Echo des Flusses, im Schleusenbecken. Sie watet durch geschmolzenes Redstone, zieht alle zu sich heran, und ihre Sturmflut gießt geschmolzenes Redstone um sie herum. Bei halber Kraft ruft sie Kettenkiefer. Lässt den Kern des Flusses fallen.'),
    'the_overtoll': ('The last fight. The Great Bell come down, called with the Heart of the Five at a Cradle. Its chains lash and drag; when it tolls, everyone who moves is struck. It calls Wirewraiths and Bell Stalkers, and near its end fire rains. Its fall frees the realm.',
                     'Der letzte Kampf. Die Große Glocke, herabgerufen mit dem Herz der Fünf an einer Wiege. Ihre Ketten peitschen und ziehen; wenn sie schlägt, wird jeder getroffen, der sich bewegt. Sie ruft Drahtgespenster und Glockenpirscher, und gegen Ende regnet Feuer. Ihr Fall befreit das Reich.'),
}
for mob, (e, g) in MOBS.items():
    name(f'entity.{NS}.{mob}.desc', e, g)

BIOMES = {
    'redstone_fields': ('Overworld. Red grass and redstone boulders, lots of rubies. Redstone Creepers and Golems.',
                        'Oberwelt. Rotes Gras und Redstone-Brocken, viele Rubine. Redstone-Creeper und -Golems.'),
    'crystal_forest': ('Overworld. Turquoise forest with amethyst crystals and lots of sapphire. Crystal Spiders.',
                       'Oberwelt. Türkiser Wald mit Amethyst-Kristallen und viel Saphir. Kristallspinnen.'),
    'titan_highlands': ('Overworld. Grey hills with titanium rocks and lots of titanium ore.', 'Oberwelt. Graue Hügel mit Titan-Brocken und viel Titanerz.'),
    'void_wastes': ('End. Void Stone ground with void crystals. Void Endermen.', 'End. Boden aus Leerenstein mit Leerenkristallen. Leeren-Endermen.'),
    'crystal_spires': ('End. Towers of void crystal.', 'End. Türme aus Leerenkristall.'),
    'piston_karst': ('Realm. Pale limestone towers. Crusher passages with an AND-gate vault. Karst Colossus.',
                     'Reich. Helle Kalktürme. Quetschgänge mit einem UND-Gatter-Tresor. Karst-Koloss.'),
    'switchyard_flats': ('Realm. Rust and slag crossed by long tracks with powered rails and signal posts. Junctions with a Tripper Rail and a spike pit. Switchback Crawler.',
                         'Reich. Rost und Schlacke, durchzogen von langen Gleisen mit Antriebsschienen und Signalmasten. Weichen mit Auslöseschiene und Stachelgrube. Weichenkriecher.'),
    'resonance_hollows': ('Realm, underground. Crystal caves. Gatehouses with sculk sensors and Lockdown Gates. Bell Stalker.',
                          'Reich, unter Tage. Kristallhöhlen. Torhäuser mit Sculk-Sensoren und Sperrgittern. Glockenpirscher.'),
    'sluice_gardens': ('Realm. Canals and copper on the shore of the Red Sea. Tripwire bridges over floodgates. Sluice Chainjaw.',
                       'Reich. Kanäle und Kupfer am Ufer des Roten Meers. Drahtbrücken über Flutschleusen. Schleusen-Kettenkiefer.'),
    'kiln_barrens': ('Realm. Black rock, sulfur and lava. Bridges between walls of Kiln Turrets. Kiln Brute.',
                     'Reich. Schwarzer Fels, Schwefel und Lava. Brücken zwischen Brennofen-Geschützen. Brennofen-Rohling.'),
    'tripwire_briar': ('Realm. Thorns and rusted towers. Ambush paths with arrow launchers and a laser watch post. Spool Weaver.',
                       'Reich. Dornen und rostige Türme. Hinterhalt-Pfade mit Pfeilwerfern und Laser-Wachposten. Spulenweber.'),
}
BIOMES.update({
    'arsenal_dunes': ('Realm. Red dunes with crashed shell casings, derelict pylons and a minecart loop that never stops.',
                      'Reich. Rote Dünen mit abgestürzten Geschosshülsen, verfallenen Masten und einer Lorenschleife, die nie hält.'),
    'rubedo_gardens': ('Realm. Terraces of redstone crystal, red pools and glass domes over growing crystals.',
                       'Reich. Terrassen aus Redstone-Kristall, rote Becken und Glaskuppeln über wachsenden Kristallen.'),
    'landmark_moors': ('Realm. Crimson heather, ruined walls, split monoliths and bell towers that toll by themselves.',
                       'Reich. Karminrote Heide, Ruinen, gespaltene Monolithen und Glockentürme, die von selbst läuten.'),
    'red_clay_fen': ('Realm. Red water and clay, reeds, boardwalks and brick kilns that never go out.',
                     'Reich. Rotes Wasser und Ton, Schilf, Stege und Ziegelöfen, die nie ausgehen.'),
    'hematite_scarps': ('Realm. Banded red cliffs and hoodoos, stamping crusher mills and caged beasts.',
                        'Reich. Gebänderte rote Klippen und Felstürme, stampfende Zermalmer-Mühlen und gefangene Bestien.'),
    'tempest_shoals': ('Realm. The Red Sea, a basin of rust brine in the widest chamber after the Heart, between black basalt pillars; storm spires call down lightning.',
                       'Reich. Das Rote Meer, ein Becken aus Rostlake in der weitesten Kammer nach dem Herzen, zwischen schwarzen Basaltsäulen; Sturmtürme rufen Blitze herab.'),
    'frostwork_wastes': ('Realm. Pale rime and salt over amber-crusted rails, frosted spires and leaning derelict towers.',
                         'Reich. Blasser Reif und Salz über bernsteinverkrusteten Schienen, bereifte Türme und schiefe verlassene Masten.'),
    'vein_mire': ('Realm. A dark fen threaded with glowing red roots and pale stalks.', 'Reich. Ein dunkles Moor voller leuchtender roter Wurzeln und bleicher Stängel.'),
    'oxide_salt_flats': ('Realm. White salt with glowing red cracks, scrap heaps and old track.',
                         'Reich. Weißes Salz mit leuchtenden roten Rissen, Schrotthaufen und alten Gleisen.'),
    'lamplit_grove': ('Realm. Giant pale-root trees with lamps hanging on chains under an ember-dark sky.',
                      'Reich. Riesige Bleichwurzel-Bäume mit Lampen an Ketten unter glutdunklem Himmel.'),
    'sealed_reach': ('Realm. Blighted land under a blood-red sky and a fog that never lifts, walled in by the Wirewrights: quarantine plating, sealed gates, warning signs all around. Wirewraiths and Maw Engines.',
                     'Reich. Verdorbenes Land unter blutrotem Himmel und ewigem Nebel, von den Drahtwerkern eingemauert: Quarantäneplatten, versiegelte Tore, ringsum Warnschilder. Drahtgespenster und Schlundmaschinen.'),
    'the_abyss': ('Realm. The void around the artery: nothing to stand on, a red haze, and far below the Blood Below, a sea of molten redstone with veins hanging into it.',
                  'Reich. Die Leere um die Ader: nichts, worauf man stehen könnte, roter Dunst, und tief unten das Blut darunter, ein Meer aus geschmolzenem Redstone, in das Adern hinabhängen.'),
    'circuit_fossil_beds': ('Realm, underground. Caves of fossil circuits, glowing redstone veins and crystal clusters.',
                            'Reich, unter Tage. Höhlen aus fossilen Schaltungen, leuchtenden Redstone-Adern und Kristallen.'),
})
for biome, (e, g) in BIOMES.items():
    name(f'biome.{NS}.{biome}.desc', e, g)

# ------------------------------------------------------------------------------------------ write and check
for lang, entries in (('en_us', en), ('de_de', de)):
    path = os.path.join(ASSETS, 'lang', lang + '.json')
    with open(path, encoding='utf-8') as f:
        current = json.load(f)
    current.update(entries)
    write(path, current)

with open(os.path.join(ASSETS, 'lang', 'en_us.json'), encoding='utf-8') as f:
    lang = json.load(f)
missing = []
for key in sorted(lang):
    parts = key.split('.')
    if len(parts) == 3 and parts[0] in ('item', 'block') and parts[1] == NS and not parts[2].endswith('_spawn_egg'):
        if key + '.desc' not in lang:
            missing.append(parts[2])
    if len(parts) == 3 and parts[0] == 'entity' and parts[1] == NS and key + '.desc' not in lang:
        missing.append('entity ' + parts[2])
    if len(parts) == 3 and parts[0] == 'biome' and parts[1] == NS and key + '.desc' not in lang:
        missing.append('biome ' + parts[2])
# tools and armor are listed by name only on purpose
gear = ('_sword', '_pickaxe', '_axe', '_shovel', '_hoe', '_helmet', '_chestplate', '_leggings', '_boots')
# parts that are never items in a player's hand (model-only blocks, piston parts, vehicles, TNT entities)
INTERNAL = {'realm_portal', 'drill_bit_model', 'drill_body_model', 'super_piston_arm', 'super_piston_head',
            'entity drill', 'entity frozen_tnt', 'entity nuke_tnt'}
missing = [m for m in missing if not m.endswith(gear) and m not in INTERNAL]
print(f'guide texts written ({len(en)} entries)')
if missing:
    print('NO DESCRIPTION YET (add a .desc text so the guide explains it):')
    for m in missing:
        print('  -', m)
    sys.exit(1)
print('everything in the mod has a description in the guide')
