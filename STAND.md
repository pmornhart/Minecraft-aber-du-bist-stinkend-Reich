# Stand (Übergabe)

_Zuletzt aktualisiert: 2026-11-08, 15:40 Uhr (Runde 0.5.0: Treppen neu, Butler überarbeitet, Sicherheit übers Handy, Leertaste im Hubschrauber)_

**Philips feste Regel (26.10., mehrfach gesagt; 27.10. ernst gemeint – „sonst wechsle ich zu Codex“):** Die Fortschrittsseite nach JEDEM Zwischenschritt neu
veröffentlichen (jede Gruppe Bilder, jedes fertige Feature, jeder Test) – ohne dass er es sagen muss.
Werkzeug: `scratchpad/seite_daten.py` anpassen, `seite_neu.py seite_daten.json`, dann Artifact mit derselben Datei.

## Erledigt

- **Baustein 1: Multi-Version-Setup.** Stonecutter 0.9.8 mit 17 Versionen (1.21 bis 1.21.11 mit Java 21, 26.1 bis 26.3 mit Java 25), eine .jar je Version, Mojang-Namen über `loom-back-compat`. Alle Versionsunterschiede stecken in `Kompat`/`KompatClient` oder in `//?`-Bedingungen.
- **Baustein 2: Villa.** Eigener Entwurf (`tools/villa/villa.py` → `villa.nbt`), entsteht in jeder neuen Welt am Spawn mit geebnetem und angeblendetem Gelände. 29 Mechanismen als eigene Blöcke (Sicherheitsstufen 0–3, Laser, Bewegungsmelder, Sirenen, Zugangskarte, Kartenleser, Tastenfeld, Tore, Rollläden, Schiebetüren, Aufzug zu allen Etagen und in den Bunker, Schmuck-Tresor mit Fallen und Entschärfknöpfen, Kameras mit Monitoren, Geheimgang, Fluchttunnel, Poolabdeckung, Whirlpool, Kamin …). **Komplett-Test in allen 17 Versionen bestanden** (25.10., Bilder je Version identisch). Ab 1.21.5 fiel beim Ebnen Laubstreu ab; das Aufräumen nach dem Bau entfernt jetzt alle herrenlosen Pflanzenreste.
- **Baustein 3: Butler.** Spieltest in 26.3 bestanden: Auftrag „hol mir 16 Eisen“ im Chat → Tunnel graben, Erz abbauen, im Hochofen schmelzen, liefern in gut 3 Minuten; „sortier die Truhen“ legt jede Sorte in ihre Stammtruhe.
  - `butler/ButlerEntity`: unverwundbar (nur /kill), folgt oder wartet, öffnet Holztüren, Taschen mit 27 Plätzen, speichert Aufträge mit. `gehZu` zählt Teilwege als Fortschritt; `hinkommen` springt bei Öfen, Truhen und Werkbank notfalls daneben.
  - Aufträge (`butler/auftrag/`): Erz mit Tunnelbau (`SammelAuftrag`, `Tunnel`, `Abbau`), Bäume fällen und neu pflanzen (`HolzAuftrag`), jagen (`JagdAuftrag`), echte Öfen (`OfenAuftrag`), herstellen (`HerstellAuftrag`), Speisekammer (`VorratAuftrag`), Truhen sortieren (`SortierAuftrag`), liefern (`LieferAuftrag`), kommen/heim (`GehAuftrag`).
  - `Planer` zerlegt Bestellungen mehrstufig, `Sprache` versteht deutsche Sätze, `ButlerBefehle`: `/butler`, `/butler rufen`, `/butler truhe`, Chat „Butler, …“, Menü per Rechtsklick.
  - Schutz (`Schutz`, `SpielerBloecke` + Mixin in `BlockItem.placeBlock`): nichts in der Villa, nichts Gesetztes, nur natürliche Blöcke.
  - Optional Claude (`ButlerKI`): offizielle Anthropic-Java-Bibliothek 2.75.0 (per `include` in der .jar), Modell aus der Config (Standard `claude-opus-5-5`), Werkzeuge = Butler-Befehle. Key nur aus `config/stinkendreich.json`.
- **Baustein 4: Bonzenauto.** Spieltest in 26.3 bestanden: Garage, einsteigen (Fahrer schaut in Fahrtrichtung, Kamera dahinter), Gas mit Trägheit, Kurve, Drift mit Reifenqualm, Scheinwerfer (Taste **G**, L ist in Minecraft „Fortschritte“), Hupe, drei Kameraarten, acht Lackfarben, vier Sitze.
- **Update-Funktion** (`update/UpdatePruefer`, `client/update/UpdateKlient`), **README**, **Release-Ablauf** (`.github/workflows/release.yml`: bei Tag `v*` oder neuer Nummer in `.github/release-version` alle 17 .jar bauen und anhängen).
- **Alle 17 Versionen im Spiel getestet:** Villa-Komplett-Test und Rundgang (`rundgang.txt`: Villa, Butler-Auftrag „hol 4 Eisen in die Truhe“ bis zur Lieferkiste, Auto, Poolabdeckung) überall bestanden.
- Werkzeuge: `tools/ingame/test.sh` (Spieltest mit Xvfb/Mesa; Aktionen u. a. `server`, `klient`, `halte`, `druecke`/`loslassen`, `merke`/`bis`), `tools/ingame/alle.sh` (alle Versionen nacheinander), Szenarien in `tools/ingame/szenarien/` (`villa_komplett`, `butler`, `auto`, `rundgang`), `tools/mcapi.sh` (Minecraft-Klassen je Version nachschlagen).

## Version 0.2 (in Arbeit, Stand 25.10. abends)

- Fertig und in 26.3 im Spiel getestet: Fehler aus Philips Bildern, neue Türen, Aufzugsfahrt, Glow-up der Räume,
  4 Partybereiche, Musik (DJ-Pult, Spotify), Tiefgarage mit Rampe, Fuhrpark und Drehteller (`auto/DrehtellerEntity`),
  Fahrzeug-Baukasten `tools/assets/fahrzeuge.py` (alle Autos, runde Räder, Textur 4-fach), XXL-Fernseher
  (`fernseher/`), Büro-Monitore (`Winkel` links/rechts/gerade/wand), Penthouse als privater Schlafbereich.
- Möbel (`tools/assets/deko.py`, `moebel/`): XXL-Kingsize-Bett (`KingsizeBettBlock`, echtes Bett, Spalten verbinden
  sich), Bettende/Bettbank/Sofas als `VerbundDekoBlock`, Nachttisch, Tischlampe, Kinotisch, Strandliegen,
  Sonnenschirm, Loungetisch; Hinsetzen über `SitzEntity`. Kurztest auch in 1.21 bestanden.
- Butler als Chauffeur (`butler/Chauffeur`, `FahrAuftrag`, Orte merken) und Pilot (`butler/Pilot`, `FlugAuftrag`),
  Luxus-Hubschrauber (`auto/HubschrauberEntity`) auf dem Landeplatz.
- OP-Ausrüstung (`ausruestung/`): Agenten-Anzug als echte Rüstung (`AgentRuestung`, je Version anders angelegt:
  bis 1.21.1 registriertes Material mit Schichten, 1.21.2–1.21.4 `ArmorItem`, ab 1.21.5 `humanoidArmor`),
  Texturen aus `tools/assets/ausruestung.py`, Kräfte in `AgentKraefte` (Nachtsicht + leuchtende Gegner, Resistenz,
  Tempo, Sprung, kein Fallschaden, Set: Stärke + Regeneration), Waffen `AgentWaffe` (Pistole, Laseruhr, Geldkanone,
  Sofort-Treffer ohne Blockschaden), Waffenkammer mit 6 verzauberten Ständern (`Waffenkammer`, Marke „ruestung“).
  Test: `tools/ingame/szenarien/agent.txt` und `agent_waffen.txt`.
- Achtung bei Testfotos: Kamera nicht in Kronleuchter stellen (die Kristalle sahen aus wie schwebende Flächen).
  Wüstenzombies geben Hunger (grüne Ringe im Bild) – für Waffenbilder Diener (Vindicator) nehmen.
- README-Fotos neu (`fotos.txt` + `fotos2.txt`, mit `warte 30` vor F1, sonst kommt F1 beim Laden nicht an).
- Video-Werkzeug: `fahrt` im Testmodus (Kamerafahrt über Wegpunkte), `video start/stopp` und `kapitel` in
  `test.sh`, `tools/ingame/video.py` (Titel, Kapitel, Musik), Drehbuch aus `tools/ingame/video_szenario.py`.
  Für flüssige Bilder keinen zweiten Spieltest parallel laufen lassen (sonst nur ~6 Bilder/s).
- Rundgang aller Versionen (mit Agent und Waffenkammer): **alle 17 Versionen OK** (1.21.1 nach Abbruch neu
  gelaufen; nie `test.sh` während eines Laufs ändern!).
- **Release 0.2.0 angestoßen** (Philip: „gleich raus und weiter testen“): `mod.version` und `.github/release-version`
  auf 0.2.0, Neuigkeiten in `.github/neu.md`. Die restlichen Versionen laufen im Spiel weiter; Fehler → 0.2.1.
- Truhe direkt neben dem Agenten-Ständer in der Waffenkammer entfernt (Philips Bild), Inhalt in die Nachbartruhe.
- Video an Philip geschickt (`villa_rundgang.mp4`).

## Version 0.2.1 (in Arbeit, Fehler aus Philips 20 Bildern)

- Erledigt und in 26.3 im Spiel geprüft (`tools/ingame/szenarien/bugs021.txt`): Personalküche mit Wand und
  Gartentür, Tresor wie auf Philips Bild (Diamantberg, Tresor-Blöcke aus dem Spiel), Garagendurchgang 2×3 mit
  Schiebetür, Balkon-/Penthouse-Doppeltür ohne Loch, Privattür (`PrivattuerBlock`, nur Bewohner, bleibt offen),
  Clubtür, Weinlounge als Weinkeller, VIP-Sofa auf dem Podest, DJ-Pult vorn, Wohnzimmer-U, Kamin symmetrisch,
  Penthouse-Lounge und Ostecke, Bunkerlager, Lava-Graben (`Graben`, Marken „graben“ und „haus“, zweistufiger
  Tausch gegen Obsidian), G-Klasse kommt die Rampe hinauf (`RAMPE_OFFEN_AB` in villa.py), Autos mit Maßstab
  (`Autotyp.massstab`), Tische `tafel_marmor`/`tafel_holz` (moebel.tisch), Sofas ohne Spalt.
- Rutsche (`Rutsche`, Marke „rutsche“): schiebt jetzt richtig (in 1 s unten im Pool). Wichtig: Bewegung an den
  Spieler geht ab 26.3 über `syncVelocity` (bis 26.2 `hurtMarked`), Hilfe `Kompat.bewegungSenden`.
- Waffenhaltung endgültig (Stand 0.2.2, Philip: „Griff in der Hand wie beim Schwert“): Mit Pistole/Geldkanone
  nimmt die Spielerfigur die Zielhaltung ein (`mixin/client/ArmHaltungMixin`: Armhaltung CROSSBOW_HOLD; bis 1.21.1
  `PlayerRenderer.getArmPose(Spieler, Hand)`, 1.21.2–1.21.3 Merkmal `holdsChargedCrossbow` in `extractHandState`,
  1.21.4–1.21.8 `getArmPose(Player, ItemStack, Hand)`, ab 1.21.9 `AvatarRenderer`). `WAFFEN_HALTUNG` von außen:
  [0,90,0] / [0,-90,0], Maßstab 0,85, Verschiebung [0,1.8,-3.1] (Griff senkrecht durch die Faust), Ich-Sicht
  [0,100,0] / [0,-100,0]. Rüstungsständer mit Waffe bekommen den Arm auf -90° (Waffenkammer). Prüfen an einer
  Schaufensterpuppe (`minecraft:mannequin`, ab 1.21.9) von der Seite (Szenario `show.txt`), ältere Versionen mit
  `check022.txt`; am Spieler steht der Körper nach einem Teleport schräg zum Kopf. Achsen in der Hand vor der Drehung:
  +y vorn, +z oben, x seitlich; Drehfolge erst z, dann y, dann x (Mojang-Standard). Zum Prüfen am Spieler: von
  hinten muss man das Ende ohne Mündung sehen, von vorn in die Mündung; die Ich-Sicht braucht eingeblendete
  Hand (kein F1). [0,-90,-80] zeigte nach hinten, [0,-90,100] hatte den Griff oben.
- Bilder aus Philips Nachrichten lassen sich aus dem Sitzungsprotokoll holen (Einträge vom Typ „attachment“).
- Zweite Runde (Sprachnachricht 25.10. abends): Haustür-Achse symmetrisch (Tor/Brücke/Teppich/Beete/Brunnen
  einen Block nach Westen, `ZUFAHRT_X0/X1` in VillaPlatzierung), Sicherheitstüren mit Kartenleser (Büro,
  Kontrollraum; neue Tür `sicherheitstuer`), Wand zum Kontrollraum, Gaming-Stuhl (`gamingstuhl`, Sitz),
  Zentrale auf dem Schreibtisch (Szenarien angepasst), Gästezimmer neu mit Fernseher, Bad mit Badewanne
  (`wanne_kopf`/`wanne_fuss`, Sitz) und Toilette mit Spülung (`ToiletteBlock`, Ton `toilette`), gefüllte Truhen,
  Landeplatz eine Lage höher (`LANDEPLATZ`), drei Sofas am Penthouse-Fernseher, Feuerstelle aus dem Beet,
  Clubtür am Gartenzugang (Tunnelhülle hatte x 30 wieder zugemauert), Geheimclub (`geheimclub()`, Geheimregal mit
  Schalter an der Club-Bar), Bunkertüren, Laser bündig in der Wand, Münzgrube (`goldmuenzen`, `Goldbad`) mit
  Sprungbrett, Tiefgarage mit Rampe mittig (`RAMPE_X0/X1`), Sektturm neu, Totem = Geldbündel (Bild und Name
  des Spiels ersetzt in `assets/minecraft`).
- Wunsch: YouTube-Videos im Fernseher richtig abspielen. Geprüft: geht nur mit WATERMeDIA (VLC) und deren
  YouTube-Erweiterung; Stand Juli 2026 für 1.21.1, 1.21.11, 26.1.2 und 26.2, noch nicht 26.3. Plan: optionale
  Anbindung, sobald es sie für 26.3 gibt (Philip spielt 26.3); sonst bleibt Vorschaubild + Browser-Knopf.
- Totem der Unsterblichkeit = Geldbündel: Bild und Name in `src/main/resources/assets/minecraft/` (von
  ausruestung.py erzeugt, `python3 tools/assets/assets.py` nach Änderungen ausführen).
- Club-Bar „ein bisschen Wasser“: in der neuen Villa nicht nachvollziehbar (`/villa lecks` findet nichts dort).

## Version 0.2.1 (veröffentlicht 25.10., 17 Dateien) und 0.2.2 (in Arbeit)

- 0.2.1: https://github.com/MoinMornhart/Minecraft-aber-du-bist-stinkend-Reich/releases/tag/v0.2.1
- 0.2.2: Penthouse-Fernseher mittig (Marke x 63.0), Waffen mit Zielhaltung (Griff wie ein Schwertgriff),
  Show-Tänzerinnen im Geheimclub (`club/TaenzerinEntity`, `client/club/Taenzerin*`, Texturen aus
  `tools/assets/taenzerin.py`; im Bauplan als Entities in `geheimclub()` mit „stange|Kostüm“ oder „laufen|Kostüm“,
  den Anker setzt die Figur beim ersten Tick selbst). Tänzerinnen tragen Glitzer-Showkostüme (bewusst nicht
  freizügiger, weil Repo öffentlich und Minecraft auch von Kindern gespielt wird).

## Version 0.2.2 (veröffentlicht, in allen 17 Versionen geprüft) und 0.2.3 (veröffentlicht 25.10. nachts)

- 0.2.3: Konzertflügel (`klavier/`, `client/klavier/KlavierBildschirm`, Modell `konzertfluegel` + 8 unsichtbare
  `fluegel_teil` in deko.py, Töne `klavier_c2…c6` aus sounds.py, Stücke in `Lieder`), Tresorfallen nur für
  Eindringlinge (`FallenplatteBlock`), Lava-Graben bei Bewohner auf dem ganzen Grundstück und im Lockdown immer
  (`Graben`), Goldmünzen kletterbar (Tag `minecraft:climbable`, `Goldbad` nur noch ohne Fallschaden), Leiter am
  Sprungturm an Eisensäule, James folgt nach `/butler rufen`.
- Türen: Haustür = Torflügel `portaltuer` (tueren.py, Modus „schiebetuer“, Radius 3), Banktür `banktuer` und
  `sicherheitstuer` sind `SicherheitstuerBlock` (Kartenleser öffnet direkt beide Flügel, bleibt offen, Rechtsklick
  schließt), Tür Ballsaal–Wohnzimmer, Wandlücken neben Gästezimmer- und Badtür zu.
- Weiße Decken: `register/ModDecken` (decke_holz, _teppich_weiss/_rot/_gelb, _grau, _gold, _schwarz, _schiefer;
  Vorbild per ID, `ofFullCopy`), Modelle in assets.py (`DECKEN`), villa.py `decken_weiss()` ersetzt sichtbare Böden.
- `pruefe_vor_glas()` in villa.py: Wandgeräte vor Glas → Villa wird nicht gespeichert.
- Magischer Eingang `mechanik/Geheimgang` (schleichen vor Geheimregal, Bewohner/Karte/Stufe 0, schließt nach 10 s),
  Geheimschalter liest den echten Zustand des Regals. Whirlpool taut Eis auf und dampft. Drehteller hält sich an
  der Marke. Garten-Lounge neu, Club-Sofa mit einem Block Abstand, Tänzerinnen im zweiteiligen Showkostüm (so viel
  wie ein Bikini, nicht freizügiger: Repo öffentlich, Kinder spielen Minecraft).
- Tests: `check023.txt`, `check023b.txt`, `check023c.txt` (Weltkoordinaten = Villa + (-56, 62, -56)).
- Neu geplant: README-Bilder, Trailer (`tools/ingame/trailer_szenario.py`), Server-Version ohne Mod beim Spieler
  (Philip: „soll alles gehen“ → Fabric-Server mit Ressourcenpaket und Paper-Plugin).

## Version 0.3 (in Arbeit, Philips Wünsche vom 25.10.)

- 0.2.3 ist in allen 17 Versionen geprüft (`kurz023.txt`, je 9 Prüfungen OK).
- **Handy** (`handy/`, `client/handy/HandyBildschirm`, Texte und Bild aus `tools/assets/handy.py`): Netz-Nachricht
  „handy“ mit `anruf|limo/heli`, `fahr|Ziel`, `liefer|Was`, `auftrag|Text`, `mitarbeiter`, `bodyguards|rufen/heim`.
  Geschenk einmal je Spieler (Marke `stinkendreich.handy` in `VillaPlatzierung.beimBetreten`, auch alte Welten).
  Knöpfe im Test (1280×720, Maßstab 2): Startseite y 188 + 42 je Knopf, Lieferservice y 185 + 34 je Knopf.
  `AnrufAuftrag`: Limo kommt zum Spieler (Parkplatz 25–40 Blöcke), `HeliBereitstellung` stellt den Hubschrauber
  bereit, `Lieferservice` (Tiere über `Kompat.entityTypNachId`, Waren ins Inventar). Test: `handy.txt` (OK).
- **Mitarbeiter** (`mitarbeiter/`): `MitarbeiterEntity extends ButlerEntity` (`istButler()` false), Kiste =
  `MitarbeiterkisteItem` (BlockItem der Truhe). Test `mitarbeiter.txt`: nach 5 Min. 16 Eisenerz + 16 Kohle in der Kiste.
- **Bodyguards** (`bodyguard/`): Golem-Modell mit eigener Smoking-Textur, unverwundbar, Pistole 4,5–24 Blöcke,
  Nahkampf, Creeper nur mit der Pistole (Nahkampf-Ziel ausgeschlossen, Creeper zählen bei der Zielwahl näher).
- **Straßennetz** (`strasse/`): `Strassennetz` (Speicher `stinkendreich-strassen.json`, Routen, Kreisel),
  `StrassenBau` (Spalten nach Abstand zur Mittellinie: Fahrbahn ≤ 2,5, Bordstein ≤ 3,5, Seitenstreifen ≤ 4,5; ein
  Block wird erst gebaut, wenn die Linie 12 Schritte vorbei ist; Stufen aus Tiefenschiefer-Platten; über Wasser
  mindestens einen Block hoch), `StrassenZiele` (Dorf, Mine, Endportal, Netherportal). Wegpunkte alle 3 Blöcke;
  `Chauffeur` fährt bei dichten Wegpunkten „auf Schienen“ (Ziel 6 Blöcke voraus, bremst vor Kurven).
  Befehle: `/butler strassen`, `/butler strasse <ziel>`, `/butler strassenpunkt <strasse> <punkt>` (Test-Teleport).
  Test: `strasse2.txt` (Bau, Fotos von oben, Fahrt zum Dorf): James fährt in 26.3 bis ins Dorf („Wir sind da“).
- README-Abschnitt „Goldenes Handy“ mit Fotos aus `fotos03.txt` und `fotos03_lieferung.txt` (Kiste-Bild ist ein
  Ausschnitt aus dem Mitarbeiter-Test). Alle 17 Versionen lassen sich bauen.
- **Release 0.3.0 angestoßen** (26.10. nachts). Kurztest für alle Versionen: `kurz03.txt` (Handy-Geschenk,
  Lieferung, Mitarbeiter, Bodyguards gegen Zombie, Kreisel, Fahrt ins Dorf).

## Server-Version (Spieler ohne Mod, in Arbeit seit 26.10. nachts)

- Weg: Fabric-Server mit unserer Mod **und Polymer** (Bibliothek von Patbox, gibt es für 1.21–26.3). Ist Polymer
  installiert, übersetzt `server/ServerPaket` (nur 26.3, Dateien mit `//? if >=26.3`) alles für Spieler ohne Mod;
  Spieler mit Mod erkennt der Server am Paket „hallo“ (`netz/ModErkennung`, Client meldet einen Empfänger an).
  Polymer nur als `modCompileOnly` (`deps.polymer` in stonecutter.properties.toml), Betreiber installieren es selbst.
- Erledigt (26.3): Blöcke (`BlockUebersetzung`: volle Würfel als Polymer-Texturblock, Rest als Barriere bzw.
  Strukturleere + Anzeige-Figur mit dem echten Modell, Modelle aus den Blockzuständen via `Blockmodelle`), Items
  (Papier mit unserem Item-Modell), Töne, Ressourcenpaket (Polymer baut und schickt es), James/Mitarbeiter/
  Tänzerinnen als Schaufensterpuppe mit eigener Haut, Bodyguards als Eisengolem, Sitze unsichtbar, Handy als
  Dialogfenster (`HandyDialog`, Befehl `/handy …`), Autos und Hubschrauber als Item-Modelle aus den Fahrzeugdaten
  (`FahrzeugAnzeige`, Modellraum mit y nach unten, Anzeige um x gedreht, Maßstab 4 × massstab), Fahren mit W/A/S/D
  auf dem Server (`AutoEntity.fahrerOhneMod`, Hubschrauber: Leertaste hoch, Strg runter).
- Test: `VANILLA=1 PORT=25571 tools/ingame/test.sh 26.3 tools/ingame/szenarien/vanilla*.txt` (Server mit Mod und
  Polymer aus `tools/ingame/server-mods/`, holen mit `tools/ingame/server_mods_holen.sh 26.3`, dazu ein normales
  Minecraft über `tools/ingame/vanilla_klient.py`). Tasten gehen dort per `xdotool --window`, Bilder vom Bildschirm.
- Bedienfelder ohne Mod (`netz/OhneMod`: Blöcke rufen auf dem Server `OhneMod.zeigen`, die Server-Version meldet
  Dialoge an; Knöpfe schicken `/stinkendreich knopf <art> <x y z> <wert>` = dieselbe Netz-Aktion wie die Bildschirme
  der Mod, `Netz.ausfuehren` prüft die Reichweite): Butler-Menü (`ButlerDialog`, `/butler knopf <nr> <befehl>`),
  Zentrale, Tastenfeld (Code + neuer Code), Aufzug, Flügel mit Klaviatur (Fenster bleibt offen), Musikanlage
  (ohne Mod: Schallplatten von Minecraft), Monitor (`KameraBlick`: Kamera des Clients auf eine Hilfsfigur,
  Springen = nächste Kamera, Schleichen = Ende). Aufzugskabine als Modell (`FahrzeugAnzeige.kabine`).
  Blöcke ohne Kollision bekommen ein Klickfeld; `mixin/BlockBenutzenMixin` verhindert, dass nach einem Blockklick
  zusätzlich das Handy aufgeht. Getestet (26.3, `vanilla_bedien.txt`): Tastenfeld, Aufzug, Klaviatur, Kameras.
- Tasten im Test ohne Mod: das Spielfenster ist ein Kindfenster der Wurzel (`xwininfo -root -tree`), Fahren mit
  W/A und Hubschrauber mit Leertaste geprüft (`vanilla_auto.txt`).
- Bodyguards ohne Mod: großer Türsteher im Smoking (Schaufensterpuppe mit Haut `bodyguard_mensch` aus handy.py,
  Attribut SCALE 1,45 über `modifyRawEntityAttributeData`).
- Spieler mit Mod auf dem Polymer-Server (`MITMOD=1`, Client ohne Polymer; `MITPOLYMER=1` mit): geht. Wichtig:
  `ModErkennung` merkt sich beim Anmelden (`ServerConfigurationConnectionEvents.CONFIGURE`), wer die Mod hat, sonst
  schwankt die Erkennung am Anfang der Spielphase (Figur als Ersatz, danach Mod-Daten → Verbindungsabbruch). Alle
  Polymer-Übersetzungen fragen `ModErkennung.hatMod(PacketContext)`; ohne Kontext (Polymer beim Laden von Chunks!)
  immer „ohne Mod“ – ein Absturz dort lässt den Server den Chunk neu erzeugen (Villa weg).
- Tests: `vanilla_bedien.txt` (alle Bedienfelder), `vanilla_figuren.txt` (Bodyguards, Butler, Geheimclub),
  `vanilla_oben.txt` (Villa von oben).
- Server-Version jetzt ab 26.1 (Fences `//? if >=26.1` in server/* und an den Server-Stellen in ButlerEntity,
  StinkendReich, Handy, HandyItem, AutoEntity, HubschrauberEntity; `deps.polymer` für 26.1–26.3). Geprüft mit
  `vanilla_figuren.txt` in 26.1.2 und 26.2. Vorsicht beim Umstellen: nur diese Stellen ändern, nicht alle
  `>=26.x`-Fences im Projekt.
- Server-Version jetzt ab 1.21.10 (Fences `//? if >=1.21.10`). Unterschiede der Polymer-Versionen in
  `server/PolymerKompat` (Paket-Kontext: ab 26.1 Fabric `PacketContext`, davor `xyz.nucleoid.packettweaker`;
  Anzeige-Daten `DisplayEntityData` bzw. `DisplayTrackedData`; Kamera- und Mitfahrer-Pakete selbst gebaut).
  1.21.9 fehlt (Polymer 0.14.2 kann keine Haut für Schaufensterpuppen). Keine Reflexion über Feldnamen: in der
  fertigen .jar für 1.21.x heißen die Felder anders (ab 26.1 ist Minecraft nicht mehr verschleiert).
  Geprüft mit `vanilla_figuren.txt`: 1.21.10, 1.21.11, 26.1.2, 26.2, 26.3.
- Server-Version jetzt ab 1.21.9 (Fences `//? if >=1.21.9`, `deps.polymer` 0.14.2 für 1.21.9). Die Haut der
  Schaufensterpuppen entsteht über das Datenformat von Minecraft (`ResolvableProfile.CODEC` mit `texture`/`model`),
  nicht mehr über Polymers Hilfsfunktion (die fehlte in 0.14.2). Geprüft mit `vanilla_figuren.txt` in 1.21.9.
- Drehteller und Fernseher ohne Mod: Item-Modelle wie die Aufzugskabine (`FahrzeugAnzeige.einfachesModell`), der
  Fernseher zeigt das Bild „aus“ und hat ein Klickfeld; Rechtsklick öffnet ein Dialogfenster (YouTube-Link,
  „Auf dem PC abspielen“ öffnet den Browser, Ausschalten), Aktion „fernseher“ wie im Menü der Mod.
- Wichtige Fehler der Server-Version gefunden und behoben (Test `vanilla_richtung.txt`, `vanilla_modelle.txt`):
  1. Volle Mod-Blöcke (Deckenblöcke = Böden, Laser-Sender …) erschienen als Notenblock: `BlockUebersetzung` muss
     `PolymerTexturedBlock` sein, sonst wandelt Polymer die Texturzustände wieder in normale Notenblöcke um.
  2. Anzeige-Figuren übernahmen Datenfelder der echten Figur, wenn Typ und Nummer passten (Polymer filtert nur
     danach): „ohne KI“ wurde zu „dreht sich immer zum Spieler“, die Lackfarbe des Autos zur Helligkeit (Autos fast
     schwarz). `ServerPaket.fremdeDatenEntfernen` löscht ab Feld 8 (Anzeige-Figuren) bzw. 15 (Schaufensterpuppen).
  3. Texturkoordinaten der Modelle: jetzt Ecke für Ecke wie `ModelPart.Cube` (vorher Ober-/Unterseite vertauscht
     und Seiten um 180° gedreht, das Fernsehbild stand auf dem Kopf).
  4. Bis 1.21.10 dürfen Modellteile nur um eine Achse in 22,5°-Schritten gedreht sein (Autos waren dort lila);
     `FahrzeugAnzeige` schreibt für diese Versionen das alte Format und lässt andere Drehungen weg.
  5. Anzeige-Figuren nehmen das Licht am Fußpunkt; stehende Modelle bekommen das Licht ihrer Mitte (`licht`).
- Testhilfe: `debug-profile.json` im Ordner des normalen Clients (`versions/<v>/run/vanilla/`) mit
  `looking_at_block_state: alwaysOn` zeigt oben links den Blockzustand, auf den man schaut (ab 26.x).
- `test.sh` wartet jetzt, bis Server und Client wirklich beendet sind (sonst schrieb ein noch speichernder Server in
  die Welt des nächsten Laufs: „Failed to load level data“).
- In Arbeit: 1.21.6–1.21.8 (Polymer 0.13, `deps.polymer` eingetragen, Server-Fences jetzt `>=1.21.6`). Dort gibt
  es keine Schaufensterpuppen: Menschenfiguren werden zum Standbild (`FahrzeugAnzeige.figur`, Spielermodell mit
  Haut, Überzüge mit `dehnen`) mit Klickfeld. Übersetzt in 1.21.5–1.21.10 und 26.3, noch nicht im Spiel geprüft
  (Server-Mods holen: `tools/ingame/server_mods_holen.sh 1.21.8`).
- Offen: 1.21–1.21.5 (ohne Dialogfenster → Truhen-Menüs), Paper-Plugin (Philip fragen, ob nötig).

## Paper-Plugin (in Arbeit seit 27.10. nachts, Ordner `paper/`)

- Eigenes Gradle-Projekt: `./gradlew -p paper build` → `paper/build/libs/stinkendreich_paper-<version>+paper26.3.jar` (Name bewusst ohne „stinkendreich-…+mc“, sonst hält die Update-Funktion der Mod das Plugin für ein Mod-Update)
  (paper-api 26.3.build.226, Java 25). Nimmt Bilder/Modelle/Töne/Texte und den Bauplan aus `../src/main/resources`
  (Ordner `mod/` in der .jar), dazu `bloecke.json` und `paket/` (beide von `tools/paper/daten_holen.sh`: Fabric-Server
  mit Polymer, `dev/PaperDaten` schreibt Kollision/Umriss/Licht/Klasse aller Mod-Blöcke, aus Polymers Paket kommen die
  Fahrzeug-/Kabinen-/Fernseher-Modelle).
- Aufbau: `Nbt` (eigener NBT-Leser/-Schreiber), `villa/Bauplan` (villa.nbt + villa.json), `villa/Baustelle` (Schritte wie
  in der Mod, Chunks per `getChunkAtAsync` + Plugin-Tickets, 20 ms je Tick), `block/VirtuelleBloecke` (Träger Barriere/
  Licht/Strukturleere bzw. Vorbild bei Deckenblöcken + ItemDisplay mit Item-Modell `stinkendreich:anzeige/<modell>` +
  Interaction als Klickfeld; Zustand und Werte im PDC der Anzeige-Figur), `block/Mechanik` (Türen, Sitzen),
  `block/Tore` (Torsteuerung), `block/Zugang` (Kartenleser, Sicherheits-/Bank-/Bunker-/Privattür, Zugangskarte),
  `block/Aufzug` (Dialog mit Etagen), `butler/Butler` (Schaufensterpuppe + unsichtbarer Händler als Körper),
  `Handy`, `Gaeste`, `Dialoge` (Paper-Dialog-API mit Rückrufen), `Ressourcenpaket` (baut Zip, eingebauter Webserver,
  Port `ressourcenpaket.port` 25580).
- Test: `tools/ingame/paper.sh <szenario>` (lädt Paper nach tools/ingame/server-mods/paper/, Server in build/paper-test,
  normales Minecraft über vanilla_klient.py, Szenario-Zeilen wie test.sh, `bis` liest das Serverprotokoll).
  Erster Test `paper_start.txt` (27.10. 01:15): Villa nach 16 s, Paket kommt an, Möbel/Türen/Kronleuchter sichtbar.
  Offen: Autos, Fernseher, Hubschrauber, eigene Gemälde (10 von 63 Figuren fehlen), 3 unbekannte Blockzustände im
  Bauplan (alte Namen aus DataVersion 3953, z. B. chain → iron_chain), Smart Home, Sicherheitssystem, Inkognito.
- Stand 03:05 (alles mit `tools/ingame/paper.sh` im Spiel geprüft, Szenarien `paper_*.txt`): Fuhrpark (`auto/Fahrzeuge`,
  W/A/S/D), Hubschrauber, Fernseher, Drehteller, eigene Gemälde (Item-Modell, Bild im Block-Atlas, Item-Anzeigen sind
  um 180° gedreht), Smart Home (`SmartHome`, smarthome.json), Gästeliste, Sicherheitssystem (`block/Sicherheit`:
  Stufen 0–4, Zentrale, Laser, Bewegungsmelder, Sirenen, Warnleuchten, Tastenfeld, Tresorfallen), Zugbrücke
  (`block/Zugbruecke`, Block-Anzeigen), Geheimgang, Lava-Graben, Kameras am Monitor (`block/Kameras`, kurz
  Zuschauer-Modus), Klingel/Kamin/Whirlpool/Toilette/Dusche/Schublade (`block/Haushalt`), Flügel und Musikanlage
  (`block/Unterhaltung`, `Lieder.java` wird aus der Mod mitübersetzt), Festung (`block/Festung`: Minengürtel,
  Geschütze, Notausgang mit Luke), Inkognito (`block/Inkognito`: Spalten aus Chunk-Abbildern im Zwischenspeicher,
  `VirtuelleBloecke.sperren/verschoben`). Testhilfen: `/villa zentrale|code|klick|kamera|lied|klaviatur|platte|bruecke|
  inkognito`, `/villa ort x boden+N z` (Höhe über Gelände). Testserver steht auf „friedlich“ (für Zombies
  `/difficulty easy`). Kaminschalter suchte in der Mod nur 5 Blöcke weit (linkes Feuer blieb an) – jetzt 7.
- Nachtschicht 27.10. (Philip schläft, Release 0.4.1 erst nach seinem „Release raus“): Fernseher-Fenster mit
  Video-Link (`block/Fernseher`, Bild „Video bereit“ = eigenes Modell `zusatz/…/fernseher_youtube`), Lieferservice und
  Bodyguards (`butler/Bodyguards`: Eisengolem + Puppe im Smoking, schießen mit der goldenen Pistole), Agenten-Ausrüstung
  (`ausruestung/Agent`: Netherit-Rüstung mit Ausrüstungsmodell `stinkendreich:agent`, Waffen = Papier mit Item-Modell und
  `USE_COOLDOWN`, Kennung im PDC `agent`; Kräfte wie `AgentKraefte`), Waffenkammer (`ausruestung/Waffenkammer`, Marken
  „ruestung“), Truhen-Inhalte der Mod (`Baustelle.modGegenstand`: Zugangskarte der eigenen Villa, Agenten-Teile),
  Tänzerinnen (`butler/Taenzerinnen`: Puppe wird jeden Tick bewegt; Puppen sind unverwundbar, sonst erstickten sie an der
  Stange), Haie (`hai/Haie`: unsichtbarer Delfin + zwei Anzeigen `polymer/hai` und `polymer/hai_schwanz` aus
  `tools/paper/hai_modell.py`, Biss ohne Verursacher = wirkt auch auf Friedlich, Todesnachricht selbst gesetzt).
  Geprüft mit `paper_agent*.txt`, `paper_nacht2.txt`, `paper_nacht3.txt` (Diener 24 → 16 → 8 → 5, Spieler im Becken
  15,2 → 10,8 → 2,8). Autotest-Zeilen `server @/befehl` laufen als Konsole (Ausgabe im Serverprotokoll).
- Danach (04:50): Haifalle (`block/Haifalle`: Knopf, Smart Home „haie“, Automatik bei Angriff auf Bewohner), Rutsche und
  Goldmünzen (`block/Pool`: Münzen sind Strukturleere, das Plugin lässt langsam sinken, Leertaste = hoch, kein
  Fallschaden). Wichtiger Fehler behoben: villa.py lässt Standardwerte weg (`haifalle` ohne open, Türen ohne powered);
  `Blockdaten.vervollstaendigen` ergänzt sie beim Lesen des Bauplans (Wahr/Falsch = false), sonst galt der erste
  Zustand aus bloecke.json (Glasboden offen und unsichtbar). `Zustand.mit` ändert nur vorhandene Eigenschaften,
  `Zustand.ergaenzt` setzt auch fehlende. Tests: `paper_pool.txt`, `paper_haifalle_*.txt`.
- Danach (06:10): Bergbau auf Paper (`butler/Bergbau`: Start neben der Zufahrt – die Zufahrt selbst ist kein natürlicher
  Block –, Suche 32 Blöcke, Treppentunnel, Ader, Abdichten, Rückweg; `butler/ButlerAuftrag`: Hochofen/Ofen der Villa –
  der Räucherofen schmilzt kein Erz –, Kohle aus den Fässern „brennstoff“, Lieferung; Wege höchstens 20 s, danach
  Sprung; Wegfinder beim Sprung/Ankommen stoppen, sonst läuft der Körper den alten Pfad zurück). Mitarbeiter
  (`butler/Mitarbeiter`, Kiste per Handy/`/handy mitarbeiter`). Tests: `paper_butler_kohle.txt`,
  `paper_butler_eisen.txt` (3 Eisenbarren geliefert), `paper_arbeit.txt` (16 Kohle in der Kiste).
- Danach (06:40): Holz und Truhen sortieren auf Paper (`butler/Holzfaeller` als `Beschaffung` wie `Bergbau`, beide
  liefert `ButlerAuftrag`; `butler/Sortierer` und `ButlerAuftrag` sind `Auftrag`; `butler/Laufen` = Wegfinder mit Sprung).
  Tests: `paper_holz.txt` (10 Holz, Setzlinge; drei Truhen genau richtig sortiert), Bilder `paper_holz_foto.txt` mit
  Testhilfe `/villa butlerblick` (freie Stelle mit Sicht auf James). Testskripte: Serverprotokoll jetzt UTF-8 (sonst
  „?“ statt „→“, `bis` mit Pfeil wartete ins Leere), `bis` ohne Dateinamen-Auflösung (`*` im Muster).
  Jagd (`butler/Jaeger`, Config `butler.jagen`): Test `paper_jagd.txt` (drei Tiere, 6 Fleisch, geliefert).
- Pilot (07:10): `butler/Flug` (ABHOLEN → EINSTEIGEN → REISE → ANGEKOMMEN), `Fahrzeuge.hubschrauber/pilotSetzen/sitztDrin/
  steuern/einsteigen`. Der Autopilot versetzt den Hubschrauber per Teleport (mit Geschwindigkeit stand er still, weil der
  Landeplatz außerhalb der Simulationsweite lag). Reiseflughöhe 18 über dem höchsten Punkt unter/vor ihm, Landeplatz
  neben dem Spieler (`Flug.landeplatzBei`: eben, kein Laub/Wasser). Dorf über `org.bukkit.StructureType.VILLAGE` (eine
  Suche, je Welt gemerkt; dauert beim ersten Mal 2–4 s). Test `paper_flug.txt`: abholen in 11 s, 1200 Blöcke ins Dorf
  in 90 s, zurück auf den Landeplatz. Testhilfe `/villa einsteigen`.
- Flug-Sicherheit (07:25): Aussteigen in der Luft gibt 30 s Sinkflug (Slow Falling), der Hubschrauber landet dann dort
  (Phase LANDEN). Liegt der Landeplatz in einem ungeladenen Chunk, wird er kurz geladen und die Suche wiederholt. Dorf
  wird eine Minute nach dem Start vorab gesucht. Test `paper_flug2.txt` (aus 26 Blöcken Höhe ausgestiegen: 20/20 Leben).
  Regression nach den Umbauten: `paper_auto.txt`, `paper_nacht1.txt` ohne Fehler.
- Straßen und Chauffeur auf Paper (07:50): `strasse/StrassenBau` (Port aus der Mod: 7 breit, Mittellinie, Bordstein,
  Laternen, Stufen, offener Einschnitt, Brücken; Chunks per `getChunkAtAsync` + Plugin-Ticket; Natürliches über
  `strasse/Natur`; Landminen der Festung werden überbaut), `strasse/Strassen` (Warteschlange, `strassen.json`, Straße
  von der Zufahrt der Spawn-Villa zur Zufahrt jeder Nachbarvilla, seitliche Wegpunkte, wenn das Ziel hinter einer Villa
  liegt; Villen ohne Straße werden 30 s nach dem Start nachgeholt). Im Gebirge wird die Straße mit der Zeit „geduldiger“
  (`strenge`: weniger Umwege, mehr Einschnitt) – vorher kreiste sie und brach ab. `butler/Chauffeur`: Limo fährt ans Ende
  der Zufahrt, James am Steuer, Fahrt „auf Schienen“ über die Mittellinie (bis 0,75 Blöcke/Tick, vor Kurven langsamer).
  Befehle „fahr mich zur Villa von Max“ / „nach Hause“ (über die Spawn-Villa), Handy-Knopf „Limo: Villa von …“.
  Testhilfen `/villa strassen`, `/villa strasse <nr>`, `/villa strassenblick <anteil> [höhe]`. Tests `paper_strasse.txt`
  (714 Schritte bis ans Ziel), `paper_chauffeur.txt` (ganze Straße gefahren).
- Trailer (07:55, 12:00): neuer Paper-Teil 3 (2:17) am Ende: Haifalle, Rutsche, Münzbecken, Tunnel, Holz, Sortieren,
  Jagd, Mitarbeiter, Hubschrauber, Straße und Limo. Aufnahme in Stücken (`paper_trailer3.txt` a–g, `paper_trailer3h.txt` h–j, `paper_trailer3k.txt` k–l, mit
  `CHAT_AUS=1`), Schnitt `tools/ingame/trailer3_schnitt.py` (Leerlauf raus), dann `video.py` und an `docs/trailer.mp4`
  gehängt (ffmpeg concat).
- Trailer-Nachdreh: In `trailer_kennzeichen.txt` verschwindet der Geländewagen nach etwa 20 s vor dem Tor (noch
  ungeklärt); im Trailer 0.4.0 ist die Nummernschild-Szene deshalb das Spielbild aus `nachbarn041.txt` mit Zoom.

## Version 0.4.0 (veröffentlicht 27.10. 01:08: https://github.com/MoinMornhart/Minecraft-aber-du-bist-stinkend-Reich/releases/tag/v0.4.0)

- **Nachbarvilla im Hintergrund** (`villa/VillaBaustelle`): Schritte SUCHEN (flachster freier Platz der inneren
  Ringe, Höhe nur geschätzt über `ChunkGenerator.getBaseHeight`, ohne Chunks; Plätze mit Straße darauf fallen weg),
  LADEN (Chunks in Gruppen zu 24 über `ServerChunkCache.updateChunkForced`; `setChunkForced` lädt in allen Versionen
  sofort und blockierend!), MESSEN, GELAENDE, BLAETTER, BLOECKE (Palette über `mixin/StrukturZugriff`, je Tick 20 ms,
  Flags 2|16, Truhen-Daten mit `loadWithComponents`), FORMEN (`Block.updateFromNeighbourShapes` + Nachbarn melden),
  FIGUREN (Minecrafts `placeEntities` per Invoker), AUSSTATTEN (`VillaPlatzierung.ausstatten`). Strukturdatei wird beim
  Serverstart im Hintergrund vorgeladen (erstes Laden kostete 5–6 s). Fortschritt in der Leiste, Spieler wird nach dem
  Bau hingebracht (Tag `stinkendreich.eigene_villa`). Testbefehl `/villa nachbar [name]` (erfundener Besitzer).
- **Straße zur Nachbarvilla** endet am Ende ihrer Zufahrt (`VillaPlatzierung.zufahrtEnde`, `Strassennetz.villaZiel`);
  liegt das Netz hinter der Villa, erst ein Zubringer seitlich vorbei. Zufahrt jetzt offener Einschnitt bis zur
  Oberfläche. Straßen umfahren alle Villen (`StrassenBau.geschuetzt` mit `VillaPlatzierung.bei`), zweigen nicht aus
  überbauten Stücken ab. Straßenbau lädt Chunks im Hintergrund (5 × 5 um die Spitze, `umgebungBereit`).
- **Navi/James:** Villen der anderen im Handy (mit und ohne Mod, `Handy.andereVillen`), „fahr zur Villa von Max“
  (`Sprache.ziel`, `VillaPlatzierung.mitHerrnImText`), „nach Hause“ = eigene Villa.
- **Nummernschild:** `client/auto/KennzeichenLayer` (goldene Schrift auf schwarz), Lage aus `schilder` in den
  Fahrzeugdaten (fahrzeuge.py), Name in `AutoEntity.KENNZEICHEN` (wird gesetzt, wenn der Wagen in einer Villa steht).
- **Inkognito-Wunsch:** `Inkognito.schalten` während einer laufenden Fahrt merkt sich den Wunsch (`WUNSCH`) und führt
  ihn nach der Fahrt aus (vorher verworfen → Villa blieb im Trailer unten). Log „Inkognito: Villa N ist unten/oben“,
  der Trailer wartet darauf (`bis 120 Inkognito: Villa 0 ist …`).
- Test: `nachbarn041.txt` (26.3). Bekannt: Minecrafts Strukturen-Suche (Dorf, Endportal) ruckelt beim ersten Betreten
  einmal 2–7 s.

## Version 0.3.9 (veröffentlicht 26.10. 23:02: https://github.com/MoinMornhart/Minecraft-aber-du-bist-stinkend-Reich/releases/tag/v0.3.9)

- **Inkognito neu** (`mechanik/Inkognito`): Phasen `LADEN_RUNTER, RUNTER, DECKEL_ZU, BAEUME_HOCH | BAEUME_WEG,
  DECKEL_AUF, LADEN_HOCH, HOCH`. Vor der Fahrt werden alle Chunks der Villa erzwungen geladen (`laden`, 40 Ticks),
  Figuren (Bilder, Rahmen, Ständer, Autos ohne Spieler, Fische …) werden wie in einer Strukturdatei eingepackt
  (`StructureTemplate.fillFromWorld` + `mixin/StrukturZugriff`), entfernt und nach der Fahrt um dy verschoben neu
  angelegt (`StructureEntityInfo.pos` ist relativ, deshalb die eigene Position verschieben). Nach jeder Spalte
  `ruhe()` (Block- und Flüssigkeits-Ticks löschen), damit nichts fließt oder abfällt. Spieler über dem versenkten
  Dach werden beim Hochfahren nicht mitgehoben, sondern danach oben abgesetzt.
- Testhilfe `/villa vergleich merken|pruefen [dy]` (`villa/VillaVergleich`): jeder Block und jede feste Figur;
  natürliche Änderungen (Gras unter Blöcken → Erde, Seetang, Torflügel, Fledermäuse) zählen nicht.
  Ergebnis Hin- und Rückfahrt: „VERGLEICH alles gleich“ (Szenario `inkognito_vergleich.txt`, Tiefe 33).
- **Straßen immer an der Luft** (`StrassenBau`, `Strassennetz`): Chunk vor der Höhenabfrage laden, offener
  Einschnitt statt Tunnel, keine Unterführung unter einer anderen Straße, Brücken über Wasser, Stufen ohne
  Erdreihe. Seit 26.1 zählen Gras/Podsol/Myzel nicht mehr zu `BlockTags.DIRT` → `Schutz.istNatuerlich` kennt sie
  jetzt einzeln (auch Blumen, Setzlinge, Eis, Korallen …). Befehle `/butler strassen neu`, `/butler strassen pruefen`.
  Geprüft in neuer Welt: Dorf 695, Mine 102, Netherportal 280, Endportal 1407 Punkte, 0 unter der Erde
  (eine Bordsteinkante an der Kreuzung Endportal/Netherportal ist noch ein Hindernis).
- **Tor, Garage, Zugbrücke** (`TorsteuerungBlock.amTor`: zu Fuß 6 Blöcke, im Auto 14, James nur wenn er fährt bzw.
  hinläuft; Smart Home „Tor bleibt zu“/„Garage bleibt zu“ = `tor_zu`/`garage_zu`). Zugbrücke: geparkte Autos
  zählen nur in 12 Blöcken Umkreis, James nur beim Fahren. Test `tore039.txt`, `bruecke039.txt` (alles OK).
- **Gästeliste** (`VillaZustand.gaeste`, `darfRein` = Bewohner oder Gast): Kartenleser, Privattür, Tor, Geheimgang,
  Zugbrücke. Handy-Seite GÄSTE, ohne Mod im `HandyDialog`, `/butler gast <name>`, `/butler gast weg <name>`,
  `/butler gaeste`, Chat „Butler, lass Max rein“, Werkzeug „gast“ für Claude.
- **Haie** (`hai/HaiEntity`): greifen jeden Spieler im Wasser an (außer Kreativ), auch auf Friedlich (eigenes Ziel
  `Jagd`, Schadensart `stinkendreich:hai` mit `"scaling":"never"`), eigenes Modell `client/hai/HaiModell` und Textur
  aus `tools/assets/hai.py`.
- Aufzug ruckelt nicht mehr (`AufzugKabine`: Client holt Abweichungen weich auf statt zurückzuspringen).
- Autos werden an Leitplanken/Bordsteinen nicht mehr ausgebremst (Streifen = kaum Tempoverlust, nur echter
  Aufprall bremst). Handy im Hubschrauber geht (Rechtsklick auf das eigene Fahrzeug = PASS).
- Kamera-Monitor stürzt mit NoRisk Client nicht mehr ab (Hilfsfigur bekommt eine Kennnummer).
- Bodyguards halten die goldene Pistole (`client/bodyguard/PistolenLayer`, vier Zeichen-Varianten je Version).
- Villa: zwei fehlende Stufen der Haupttreppe (Kopffreiheit), Sektturm im EG und Kaktus/Bambus im Bunker weg.
- YouTube: kein endloser Neustart nach dem Laden mehr, yt-dlp liest die Videobeschreibung aus einer Datei
  (in der Cloud nicht mit YouTube testbar).
- Alle 17 Versionen übersetzen. README: Gästeliste, Straßen mit Brücken, Haie, Bodyguard mit Pistole.

## Version 0.3.7 und 0.3.8 (veröffentlicht 26.10. abends)

- 0.3.7: läuft mit NoRisk Client (`fabricloader` „*“), Inkognito-Fixes, YouTube über yt-dlp, Straßen beim ersten
  Betreten automatisch. 0.3.8: automatische Straßen enden an der Oberfläche (Chunk vor der Höhenabfrage laden).

## Version 0.3.6 (in Arbeit, Philips Wünsche vom 26.10. mittags)

- Erledigt (26.3 geprüft): Haustür öffnet alle drei Reihen (`TorsteuerungBlock.spalteSchalten`), Teppich unter der Tür,
  James sitzt in der Limo tiefer (`vehicleAttachment` 0,6), **Zugbrücke** (`mechanik/Zugbruecke`, Marke „bruecke“,
  Animation mit Block-Displays: ab 26.3 BlockState als `id`/`properties`, davor `Name`/`Properties`; Mauern davor,
  Sicherung, fährt für Bewohner zu Fuß und im Auto aus, Schalter innen/außen, Handy, James, `/villa bruecke ein|aus`).
- Rechte: Zugangskarte = Bewohner (`VillaZustand.istBewohner`), Alarmstufe nur der Eigentümer (erster Bewohner).
- Aufzug: Haltestelle „Untergrundbasis“ (Wert `karte=ja`) nur mit Zugangskarte (`AufzugstafelBlock.nurMitKarte`).
- Bunker-Suite (`bunker_suite()` in villa.py): Fernseher 8 × 4 (`FernseherEntity.setzeGroesse`, Marke mit Breite/Höhe),
  Bad mit Goldtoilette, Kingsize-Bett, Küche; Gang mit Stahlrippen, Rohren, Warnstreifen (`bunker_gang()`).
  Geheimregal im Bunker geht per Schleichen auf wie am Club (gleiche `Geheimgang`-Logik).
- In Arbeit: **Stufe 4 „Festung“** (`VillaZustand.FESTUNG`, nur an der Zentrale mit `kommando=ja` in der
  Kommandozentrale), `Festung` (Ziele: Monster + Eindringlinge), `LandmineBlock` (55 Minen im Rasen, `festung()` in
  villa.py), `AbwehrgeschuetzBlock` (9 auf der Innenmauer, Laser 40 Blöcke), Zugbrücke in der Festung immer gesichert,
  Notausgang (Tunnel unter Graben und Lava hindurch bis x 2, Leiter, Luke, Privattür im Außenzaun, grüne Schilder
  `notausgang_links/_rechts` aus deko.py), Kommandozentrale wie das Büro (`kommandozentrale()`), Tür zur Suite.
  Test: `festung038.txt`, Testhilfe `klient zentrale <wert>` und `klient zentralemenue`.
- Festung fertig und im Video geprüft (`festung_video.txt` → `festung.mp4`): Minen im Ring 40 Blöcke ums Grundstück
  (`villa/Festungsbau.minenfeld`, Raster 8), Mauer höher (`MAUER_OBEN`), auf der Mauerkrone abwechselnd Abwehrgeschütz
  und Himmelslaser, Animation als Welle (`Festung.verzoegerung`), Sirenen, nichts im Chat (nur Leiste), Notausgang
  mit versteckter Falltür (`GeheimlukeBlock`, nur mit Karte) 50 Blöcke westlich. Testbefehle `/villa minentest`, `/villa luke`.
- Monitore am Schreibtisch: Element-Drehung +22,5° (`links`) dreht im Spiel nach außen. Richtig zum Stuhl: Büro
  (Bildschirm nach Norden) x 68 `links`, x 70 `rechts`; Bunker (nach Süden) x 44 `rechts`, x 46 `links`. Am Foto prüfen.
- **Smart Home** (`mechanik/SmartHome`, `SmartlichtBlock` mit `lit` + `farbe`, Marken „smartlicht“ statt Laternen in
  `lichter()`, Speicher `VillaZustand.smartHome`): Handy-Seite SMART (Rollläden, Brücke, Licht, 8 Farben, Disco,
  Automatik), ohne Mod im `HandyDialog`, Befehl `/handy smart|<was>|<wert>`. Automatik: ab 12500 abends, ab 23500
  morgens (Uhr ab 26.1 `getDefaultClockTime`, davor `getDayTime`). Test `smart038.txt`, `smart038b.txt`.
- Fortschrittsseite neu aufgebaut (`scratchpad/seite_neu.py` + `seite_daten.py`): Läuft/Offen mit Uhrzeit, große Updates
  (0.3.6 Festung & Smart Home, 0.4.0 Server-Plugin & Nachbarschaft, 0.4.1 Server für alle Versionen), Archiv.
- Philips Zeitplan (26.10.): Mod-Features bis 17 Uhr, README/Trailer/Release 0.3.6 bis 18 Uhr, danach Server-Plugin
  ohne Zeitlimit.
- Danach: README komplett neu mit allen Bildern geprüft (Philip: Drohnenbild veraltet), Trailer, Release 0.3.6.
- Ganz am Schluss (Philip: „Rest des Tages dafür“): **Paper-Plugin** und **mehrere Villen auf dem Server** mit
  Abstand, Straßen zwischen den Villen, Villen im Handy-Navi, Besitzer über dem Butler, auf der Zugangskarte und
  auf dem Nummernschild.

## Version 0.3.6 (veröffentlicht 26.10. 16:54, 17 Dateien: https://github.com/MoinMornhart/Minecraft-aber-du-bist-stinkend-Reich/releases/tag/v0.3.6)

- Inkognito (`mechanik/Inkognito`): Villa fährt Schritt für Schritt um `tiefe()` = hoehe − bodenY + 2 nach unten
  (Spalten je Tick in 4 Streifen, nur geänderte Blöcke), Strukturdatei `inkognito/villa` für Truhen-Inhalte, Deckel aus
  Erde/Gras ringweise, Tarnbäume (`tarnung`, Zufall aus Villa-Position), Figuren fahren mit (Bilder per setPos +
  neu anmelden, `ButlerEntity.hoeheVerschieben`), kein Wand-/Fallschaden während der Fahrt. Server-Stopp: Fahrt
  wird ohne Animation zu Ende gebracht. Test: `inkognito_video.txt`, `inkognito_figuren.txt`, `haie036.txt`.
- Haie (`hai/HaiEntity`, `client/hai/HaiRenderer` färbt die Delfin-Textur zur Laufzeit um), Haifalle (`mechanik/Haifalle*`),
  Smart Home `haie|auf`, Krisenbunker (`smart krisenbunker`: Aufzug gesperrt in `Aufzug`), Smart-Home-Seite mit
  Zustand (`SmartHome.status`, Rückmeldung „smart“). Duschen (`mechanik/Dusche*`, Szenario `dusche036.txt`).
- Alle 17 Versionen übersetzen (Hai: kein `canBreatheUnderwater`-Override, in 1.21–1.21.5 final).

## Version 0.3.5 (veröffentlicht 26.10. vormittags, 17 Dateien: https://github.com/MoinMornhart/Minecraft-aber-du-bist-stinkend-Reich/releases/tag/v0.3.5)

- Nachttisch mit Schublade: `moebel/NachttischBlock` (DekoBlock + EntityBlock, Rechtsklick öffnet) und
  `moebel/SchubladeBlockEntity` (9 Plätze, `ChestMenu` GENERIC_9x1; Speichern ab 1.21.6 ValueOutput/ValueInput, davor
  CompoundTag). In deko.py `SCHUBLADE = {"nachttisch"}` → Eintrag „schublade“ in deko_bloecke.json, `ModDeko` baut den
  Block. Alte Welten: `Level.getBlockEntity` legt die fehlende Schublade beim ersten Öffnen an.
- Flügel: Ballsaal leer, `konzertfluegel(x0, y, z0, tasten)` in villa.py; Wohnzimmer (39, EG+1, 61, Tasten Nord, Bank
  bei 40/60, mitten im freien Teil südlich der Sofas; Philip wollte ihn weg vom Fenster) und Penthouse (47, DACH+1, 44, Tasten Ost, Bank bei 50/45, vier Blöcke von der Glaswand).
- Neue Sitzmöbel (VERBUND in deko.py): `esszimmerstuhl` (Sitzhöhe 9) im Esszimmer (x 66–74, z 55 und 59) und
  `gartenbank` (Sitzhöhe 7) an der Grillecke; Gaming-Stühle im Kontrollraum und Bunker statt Steintreppen.
- Barhocker sind Sitze (`SITZE` in deko.py, Höhe 11); in villa.py schauen sie zur Theke (Poolbar west, Club süd,
  Weinkeller zum Fass). `SitzEntity` lässt den Blick nur ±100° um die Blockrichtung drehen.
- Tests: `moebel035.txt` (alles OK, 26.3), README-Fotos `fotos035.txt` (Flügel, Barhocker sitzen). Alle 17 Versionen
  übersetzen. Trailer neu (3:31, `docs/trailer.mp4`).
- README: Bilder Ballsaal (ohne Flügel), Wohnzimmer-Flügel, Spielfenster, Esszimmer, Penthouse-Flügel, Schublade,
  Grillecke (mit sitzender Figur); Trailer mit Kapiteln „Ballsaal“, „Konzertflügel“ (Wohnzimmer) und „Esszimmer“.

## Version 0.3.4 (veröffentlicht 26.10. morgens: https://github.com/MoinMornhart/Minecraft-aber-du-bist-stinkend-Reich/releases/tag/v0.3.4)

- Bodyguards: echter Eisengolem + Überzug. `BodyguardRenderer` legt beim ersten Zeichnen
  `textures/entity/bodyguard_smoking.png` (nur Sakko, Hose, Hemd mit Krawatte, rote Blume, Sonnenbrille; Rest
  durchsichtig, aus `handy.py` `bodyguard_smoking()`) über die Golem-Textur aus Minecraft (auch aus Ressourcenpaketen)
  und merkt sich das Ergebnis als `DynamicTexture`. Keine Golem-Textur im Repo.
- Autos: Limo-Dach in Kopfhöhe + Luft, Frontscheibe weit vorn und schräg (Säulen im Teil „scheibe“), Supersportwagen
  und Cabrio Maßstab 1,5, Supersportwagen mit Säulen am Dach. Prüfen mit `autos_kopf.txt` (Schaufensterpuppen auf den
  Sitzen, Plattform am Himmel bei 198–262 / 150 / 196–218).
- Eingang (villa.py): Teppich und Quarzrand von `HZ1 + 1` bis unter das Tor (`n - 13`), keine Laterne an der
  Abzweigung, `kurve_innen`/`kurve_aussen` runden die Zufahrt zur Garage, Mittellinie im Bogen (Mitte 80/88, r 7).
- Mitarbeiter: neuer Skin (`handy.py` `mitarbeiter()`: Helm als Hut-Schicht mit Stirnlampe, Warnweste, Ausweis, $).
- Tests: `eingang_mitarbeiter.txt`, `eingang_mitarbeiter2.txt` (26.3, alles OK).
- **YouTube im Fernseher (eigene Lösung):** `client/fernseher/VideoWerkzeuge` (yt-dlp, Deno, FFmpeg aus PATH oder nach
  Zustimmung von GitHub nach `.minecraft/stinkendreich/werkzeuge`, yt-dlp `-U` einmal je Start), `VideoQuelle` (yt-dlp
  `-j`, H.264 bis 480p + Ton), `VideoAbspieler` (zwei FFmpeg-Prozesse: Bilder 640×360 RGBA 30/s in die Bildschirmfläche
  einer 2048×512-Textur bei 16/16, Ton Mono 48 kHz als `AudioStream` über `VideoTon`/`FabricSoundInstance`, Klang
  „fernseher_ton“ mit 32 Blöcken Hörweite; Uhr startet beim ersten Tonabruf, Lücken = Stille + später überspringen,
  überfällige Bilder verwirft schon der Lese-Thread), `FernseherVideos` (Abgleich mit dem Server je Tick, 48 Blöcke,
  Hinweise über der Hotbar), `FernseherFreigabeBildschirm` (Zustimmung, Config `fernseherHilfsprogrammeLaden`).
  Server: `FernseherEntity` Feld WIEDERGABE ("L<Tick>" läuft, "P<Ticks>" angehalten), Befehle `!abspielen|Link`,
  `!pause`, `!weiter`, `!spulen|s`, `!stopp`. Test ohne YouTube: `STINKENDREICH_TESTVIDEO=<datei>` (dann nur FFmpeg),
  Zeitlupe für Trailer `STINKENDREICH_VIDEOTEMPO=0.333`. Szenario `fernseher_video.txt` (26.3 OK: Video, Pause,
  Spulen, Menü, Stopp). YouTube selbst ist in der Cloud nicht testbar (Downloads/Programme von außen gesperrt).
- **Fernbedienung** (`fernseher/Fernbedienung`, `FernbedienungBlock` mit Zustand `tiefe` 0–8 = liegt auf dem Möbel,
  `FernbedienungItem`: Rechtsklick steuert den nächsten Fernseher bis 32 Blöcke, Schleichen + Rechtsklick legt ab).
  Modell in deko.py (`TIEFER`, Material `fernbedienung_hd`). In der Villa auf dem Nachttisch (Schlafzimmer OG) und dem
  Loungetisch im Penthouse-Kino. Szenario `fernbedienung.txt`.
- **Handy-Navi „Meine Orte“:** Seite ORTE im `HandyBildschirm` (Liste, ✕ löscht, Name + „Ort hier speichern“),
  Server `Handy` Aktionen `orte`, `ort|merken|Name`, `ort|vergessen|Name`; Liste kommt als neues Paket
  `netz/Rueckmeldung` („orte“) zurück. Ohne Mod im `HandyDialog` (Textfeld + Speichern). `ButlerEntity.ort` findet
  auch „meine Mine“; Mine-Synonyme: minen, erzmine, zeche, schacht. Szenario `navi_orte.txt` (OK).
- Philips Regel: am Ende jeder Runde README und Trailer (`trailer_szenario.py`, jetzt mit Fernseher, Fernbedienung,
  Navi) neu. Trailer-Dreh: `SICHTWEITE=12 STINKENDREICH_TESTVIDEO=<alter Trailer> STINKENDREICH_VIDEOTEMPO=0.333
  tools/ingame/test.sh 26.3 tools/ingame/szenarien/trailer.txt`, dann `ZEITLUPE=3 python3 tools/ingame/video.py …`.
- Geprüft vor 0.3.4: alle 17 Versionen übersetzen; im Spiel 26.3 (Golems, Autos, Eingang, Mitarbeiter, Fernseher-
  Video, Fernbedienung, Navi) und 1.21 (Fernbedienung, Video im Fernseher).

## Version 0.3.3 (veröffentlicht 26.10. morgens, 17 Dateien: https://github.com/MoinMornhart/Minecraft-aber-du-bist-stinkend-Reich/releases/tag/v0.3.3)

- Server-Version 1.21.9–26.3 mit allen Darstellungs-Fixes (siehe oben), Drehteller und Fernseher ohne Mod,
  Chauffeur-Rettung. Vorher geprüft: alle 17 Versionen übersetzen, 26.3 ohne Mod (`vanilla_richtung`, `_auto`,
  `_bedien`, `_figuren`, `_modelle`, `_club`, `_tv`) und mit Mod (`kurz03` alles OK), 1.21.9 ohne Mod
  (`vanilla_figuren`, `vanilla_richtung`). README: Server-Abschnitt ab 1.21.9, neue Bilder (Penthouse, Autos,
  Garage, Fernseher-Menü).

## Versionstest 0.3.1 (kurz03, alle 17 Versionen)

- Überall OK: Handy-Geschenk, Lieferung, Mitarbeiter, Bodyguards, Kreisel, Fahrt ins Dorf (außer einmal 26.1.1:
  Wagen hing am Fluss in einer Rinne fest; im zweiten Lauf kam er an). In 1.21.7 und 26.1 war der Zombie nach 12 s
  noch da (langsamer Server) → kurz03 wartet jetzt 20 s. `Chauffeur.zurueckAufDieStrasse` setzt bei wiederholtem
  Festhängen jedes Mal 4 Punkte weiter vorn auf und nie in Wasser oder Lava.

## Version 0.3.2 (veröffentlicht 26.10. nachts)

- Server-Version: Spieler mit und ohne Mod gemeinsam (Erkennung beim Anmelden), Bodyguards ohne Mod als
  Türsteher im Smoking, Trailer im README (`docs/trailer.mp4`, Vorschaubild `docs/bilder/trailer.jpg`).
  Vorher: kurz03 in 26.3 (Einzelspieler) alles OK, `vanilla_figuren.txt` mit und ohne Mod OK.

## Version 0.3.1 (veröffentlicht 26.10. nachts)

- Versionstest 0.3.0 (`kurz03.txt`, alle 17): Handy, Lieferung, Mitarbeiter, Bodyguards, Kreisel überall OK.
  Fahrt ins Dorf scheitert in 1.21.2/1.21.3 (Zeit zu knapp) und 26.1/26.1.2/26.2 (Kreuzung über einem Fluss voller
  Geländer). Fix in `StrassenBau`: Spalte neu bauen, wenn die Straße näher vorbeikommt, Geländer/Bordstein/Mast auf
  der Fahrbahn entfernen, Wasser neben der Straße abdichten (`wasserAbdichten`). Geprüft mit kurz03 (Wartezeit für die
  Fahrt jetzt 360 s): 26.1, 26.2 und 1.21.2 kommen im Dorf an, alle anderen Prüfungen OK.
- Dazu als Vorschau die Server-Version für 26.3 (README: „Server: Freunde spielen ohne Mod mit“). Klicks ohne Mod:
  `BlockBenutzenMixin` rechnet mit den echten Formen nach, worauf der Spieler zeigt (kleine Möbel sind für ihn ganze
  Barriere-Blöcke), `FigurBenutzenMixin` merkt sich Klicks auf Figuren, damit das Handy nicht zusätzlich aufgeht.

## Release

- **v0.1.0 ist veröffentlicht:** https://github.com/MoinMornhart/Minecraft-aber-du-bist-stinkend-Reich/releases/tag/v0.1.0 (17 .jar, je ~45 MB wegen der mitgelieferten Anthropic-Bibliothek).
- Tags lassen sich aus der Cloud-Sitzung nicht pushen. Deshalb startet der Release-Ablauf auch, wenn sich `.github/release-version` auf einem Branch ändert (Nummer muss zu `mod.version` passen); er legt Tag und Release selbst an.
- Update-Funktion gegen das echte Release getestet: eine Testfassung 0.0.9 bietet 0.1.0 im Chat an, die Download-Adresse liefert genau die Datei aus dem Release.
- Nach dem Release behoben (kommt mit 0.1.1): Die Mod-Version kommt jetzt aus `fabric.mod.json`. Vorher stand sie für die aktive Stonecutter-Version (26.3) fest im Quelltext und wäre bei einer neuen Nummer nicht mitgewandert.

## Offen

- Die Arbeit liegt auf dem Branch `claude/elegant-darwin-00k3xq`; ob sie in `main` kommt (Pull Request), entscheidet Philip.
- Claude-Anbindung des Butlers (`ButlerKI`) ist nur übersetzt, nicht mit echtem Schlüssel getestet.
- Original-Map fehlt weiterhin in `/vorlage` (nur zum Abgleich der Mechanismen-Liste, nie committen).
- Feinschliff: Innenräume heller; Kamera im Butler-Spieltest im Tunnel zu nah.
- Zweiter Arbeitsordner `/home/user/sr-test` (Git-Worktree) für parallele Spieltests; kann weg, wenn er nicht mehr gebraucht wird.

## Vormittag 27.10. (nach der Nachtschicht)

- **Server ohne Mod 1.21.6–1.21.8:** Standbilder (`FahrzeugAnzeige.figur`) waren schwarz, sobald die Figur etwas im Boden
  stand (Licht kam vom Fußblock). Jetzt Licht aus Fuß und Mitte (`licht(e)`), beim Laufen alle 10 Ticks nachgemeldet
  (`onEntityTrackerTick`, eigenes `ClientboundSetEntityDataPacket`). Der „fehlende“ Bodyguard war ein Szenario-Fehler
  (`sort=furthest` traf denselben zweimal) → `vanilla_figuren.txt` mit Marke `bg1`. Bedienfelder und 1.21.6/1.21.7 noch offen.
- **NoRisk „Hosted Worlds“ / LAN** (= geöffnete Einzelspielerwelt): Test `tools/ingame/lan.sh` (GAST=mit|ohne, zwei
  Bildschirme, Freund „Max“, Autotest-Zeile `offline` + `/publish true <port>`; Syntax ab 26.x ohne Spielmodus),
  Szenario `lan_nachbar.txt`. Beide Fälle bestanden (eigene Villa, James, Straße). Behoben: `ModErkennung.hatMod`
  hielt im Einzelspiel jeden für einen Mod-Spieler (jetzt nur, wenn die Welt nicht geöffnet ist: `isPublished`) – Freunde
  ohne Mod flogen mit „container_set_content“ raus. Polymer-AutoHost ist außerhalb der Entwicklungsumgebung aus
  (`enabled = DEV_ENV`) → `ServerPaket.autoHostEinschalten` setzt `config/polymer/auto-host.json` auf `enabled: true`
  (Konfig `serverRessourcenpaket`, Standard an). README-Abschnitt dazu.
- **Neue Skins** (Philip: Haare, Anzug, bei den Ladys etwas Oberweite, mit zweiter Schicht): Alle Menschen-Häute im
  Spieler-Layout (`tools/assets/spielerhaut.py`: `wuerfel`, `linke_seite` spiegelt rechts nach links). Modelle der Mod
  über `client/MenschenNetz` (Überzüge 0,25, linke Glieder eigen). James (`butler.py`): Haar-Schicht, Frack mit Revers
  und Einstecktuch, Fliege (Teil, 24,0), Frackschöße (56,16). Tänzerinnen (`taenzerin.py`, schmal): Haare mit Volumen,
  Haare über den Rücken (Jacke hinten), Top mit Tiefe + Teil „oberteil“ (24,0, angewinkelt), Feder (56,16); angezogen
  wie bisher. Mitarbeiter: linke Seite gespiegelt. Schaufensterpuppen: Tänzerinnen „slim“ (Mod und Paper), Standbild
  mit schmalen Armen. Vorher fehlten ohne Mod allen dreien linker Arm und linkes Bein.
- `test.sh` setzt den Port jetzt auch bei `WELT_BEHALTEN=1` (sonst wartete der Client auf dem falschen Port).
- **Aufzugstafel ohne Mod (alle Versionen):** Für ein normales Minecraft ist sie ein ganzer Barriere-Block;
  `BlockBenutzenMixin` rechnete mit der dünnen echten Tafel nach, der Strahl ging vorbei und traf die Lampe dahinter →
  Klick dorthin umgeleitet, das Handy ging auf. Jetzt darf erst der angeklickte Mod-Block antworten; nur wenn er nichts
  tut, geht der Klick an den fremden Block dahinter. Strahl „innen“ (Kopf steckt in einem Block) wird ignoriert.
  Autotest-Protokoll: „Autotest: Blockklick …“, „Fenster …“, „Handy-Fenster …“, „Klickfeld …“.
- **Trailer Teil 4** (0.4.1, 69 s, an `docs/trailer.mp4` gehängt, jetzt 13:09, 84 MB – GitHub erlaubt höchstens 100 MB je
  Datei, beim nächsten Teil also neu kodieren oder kürzen): `trailer4a.txt` (neue Skins, `SICHTWEITE=12 WELT_BEHALTEN=1
  test.sh`), `trailer4b.txt` (Freund ohne Mod, `CHAT_AUS=1 GAST=ohne lan.sh`), Schnitt `tools/ingame/trailer4_schnitt.py`
  (Bauzeit, Himmel beim Laden der neuen Villa und James in Max' Kopf nach „rufen“ raus), dann `video.py … "Neu in 0.4.1"`.
  README-Fotos mit den neuen Skins aus `fotos041.txt`.
- `vanilla_bedien.txt`: Flügel steht seit 0.3.5 im Wohnzimmer (Villa 40/62 = Welt −16 88 6), Bodyguards werden vorher
  entfernt. Neu: `vanilla_klickfeld.txt` (F3-Blick und Klick auf Aufzugstafel und Flügel).
- Server ohne Mod 1.21.6, 1.21.7, 1.21.8 im Spiel geprüft (Figuren, Handy, Butler, Zentrale, Tastenfeld, Musik,
  Klaviatur, Kameras, Aufzug); Kette: `scratchpad/alt_tests.sh "<versionen>" <szenario>…`.

## Nachtschicht 27./28.10. (0.4.2, noch kein Release)

- Philip (27.10. abends): Paper-Server mit schon bestehender Welt → keine Villa, kein James. Grund: Das Plugin baute die erste
  Villa nur, wenn die Welt jünger als eine Minute war. Wunsch: „muss wirklich jederzeit gehen“, auch wenn später noch ein
  Freund joint. Dazu: Bodyguards zielen nicht, einer macht alles, der andere nichts.
- **Paper `villa/Bauleitung`** (ersetzt Baustelle/Warteschlange in StinkendReichPaper): erste Villa auch in bestehenden
  Welten (neu: ohne Prüfung am Spawn; bestehend: Platz ohne Spielerbauten, Ringe um den Spawn, Schritt
  max(breite) + 2·Anblendung + 48), eigene Villa für jeden, der dazukommt – auch während gerade gebaut wird (Warteliste
  für die erste Villa; der Erste wird Herr: Karte, Teleport). Nummern und Plätze zählen wartende Aufträge mit (vorher bekamen
  zwei gleichzeitige Spieler denselben Platz). Merkdatei `plugins/StinkendReich/baustellen.json` (Aufträge, verworfene
  Plätze); ab dem Ebnen steht `y0` fest → nach einem Neustart geht der Bau an derselben Stelle weiter (ohne Messen/Prüfen).
  Fehler im Bau: bis zu 3 Versuche. `auchInAltenWelten` gibt es nicht mehr. `/villa bauen` (Auslöser wird Herr),
  `/villa auftraege`, `/villa zaehlen <nr>` (Figuren je Art auf dem Grundstück, Testhilfe).
- **Paper `Baustelle`**: Schritt PRUEFEN nach MESSEN (über dem Boden: Spalten mit `Natur.gebaut`, ab 4 → bebaut; unter dem
  Grundstück `Natur.spielerSpur` (Fackel, Werkbank, Ofen, Bett, Schild …), ab 3 → bebaut). LADEN wartet auf
  `Chunk.isEntitiesLoaded` (sonst doppelte Anzeigen beim Fortsetzen). `STINKENDREICH_BAUZEIT_MS` (Test) kürzt die Rechenzeit
  je Tick. Landminen nur auf natürlichem Boden.
- **Mod `VillaBaustelle`**: gleiche Prüfung (SpielerBloecke + `Schutz.istGebaut`/`istSpielerSpur`), verworfene Plätze bis zum
  Neustart; angefangener Bau in `stinkendreich-baustelle.json` (Weltordner) je Besitzer, geht beim nächsten Betreten weiter.
- **Bodyguards Mod**: synchronisiertes Feld ZIELT; `BodyguardModell` hebt den rechten Arm (xRot −90° + Kopfneigung, yRot =
  Kopfgier), `BodyguardRenderState` (ab 1.21.2), `PistolenLayer` lässt beim Zielen die 90°-Drehung weg. Kopf/Körper zum Ziel,
  Ziele werden zwischen den Bodyguards eines Chefs aufgeteilt, `Bodyguards.freieStelle` (Golem 1,4 × 2,7), Feststecken
  (in der Wand oder 2 × 40 Ticks kein Fortschritt) → zurück zum Chef. Ab 1.21.9: Pistole in der Hand, beim Zielen
  `ClientboundSetEquipmentPacket` mit geladener Armbrust + `item_model` goldene_pistole → Spieler ohne Mod sehen die
  Zielhaltung der Schaufensterpuppe (CROSSBOW_HOLD).
- **Bodyguards Paper**: Puppe schaut zum Ziel (Neigung), geladene Armbrust mit Pistolenbild = Zielhaltung (kein
  `swingMainHand` mehr, das bricht die Haltung ab), Ziele verteilt, freie Stellen, Feststecken wie in der Mod.
- Tests (Paper): `KEINE_VILLA=1 paper.sh paper_altwelt_vorbereiten.txt` (Haus am Spawn), dann `WELT_BEHALTEN=1` mit
  `paper_altwelt.txt` (Spawn verworfen, Villa 184 Blöcke daneben, Max/Anna gleichzeitig an zwei Plätzen),
  `STINKENDREICH_BAUZEIT_MS=1 WELT_BEHALTEN=1 … paper_neustart_a.txt` + `WELT_BEHALTEN=1 … paper_neustart_b.txt`
  (fortgesetzt, Zählung gleich: 2699 Anzeigen, 658 Klickfelder, 7 Puppen). `paper.sh` kennt jetzt `merke anfang`.
- **Falle in Minecraft-Goals (Ursache für „ein Bodyguard macht alles, der andere nichts“):** `GoalSelector` fragt `canUse`
  und `tick` nur jeden zweiten Tick ab, je nach `(tickCount + getId()) % 2`. Ein `tickCount % 10` darin kommt bei
  ungerader Nummer NIE dran. Deshalb: Zielwahl der Bodyguards im eigenen `tick()` (`zielSuchen`), Goals mit
  `tickCount %` brauchen `requiresUpdateEveryTick()` (Bodyguard-Folgen, Butler-Folgen/-Heim), Hai-Jagd zählt eigene Abfragen.
- Bodyguards jetzt wie Personenschützer (Mod und Plugin): bleiben beim Chef und schießen (4,5–24 Blöcke), Nahkampf nur, wenn
  ein Gegner näher als 5,5 kommt (bleiben dran bis 8); Ziel wird behalten, teilen sich zwei eins, wechselt der mit der
  größeren Nummer. Plugin: Minecrafts Golem-KI abgeschaltet (`Bodyguards.kiAnpassen`, Paper `MobGoals`/`VanillaGoal`),
  eigener Nahkampf (`nahkampf`, `g.attack`). Testprotokoll je Sekunde: „Autotest: Bodyguard <id> bei … Ziel … Abstand …“.
- Tests (Mod): `bodyguard_ziel.txt` (Diener mit Resistenz V für die Fotos), `nachbar_bebaut.txt` (Haus auf dem Platz, den
  die Suche in der Welt mit Saat „stinkendreich“ wählt: 584 -696 → verworfen, Villa bei 264 -376).
- **Paper: Puppe und Golem ohne Kollision** (`setCollidable(false)` für beide, auch beim Laden): Sie stehen aufeinander und
  schoben sich gegenseitig an – ein Golem, der einmal lief, glitt mit 1 Block/s endlos weiter (auch beim Zielen). Im
  Zuschauermodus sieht man unsichtbare Figuren als Schemen → Paper-Aufnahmen mit Bodyguards im Kreativmodus drehen.
- **Trailer Teil 5** (0.4.2, 39 s, „Neu in 0.4.2 – Bodyguards im Einsatz“): `trailer5a.txt` (Mod, `SICHTWEITE=12 test.sh`),
  `trailer5b.txt` (Paper, `CHAT_AUS=1 paper.sh`), Schnitt `tools/ingame/trailer5_schnitt.py`, dann `video.py`, mit ffmpeg concat
  (`-c copy`) an `docs/trailer.mp4` gehängt: jetzt 13:48, 87 MB (GitHub-Grenze 100 MB → beim nächsten Teil neu kodieren).
- `mod.version` = 0.4.2, `.github/neu.md` hat den Abschnitt 0.4.2; `.github/release-version` bleibt 0.4.1 bis Philips OK.
- Philip (27.10. abends, mehrfach): Auf der Fortschrittsseite auch „Läuft gerade“ und „Als Nächstes“ immer aktuell halten –
  `seite_neu.py` zeigt dort jetzt Mod- und Server-Punkte zusammen.

## Morgen 28.10. (0.4.2, Philip: „Mach das, dass es überall geht“ + feste Plätze für weitere Villen)

- **Mod: erste Villa auch in bestehenden Welten** (Konfig `villaInBestehendenWelten`, Standard an): `VillaPlatzierung.beimStart`
  merkt sich `ersteVillaOffen`, wenn die Welt keine Villa hat (alte Welt oder Merkdatei mit `gebaut:false`); beim ersten
  Betreten bestellt `ersteVillaBestellen` sie bei `VillaBaustelle` (Auslöser wird Herr, `ausstatten(nr==0)` setzt den
  Weltspawn). Platzwahl `ersteVillaPlaetze`: Ring 0 am Weltspawn, dann Raster mit Schritt max(breite) + 2·Anblendung + 48;
  Prüfung auf Spielerbauten wie bei Nachbarvillen. Wer während des Baus kommt, bekommt „erstewarten“ und danach die eigene
  (`ersteVillaFertig` → `beimBetreten` für alle, Straßen-Automatik startet erst dann: `Strassennetz.automatikStarten`).
  Test: `scratchpad/mod_altwelt.sh` (Konfig `villaBauen` aus → `mod_altwelt_vorbereiten.txt` baut ein Haus am Spawn → Welt
  kopieren → `villaBauen` an → `WELT_BEHALTEN=1 test.sh 26.3 mod_altwelt.txt`): Villa 0 bei -264 -56 nach 31 s, Haus unberührt,
  Villa 1 (Max) im Raster. `test.sh` kennt jetzt `merke anfang` (Chat ab Zeile 0).
- **Feste Plätze freihalten** (Mod `VillaPlatzierung.reservierterPlatz`, Plugin `Bauleitung.reservierterPlatz`): Rasterplätze
  der Ringe 1–2 um Villa 0 (samt Rand für Anblendung und Zufahrt) sind für Straßen gesperrt (`StrassenBau.geschuetzt`), außer
  das Ziel der Straße liegt dort (`zielBei`, 48 Blöcke). Plätze mit Straße werden bei der Suche schon mit diesem Rand verworfen.
  Mod geprüft: Dorfstraße führte am Platz von Max vorbei, Max' Villa entstand genau dort.
- **Plugin: Straßen zweigen ab** (`Strassen.starten`): vom nächsten Punkt einer fertigen Straße (nicht auf Grundstücken/freien
  Plätzen), wenn er 50 Blöcke näher am Ziel liegt als der Anfang an der Spawn-Villa. Vorher fingen alle Straßen am selben Punkt
  an, die vierte lief neben der dritten her und blieb mit „andere Straße darüber“ hängen. `strassen.json` speichert `eltern`
  (Villa-Nummer der Elternstraße, -1 = Spawn-Villa) und `abzweig`; `Strassen.strecke(nr)` = ganze Strecke ab Spawn-Villa,
  `fahrweg(von, nach)` = zurück bis zur Gabelung, dann hin (James als Chauffeur). Baustelle nennt im Protokoll den Block, der
  einen Platz sperrt („z. B. … bei x y z“). Test: `CHAT_AUS=1 paper.sh paper_plaetze.txt` (vier Freunde nacheinander),
  danach in derselben Welt `WELT_BEHALTEN=1 CHAT_AUS=1 paper.sh paper_plaetze_fahrt.txt` (James fährt Otto → Max über zwei
  Abzweige, 964 Punkte, 104 s) und `paper_plaetze_bilder.txt` (Abzweige schräg von oben, `/villa strassenblick <anteil>
  <höhe> <villa>`). Der Platz -56 -376 in dieser Testwelt hat einen Plünderer-Außenposten (Zaun aus Schwarzeiche) → wird zu
  Recht verworfen.
- **Straßenbau robuster (Mod `strasse/StrassenBau`, Plugin `strasse/StrassenBau`, gleiche Regeln):**
  1. Keine Schleifen: erst nur Richtungen bis 100° vom Ziel weg; zweiter Durchgang (nur wenn alles zu ist) auch ±105/135°.
  2. Weitblick `fernGesperrt`: 12–48 Blöcke voraus über die ganze Breite (Mitte und ±(HALB+2)) auf freie Plätze und
     Grundstücke (+25 Kosten) – vorher fuhr die Straße frontal oder an der Kante auf einen freien Platz und kam nicht herum.
  3. Gefahrene Richtung prüfen: gewählt wird `frei`, gefahren gedrosselt 6°/Schritt; wäre die gedrosselte versperrt, sofort
     `frei` nehmen (sonst rutschte sie in weiten Kurven in eine Sperre).
  4. Zielhöhe: Straßen zu Villen kennen die Fahrbahnhöhe am Ende der Zufahrt (`zielY`/`zielHoehe`, gelesen am Zufahrtsende,
     nicht 3 Blöcke dahinter!) und gehen rechtzeitig darauf (ein Block auf drei); dort sperrt die Zufahrt nicht als
     „andere Straße darüber“. Vorher kreisten Straßen um ein Ziel in anderer Höhe.
  5. Anfahrt von vorn: Wegpunkt 10 Blöcke südlich des Ziels (Plugin: Wegpunkt mit Umkreis, Mod: `anfahrt`), entfällt,
     sobald die Straße auf Höhe des Ziels ist. Bis 8 Blöcke vor dem Ziel gilt „kein freier Weg“ als „am Ziel“.
  6. Abzweige nicht dicht (12 Blöcke) an freien Plätzen (`naheFreiemPlatz` / `abzweigErlaubt`).
  Mod-Test `tools/ingame/test.sh 26.3 mod_plaetze.txt` (neue Welt, vier Freunde, wartet auf alle vier Villen-Straßen):
  Lauf 6 (07:29) Dorf (bis Dorfrand), Mine, Netherportal, Endportal, Lena, Otto, Anna am Ziel, Max 6 Blöcke davor (jetzt
  „am Ziel“). Plugin-Lauf 6 (07:32): alle vier am Ziel. Achtung: `WELT_BEHALTEN=1 test.sh` startet von der Ausgangswelt
  `run/testwelt`, nicht vom letzten Spielstand (`run/saves/testwelt`).
- **Plugin: James bleibt erreichbar** (`butler/Butler`): Sein Chunk bleibt per Plugin-Ticket geladen, Ort in
  `plugins/StinkendReich/butler_orte.json` (ohne Eintrag: Halle). Vorher nach einem Neustart „nicht erreichbar“, solange
  niemand bei seiner Villa war. Fahrzeugsitze sind nicht persistent: Wer beim Stoppen darauf sitzt, verschwindet → beim
  Stoppen steigt James aus (`Butler.beenden` in `onDisable`), und fehlt seine Figur 30 s bei vorhandenem Körper, entsteht
  sie neu (Doppelte werden entfernt). Im Spieltest schreibt `Butler.sagen` „Autotest: James zu …“ ins Protokoll.
- Testfallen: Ein Spieler, der im Überlebensmodus nach `/tp` stirbt, bleibt in der Paper-Testwelt tot gespeichert
  („Attempt to teleport removed player“) → Spielerdatei in `build/paper-test/world/players/data/` löschen. In Szenarien
  erst Kreativmodus, dann teleportieren.
- **Trailer Teil 6** (0.4.2, 33 s, „Neu in 0.4.2 – Straßen zu allen Freunden“): `trailer6.txt` in der Welt aus
  `paper_plaetze.txt` (`STINKENDREICH_CHAUFFEUR_ALLEIN=1 WELT_BEHALTEN=1 CHAT_AUS=1 paper.sh`). Mit
  `STINKENDREICH_CHAUFFEUR_ALLEIN` (nur im Autotest) fährt James ohne Fahrgast, wartet 3 s vor der Abfahrt und zieht eine
  unsichtbare Kamera (Rüstungsständer, Marke `stinkendreich_kamera`) weich hinter der Limo her; der Zuschauer hängt sich
  per `/spectate` daran (durch James' Augen sah man nur Dach und Säulen, F5 im Fond landete im Dach). Schnitt
  `tools/ingame/trailer6_schnitt.py` (Fahrt 4×), `video.py`, dann wegen der schnellen Fahrt neu kodiert
  (`-crf 28 -maxrate 1300k -bufsize 2600k`, sonst 15 MB statt 5 MB) und mit ffmpeg concat (`-c copy`) angehängt:
  `docs/trailer.mp4` jetzt 14:21 und 92 MB (GitHub-Grenze 100 MB → beim nächsten Teil den ganzen Trailer neu kodieren).
- Platte war voll (alte Rohvideos in `build/ingame/26.3` und im Scratchpad gelöscht). Die Worktrees `/home/user/sr-test`,
  `sr-dusche`, `sr-hai` belegen zusammen mehrere GB; `sr-test` hat alte, nicht committete Änderungen (nicht ungeprüft löschen).

## Bedrock-Spieler über Geyser (28.10., 0.4.3 geplant, Philip: „auch bei Bedrockspielern mit dem Geyser-Plugin“)

- Geyser 2.12 (Java 26.3): Dialoge → Bedrock-Formulare, Schaufensterpuppen ok, aber **keine Anzeige-Figuren** (item/block
  display) → alle Möbel, Türen, Autos, Mod-Blöcke fehlten. Lösung im Paper-Plugin, Paket `paper/.../bedrock/`:
  `Bedrock` (Start nur mit Plugin „Geyser-Spigot“ und Config `bedrock.an`), `BedrockPaket` (baut beim Start aus dem Java-Paket
  `plugins/StinkendReich/bedrock/stinkendreich.mcpack`: 839 Modelle als Geometrie je Textur-Teil, eine Figur
  `stinkendreich:anzeige` mit Render-Regeln je Teil-Platz/Material, Variante = Modell; Animation `lage` legt Drehung/
  Verschiebung/Maßstab aus Eigenschaften `stinkendreich:rx…sz` auf Knochen sr_y > sr_x > sr_z; Gegenstands-Bilder
  `item_texture.json`; Töne `sound_definitions.json` unter Java-Namen; `sr_varianten.json` für Werkzeuge), `Modellwandler`
  (Achsen wie Blockbench: x gespiegelt, Drehung (−x, −y, z), oben/unten Ausschnitt umgedreht; die 180°-Drehung der
  Bedrock-Figur und die der Java-Item-Anzeige heben sich auf), `Grundmodelle` (cube, Türen, item/generated selbst nachgebaut),
  `Lage` (Ry(−Gier)·Rx(Neigung)·Linksdrehung → YXZ-Euler, Bedrock-Werte x: b, y: −a, z: −c, Verschiebung (x, y, −z)·16),
  `Zwillinge` (leere TextDisplay je Anzeige-Figur, nur für Bedrock-Spieler sichtbar, nur solange einer online ist, ziehen mit),
  `GeyserAnbindung` (einzige Klasse mit Geyser-API: Paket, Figur, Eigenschaften, `ServerSpawnEntityEvent` → eigene Figur,
  eigene Gegenstände (Papier/Armbrust/Truhe/Netherit + item_model), `SessionSkinApplyEvent`).
- Häute: Geyser feuert das Haut-Ereignis nur bei Profilen mit Mojang-Haut-Verweis → Puppen bekommen zusätzlich einen
  Verweis auf eine Standardhaut (Java nutzt weiter den Haut-Aufsatz), das Plugin setzt dann die echte Haut.
- Test: `tools/ingame/bedrock/einrichten.sh` (bedrock-protocol, raknet-native-Bibliothek für den Kernel freigeben), dann
  `GEYSER=1 tools/ingame/paper.sh tools/ingame/szenarien/bedrock_start.txt` (Geyser von Modrinth, RakNet, ohne Xbox-Anmeldung,
  Test-Client „Bob“, Protokoll `build/ingame/paper/logs/bedrock.log`, Zeilen `bedrock <befehl>` / `bbis`). Ergebnis 09:10: Paket
  kommt an, ~2350 Ersatzfiguren mit Variante/Lage, Handy- und James-Formular, `stinkendreich:sr_handy` im Inventar, Häute
  butler/taenzerin_0–3. Vorschau `tools/paper/bedrock_vorschau.py <mcpack> <png>` (Java links, Bedrock rechts: gleich).
- Paper 26.3 hat standardmäßig eine Whitelist (Testserver jetzt `white-list=false`); Bedrock-Spieler heißen „.Name“.
- Offen: Philips Test auf echtem Bedrock (Ausrichtung, Licht, Leistung bei ~2500 Figuren), Töne im Spiel, Fabric-Server-Version.
- GitHub meldet beim Push: Repo liegt jetzt unter `pmornhart/Minecraft-aber-du-bist-stinkend-Reich` (Umleitung klappt, auch für
  die Update-Funktion).

## Runde 0.4.3 (28.10. nachmittags, Release 18:07 nach Philips „Release raus“)

- Limo zweiter Entwurf nach echten Stretch-Limos (`stretchlimo()` in fahrzeuge.py: lange Haube, flache Zelle mit getöntem Band
  `glas_getoent`, kurzes Heck, Frontscheibe ab z −44 mit −0,75 rad, Dach −29..41, Vordersitze z −31); Sitze in `Autotyp`
  und Paper `Fahrzeuge` {±0.41, 0.27, 1.72}, hinten {±0.59, 0.36, 0.5/−1.0}. AutoRenderer zeichnet durchscheinend
  (entityTranslucent, drei Versionsvarianten). Philip: „Limo passt“. Supersportwagen im Lamborghini-Stil.
- Kein Fallschaden im Auto (`AutoEntity.tick` → `resetFallDistance` für Wagen und Mitfahrer; Paper `Fahrzeuge.beimSchaden`).
- Navi nach „Limo rufen“ nimmt den vorhandenen Wagen (`Handy` „fahr“, `FahrAuftrag.wagenFinden`, Paper `Butler`).
- Fernbedienung vom Tisch in die Hand (`FernbedienungBlock.nehmen`, `UseBlockCallback` für Klicks auf den Tisch darunter),
  steuert den angeschauten Fernseher; Paper `block/Fernbedienung`. Kein Eis auf Villengrundstücken (`KeinEisMixin`,
  `Graben.enteisen`, Paper `Graben.beimFrieren`). Bodyguards: Arm immer gehoben, Nahkampf ohne Pistole. Wand neben der
  Personalküchentür (villa.py Zeile ~457).
- Paper-Ressourcenpaket nach Fahrzeugänderungen mit `tools/paper/daten_holen.sh` neu holen (sonst alte Modelle).
- Trailer Teil 7 (`trailer7.txt`, `trailer7_schnitt.py`), ganzer Trailer neu kodiert (crf 28, maxrate 700k, Ton 96k):
  15:18, 60 MB. Tests: `runde043*.txt`.

## Runden 0.4.4–0.4.6 (28.10. abends)

- 0.4.4 (veröffentlicht 19:14): **Straßenkehrer** (`strasse/Strassenkehrer`, Mod und Paper: alle 40 Ticks herrenlose Gegenstände
  ≥ 100 Ticks alt in 11 Blöcken um Straßenpunkte weg, nie auf Grundstücken). **Limo in die Garage** (`auto/Garage`: Stellplatz
  aus Marke „stellplatz“ + FUHRPARK, Chunk 100 Ticks erzwungen, doppelte Wagen weg; `FahrAuftrag` Phasen AUSSTEIGEN/HEIMFAHRT,
  außer Sicht → `Garage.einparken`; `AnrufAuftrag` lädt die Garage vor; Paper `Chauffeur` HEIMFAHRT). Paper-Ressourcenpaket
  pro Spieler über `getVirtualHost()`. `WeltVonPaper` (Fabric-Server auf Paper-Welt: world_gen_settings.dat kopieren).
  Inkognito merkt sich die Sicherheitsstufe (`stufe_vor_inkognito`) und stellt sie beim Auftauchen wieder her (Tore blieben offen).
- 0.4.5: Inkognito in der Festung entfernt Laserstrahlen vor dem Einpacken (`Inkognito.strahlenWeg`). Release-Lauf scheiterte
  mit 403 „Resource not accessible by integration“ beim Anlegen des Release (Bauschritte alle grün, Ursache unklar, direkt
  nach 0.4.4) → in 0.4.6 enthalten.
- 0.4.6: Ressourcenpaket für Paper standardmäßig von GitHub (`ressourcenpaket.quelle: github`, Release-Datei
  `stinkendreich_paket-<version>.zip` aus `tools/paper/paket_bauen.sh`, ohne Prüfsumme; `server` = eingebauter Webserver).
  Zugangskarten personalisiert (`Zugang.karte`, `truhenkartenAusstellen`, `namenNachtragen`). **Gemeinsam fernsehen**
  (`FernseherEntity`: Zustand `W<pos>;<bereit>/<alle>`, Zuschauer = Mod-Spieler in 32 Blöcken, Paket „fernseherbereit“,
  höchstens 45 s warten; Client lädt vor mit `VideoAbspieler.halten`). Pause/Weiter: altes Bild bleibt, bis das neue da ist
  (`FernseherVideos.VORHER`). Tests `lan_fernseher.txt` (`STINKENDREICH_TESTVIDEO_LANGSAM=Max`), Testbefehl `/villa fernseher`.
- Offen: Trailer-Teil 8 + README-Abschnitt gemeinsam fernsehen; Zugangskarte, GitHub-Paket und WeltVonPaper auf echtem Server.

## Runde 0.4.7 (29.10. abends, noch kein Release)

- **Fernbedienung in der Hand:** `mixin/client/HandMitFernbedienungMixin` zeichnet in der Ich-Sicht den Arm dazu
  (bis 1.21.8 `ItemInHandRenderer.renderArmWithItem` mit MultiBufferSource, 1.21.9–26.2 mit SubmitNodeCollector, ab 26.2
  heißt die Methode `submitArmWithItem` – beide Namen im @Inject; 26.3 `FirstPersonHandsAndItemsRenderer.submitArmWithItem`
  mit PlayerRenderState). Eigenes Item-Modell `models/item/fernbedienung.json` aus deko.py (`HAND`, `hand_modell`): Ich-Sicht
  [-10, 8, 0] / [-1.5, 2.5, -4.5], dritte Person [90, 0, 0] / [0, -1.5, 0.5], Maßstab 0,6. Prüfen am Rüstungsständer
  (`fernbedienung_hand.txt`): `tp … facing` rechnet von den Füßen, nicht von den Augen.
- **Wegweiser:** `strasse/WegweiserForm` (ohne Minecraft, auch im Paper-Plugin über `include` in paper/build.gradle.kts):
  Fichtenpfahl, Pfeilbretter aus Schwarzeiche + Spitze (Quadrat 45°), goldene Text-Anzeigen auf beiden Seiten; `FORM` in der
  Signatur. Mod `strasse/Wegweiser` (Befehle `summon block_display/text_display`, Text ab 1.21.5 als NBT, davor JSON;
  Zufahrt vor dem Kreisel + je Abzweig: neue Straße, geradeaus, Heimweg; gesetzt wenn `Kompat.ganzGeladen`, Merkliste im
  Straßennetz-JSON), Paper `strasse/Wegweiser` (`wegweiser.json`). Testhilfen `/butler wegweiser <nr>`, Paper `/villa wegweiser <nr>`.
  Test `wegweiser.txt` (Mod), `paper_wegweiser.txt` (Paper).
- **Navi:** Ziel → Seite REISE („Limo oder Heli?“) → `fahr|Ziel` bzw. `flieg|Ziel`; sitzt man schon in Limo/Hubschrauber, ohne
  Nachfrage. „Limo/Hubschrauber rufen“ entfernt (Mod-Handy, `HandyDialog`, Paper `Handy.naviSeite`). `HeliBereitstellung`
  holt den nächsten freien Hubschrauber (bis 512) neben den Spieler, wenn keiner in 96 Blöcken steht (sonst tickte James im
  ungeladenen Bereich nicht). Test `navi047.txt`: Abholung und Flug nach Hause geprüft.
- **Rollläden** neben der Haustür (einzelnes Feld) standen quer: villa.py `fluegel` (Achse z nur bei z0 != z1), bestehende Villen
  beim Schalten (`TorsteuerungBlock.einzelnAusrichten`, Paper `Tore.setzeOffen`). Test `rollo047.txt`.
- `mod.version` = 0.4.7, README und `.github/neu.md` haben 0.4.7; `.github/release-version` bleibt 0.4.6 bis Philips „Release raus“.
- Trailer Teil 8 (29.10. 19:06, 68 s): `trailer8.txt` (neue Welt: Fernbedienung, Rollläden, Navi/Hubschrauber), Wegweiser-Nachdreh
  `trailer8d.txt` (Spielstand nach `run/testwelt` kopiert, `WELT_BEHALTEN=1`; in der ersten Aufnahme war das Gelände noch nicht
  geladen), Schnitt `trailer8_schnitt.py`, crf 28/maxrate 700k angehängt: `docs/trailer.mp4` 16:26, 66 MB. Paper-Wegweiser zeigen
  das Ziel ohne „Straße zur …“.

## Runde 0.4.8 (07.11., noch kein Release)

- **Neue Cloud-Umgebung einrichten:** Java 25 nach `/opt/jdk25` (Adoptium), `apt-get install xdotool libegl1 libgl1 libgl1-mesa-dri
  libglx-mesa0 libegl-mesa0` (ohne EGL startet 26.3 nicht: „Could not load EGL library“), `pip install nbtlib numpy pillow`.
  Maven Central antwortet oft mit 429 → `~/.gradle/init.d/spiegel.gradle` (Google-Spiegel, nur lokal). Gradle mit
  `JAVA_HOME=/opt/jdk25`. `bis`-Muster in Szenarien: „“ sind mehrere Bytes, `.` passt nicht → `.*` nehmen.
- **Nachrüstung bestehender Villen** (`villa/VillaNachruestung`, Listen in `data/stinkendreich/villa_nachruestung/<name>.json`
  aus `tools/villa/nachruestung.py <alte villa.nbt> <name>`): jede Liste einmal je Villa (Smart-Marke `nachruestung_<name>`),
  nur wo noch der alte Block steht, nicht während Inkognito. Vor einer Bauplan-Änderung die alte villa.nbt sichern! Liste
  `048_bar_kuppel` (373 Blöcke). Test: mit alter villa.nbt starten → „371 Blöcke getauscht, 0 … belassen“.
- **Villa:** Bar mit Personaleingang an beiden Enden (Regale nur z 70, Holzboden), VIP-Eingang mit Goldrahmen x 49–52,
  rotem Teppich, Bambus, `neon_vip_mitte` (deko.py: Modell um 8 px versetzt, Block bei x 50 = mittig über x 50/51). Kuppel
  des Seerestaurants unten zwei Lagen senkrecht (vorher Glasstufen am Tisch), Tunnelglas nicht mehr in der Kuppel.
- **Wegweiser:** Schilder unter 40° Unterschied parallel (`WegweiserForm.entzerren`), Ziel als Übersetzungsschlüssel
  `stinkendreich.wegweiser.*` mit `fallback` (Namen der Straßen sind serverseitig oft englisch), FORM 3.
- **Straßen:** `Treppenbau` (Mine: Schiene/Holzzaun, Endportal: Rahmen sonst Steinziegel, Netherportal: Portal; 2 breit,
  Wendel bei mehr Tiefe als Weite; `treppen` im Straßennetz-JSON). Kein Unterbau/Pfeiler im Lichtraum einer anderen
  Straße (`ueberFahrbahn`), keine Straße 1–4 Blöcke über einer anderen (`frei`). Straßenwart in `Strassenkehrer.wasserFernhalten`
  (Flüssigkeit über der Fahrbahn weg, Zuläufe zumauern, Pfeiler/Geländer vom Lichtraum räumen; nur in Spielernähe, nie auf Grundstücken).
- **Autos:** `AutoschluesselItem` (Wagen-UUID in CUSTOM_DATA, Funk 48), `AutoMenue` (ChestMenu 9x1 als Knöpfe; `clicked` ab
  26.1 mit `ContainerInput`, davor `ClickType`), Kofferraum 27 Plätze, Besitzer = erster Schlüssel. `AbschleppAuftrag`
  (HOLEN → ANFLUG → ANHAENGEN → TRAGEN → LANDEN, Log „Abschleppen: …“). Garage löscht nie Wagen mit Besitzer/Kofferraum.
  Testhilfe `/butler auto menue|schluessel|zu|auf|kofferraum|heim`.
- **Hubschrauber:** `HeliBereitstellung` setzt ihn 90 Blöcke entfernt in die Luft (James am Steuer, `rotorAn`), er fliegt sichtbar
  her. `HubschrauberEntity.chunkHalten`: solange James fliegt, ist sein Chunk zwangsgeladen (sonst blieb er bei kleiner
  Simulationsweite stehen – Tests haben 5). Handy-Aufträge warten bis 10 s, wenn James erst herbeigeholt wird (`NACHHOLEN`).
  `ButlerBefehle.besserFliegen`: weit weg vom Netz (> 80) und Ziel > 150 → Hubschrauber statt Limo.
  `ButlerEntity.teleportiereNahe` steigt erst aus (als Mitfahrer setzte ihn der Hubschrauber sofort zurück auf den Sitz –
  wer James rief, während er noch zum Landeplatz flog, bekam keinen Flug). Handy-Fehler landen im Protokoll („Handy: …“).
- Szenarien: F5 schaltet Ich-Sicht → hinten → vorn; nach einer Aufnahme in dritter Person zweimal F5, sonst schauen
  folgende Kamerabilder in den Himmel. Trailer Teil 9: `trailer9.txt` (a, b), Nachdreh `trailer9b.txt` (c–f),
  Schnitt `ROH=<ordner mit a/b> python3 tools/ingame/trailer9_schnitt.py`.
- **Chauffeur:** `chauffeurTempo` 100 (Konfig-Stand 1 hebt alte 60 an), SICHT 20, Kurven bis 44 Blöcke voraus mit Bremsweg.
- **Tänzerinnen:** Anker fährt bei Inkognito mit (`TaenzerinEntity.hoeheVerschieben`), falsche Anker suchen sich in derselben
  Spalte den Clubboden bzw. die Tanzstange (`ankerPruefen`, alle 100 Ticks).
- **Fernseher:** Twitch-Kanäle (`tw:name`) und -Aufzeichnungen (`twv:nummer`) über yt-dlp, `FernseherEntity.adresse`.
- **Eine .jar für alle Versionen:** `tools/alle_versionen.py build/libs/<ver>` → `stinkendreich-<ver>+alle.jar` (Kennung
  `stinkendreich_alle`, alle Fassungen per Jar-in-Jar, Bibliotheken einmal). Fabric wählt die Fassung mit passender
  Minecraft-Bedingung; geprüft mit echten Fabric-Servern 1.21.1 und 26.3 (aus 0.4.7-Dateien). Release-Ablauf baut und hängt
  sie an, `UpdatePruefer` bietet bei `stinkendreich_alle` wieder „+alle.jar“ an.
- Paper-Reste entfernt (Szenarien trailer5b/trailer6, Kommentare). Tests: `runde048.txt` (Nachrüstung, mit alter villa.nbt),
  `runde048b.txt`, `runde048c.txt`.

## Runde 0.5.0 (08.11.)

- **Treppen neu** (`strasse/TreppenPlan` ohne Minecraft, Test `tools/treppen_plan_test.sh`: 20.000 Zufallsziele): 3 breit, Absatz
  vor und nach jeder Ecke (sonst ragt der neue Gang in den Boden der vorletzten Stufe), kein Zickzack, keine 180°-Wende,
  zuerst 4 Stufen in Straßenrichtung. `Treppenbau` in Phasen SUCHEN, HUELLE, RAEUMEN, DICHTEN (26 Nachbarn: Flüssigkeit,
  Löcher, Sand, Pflanzen, Naturblöcke → Quarz; am Ziel/Straßenende und am offenen Himmel bleibt Luft), SCHMUCK; `pruefen()`
  (versperrt, ohne Boden, ohne Marmor, Wasser, Ziel). Treppenwart `pflegen()` in Spielernähe, Pläne als `treppenplaene` im
  Straßennetz-JSON, `STIL` 3. Befehl `/butler treppenpruefen`. Test `treppe050.txt` (Mine 97, Endportal 55 Stufen OK).
- **Butler:** Chunk-Halten 3 × 3 per `setChunkForced`, jeden Tick nachgeführt (`updateChunkForced` ließ Figuren nicht ticken;
  ein einzelner Chunk reichte nicht, nach Teleport stand er im Nachbarchunk still). `ButlerVerwaltung.mitButler` (Aufträge
  warten auf einen nicht geladenen James; Ruf mit `danach`, kein doppelter Ruf, 15 s + Warten auf Figuren), Handy ohne
  NACHHOLEN, `ArbeitsZiel` ohne JUMP, `AnrufAuftrag` zählt als Fahrt, Folgen/Bleiben, eigene Villa (`Umgebung.eigeneVilla`),
  Ofen speichert Phase, Sprache vor Claude. Testprotokoll „Autotest: James bei …“. Test `ferne050.txt` (700 Blöcke weg).
- **Fahrzeuge:** Synchronisiertes `AM_STEUER`; `AutoEntity.fahrenderButler()` – ohne Auftrag steuert der Spieler (Leertaste im
  Hubschrauber, Philip 08.11.). Test `heli050.txt`.
- **Sicherheit übers Handy:** `SmartHome.ANLAGEN`/`wahl()` (Smart-Marken `anlage_<name>` = auto/an/aus), Seite SICHERHEIT
  (`HandyBildschirm`, `HandyDialog`), Stufe 0–4 (auch Festung) und Alarm aus, nur Eigentümer.
- **Abschleppen:** Phase ABSETZEN auf dem Teppich vor der Haustür (`FahrAuftrag.vorDerTuer`), Nachricht aufs Handy
  (`Handy.nachricht`, auch beim Einparken in der Garage).

## Paper-Plugin entfernt (30.10., Philip: „lösch mal den Paper-Zeug, also das Plugin, nur Mod“)

- Gelöscht: `paper/`, `tools/paper/`, `tools/ingame/paper.sh`, `tools/ingame/bedrock/`, alle `paper_*`/`bedrock_*`-Szenarien,
  `dev/PaperDaten`, Paper-Fotos; Release-Ablauf baut nur noch die 17 Mod-Dateien; README ohne Paper-Abschnitt.
  Bleibt: Fabric-Server mit Polymer (Freunde ohne Mod), `WeltVonPaper` (alte Paper-Welten mit der Mod weiterspielen).
  Ältere Releases (bis 0.4.7) haben das Plugin noch als Datei. Abschnitte zum Paper-Plugin weiter oben sind nur noch Geschichte.
- `mod.version` = 0.4.8, `.github/neu.md` hat 0.4.8; `.github/release-version` = 0.4.8 (Release angestoßen 07.11.).

## Nächster Schritt

**Release 0.4.9 veröffentlicht** (07.11. 20:00 nach Philips „auf auf“, Lauf 42403170224, 17 Dateien + +alle.jar: https://github.com/pmornhart/Minecraft-aber-du-bist-stinkend-Reich/releases/tag/v0.4.9): Luxus-Treppen (Quarz/Gold, `Treppenbau.STIL` 2 überbaut alte), Bodyguards fahren mit (`BodyguardEntity.mitfahren`, schießen aus dem Wagen, kein Nahkampf als Mitfahrer), Heimfahrt bis vor die Haustür (`FahrAuftrag.vorDerTuer`, `umfahren` außen ums Grundstück, Heimweg zur Garage über den Teppich). Tests `treppe049.txt`, `runde049.txt`; Testhilfe `/butler treppenblick <ziel> <anteil>`.

**Release 0.4.8 veröffentlicht** (07.11. 18:41 nach Philips „naus“ = Release raus, Lauf 42396626798): 17 Mod-Dateien + `stinkendreich-0.4.8+alle.jar` (109 MB): https://github.com/pmornhart/Minecraft-aber-du-bist-stinkend-Reich/releases/tag/v0.4.8. Die Sammel-Datei aus dem Release startet auf Fabric-Servern 1.21.1 und 26.3 mit der jeweils passenden Fassung. Offen: Philips Test von Twitch, Pfeilern und Tänzerinnen in seiner Welt; Server ohne Mod für 1.21–1.21.5.

**Release 0.4.7 veröffentlicht** (29.10. 19:16 nach Philips „Release raus“, 17 Mod-Dateien + Paper-Plugin + `stinkendreich_paket-0.4.7.zip`: https://github.com/pmornhart/Minecraft-aber-du-bist-stinkend-Reich/releases/tag/v0.4.7). **Release 0.4.6 veröffentlicht** (29.10. 07:11, 17 Mod-Dateien + Paper-Plugin + `stinkendreich_paket-0.4.6.zip`: https://github.com/pmornhart/Minecraft-aber-du-bist-stinkend-Reich/releases/tag/v0.4.6). Als Nächstes: Trailer-Teil 8 + README (gemeinsam fernsehen). **Release 0.4.3 angestoßen** (28.10. 18:07, Lauf 40742187650). **Release 0.4.2 veröffentlicht** (28.10. 09:34, 17 Mod-Dateien + `stinkendreich_paper-0.4.2+paper26.3.jar`: https://github.com/pmornhart/Minecraft-aber-du-bist-stinkend-Reich/releases/tag/v0.4.2; Philip: „Release raus“; `.github/release-version` = 0.4.2, Lauf 40664348070; Notizen in `.github/neu.md` inkl. Bedrock-Vorschau und Trailer 6). Vorher: **0.4.2 war fertig** (28.10. 08:12): alle 17 Versionen übersetzen, Paper-Plugin baut, Tests bestanden (Paper: bestehende Welt, Freunde gleichzeitig, Neustart im Bau, Bodyguards, vier Freunde mit Straßen über Abzweige, Chauffeur über zwei Abzweige; Mod: erste Villa in bestehender Welt, Nachbarvilla auf bebautem Platz, Bodyguards, vier Freunde mit Straßen), README und Trailer (Teil 6) neu. Danach: Server ohne Mod für 1.21–1.21.5 (Truhen-Menüs).

**Release 0.4.1 veröffentlicht** (27.10. 12:05, 17 Mod-Dateien + `stinkendreich_paper-0.4.1+paper26.3.jar`: https://github.com/MoinMornhart/Minecraft-aber-du-bist-stinkend-Reich/releases/tag/v0.4.1). Vorher geprüft (27.10. 11:45):
alle 17 Versionen übersetzen, Paper-Plugin baut, README und Trailer (Teil 4) neu. Offen: YouTube im Fernseher
bei Philip bestätigen (Log-Zeilen mit „yt-dlp“), Server-Version ohne Mod für 1.21.6–1.21.8 fertig prüfen (Bedienfelder,
1.21.6/1.21.7; Figuren in 1.21.8 hell), neue Skins ohne Mod (Tänzerinnen) und im Paper-Plugin ansehen, danach
1.21–1.21.5 (Truhen-Menüs). Fortschrittsseite am Morgen des 27.10. aufgeräumt (alte Punkte im Archiv, Paper-Plugin in
vier Bereichen mit ausgewählten Bildern).
