# AmpyPad ✒️

Blocco note Android della famiglia Ampy (stile AmpyCam, AmpyRocket, AmpyRAW Cyberdark): **scrivi, disegni e dipingi sullo
stesso foglio**, organizzi le idee in **blocchi** ed esporti in **PNG** e **PDF**.
Java puro, nessuna libreria esterna. Android 8 → Android 17.

## Scarica
Releases → `latest` → `AmpyPad.apk` (oppure Actions → ultimo build → artifact `AmpyPad-APK`).

## Testo + pennello in un'unica funzione
In basso scegli **Aa TESTO** o **✎ PENNELLO**: è lo stesso foglio, cambia solo cosa fa il dito.

| Punta del pennello | Cosa fa |
|---|---|
| **Penna** | tratto che segue velocità e pressione (stilo) |
| **Stilografica** | pennino calligrafico a 45° |
| **Matita** | grafite granulosa |
| **Pennarello** | colore pieno e uniforme |
| **Evidenziatore** | trasparente, da passare sopra al testo |
| **Acquerello** | pittura morbida che sfuma |
| **Aerografo** | spruzzo di particelle |
| **Gessetto** | polvere di gesso (perfetto sulla Lavagna) |
| **Neon** | luce al neon Ampy |
| **Arcobaleno** | colori che scorrono lungo il tratto |
| **Timbri** | emoji lungo il tratto |
| **Gomma** | cancella il disegno (anche con il tasto gomma dello stilo) |

Dimensione, opacità, colore (con tavolozza), annulla / ripristina, cancella tutto (con ANNULLA).

## Blocchi di organizzazione
Testo, Titolo, Sottotitolo, Intestazione, Elenco puntato, Elenco numerato, Cose da fare (☑ con effetto),
Citazione, Riquadro con emoji, Codice, Separatore.
- scrivi **/** su una riga vuota per il menu dei blocchi, oppure **＋**
- scorciatoie: `# ` `## ` `### ` `- ` `1. ` `[] ` `> ` `! ` ` ``` ` `---`
- tieni premuto **⠿** e trascina per riordinare; tocca **⠿** per colore del blocco, rientro, allineamento, duplica, elimina
- Invio divide il blocco, Backspace all'inizio lo unisce al precedente
- grassetto, corsivo, sottolineato, barrato, **colore del carattere**, evidenziatore del testo

## La piuma
Mentre scrivi (o dipingi) una piuma resta sollevata sul cursore e lascia una scia; quando smetti **si posa sul foglio e scompare**
con un effetto: scintille, gocce d'inchiostro, piume, polvere magica, petali, coriandoli, bolle, glitch neon.
Icona (piuma d'oca, stilografica, pennello, matita, bacchetta magica), colore, dimensione, attesa prima di posarsi, suoni.
In modalità pennello diventa un pennello intinto nel colore scelto.

## Fogli
- **14 stili predefiniti**: Quaderno, Cyber Neon, Pergamena, Lavagna, Notte stellata, Carta kraft, Blueprint, Sakura,
  Acquerello, Festa, Blood Moon, Minimal, Spartito, Lino (cambiano carta, righe, inchiostro, carattere, piuma ed effetto)
- motivo: bianco, righe, quaderno con margine, quadretti, puntini, millimetrata, pentagramma — il testo si appoggia sulle righe
- colore del foglio, delle righe, dell'inchiostro e della piuma (si apre la **tavolozza**: quadrato colore, tinta, HEX, 40 colori, armonie, recenti)
- texture: carta, pergamena, acquerello, lavagna, lino · 8 caratteri · dimensione
- **nuovo foglio con animazione**: sfoglia, scivola, dissolvi, caduta sulla pila, vortice, glitch neon
- quaderni, fogli fissati, ricerca nel testo, sposta / duplica / elimina

## Esporta
PNG (anche ×2 e solo disegno trasparente) in `Immagini/AmpyPad`, PDF A4 del foglio o di **tutto il quaderno** in
`Download/AmpyPad` (testo vettoriale), condivisione, backup e importazione `.json`.

## Funzioni extra
- **Simmetria**: specchio, 4 quadranti, radiale ×6 / ×8, **caleidoscopio ×12** per mandala
- **Linea dritta**: tieni fermo il dito alla fine del tratto
- **Due dita** per scorrere mentre dipingi
- **Disegno sotto o sopra il testo**
- **Dettatura vocale** in italiano
- **Modalità concentrazione** (solo il foglio)
- **Allunga foglio** per disegni lunghi
- statistiche: parole, caratteri, tempo di lettura, tratti
- testo condiviso da altre app → diventa un nuovo foglio
- suoni sintetizzati (graffio della piuma, pagina che gira), vibrazione
- 6 temi Ampy: Cyberpunk, Vista/Aero, Futuristic, Retro, Moderno, Blood Moon

## Build
GitHub Actions compila l'APK a ogni push su `main` o su un branch `ampypad-*`.
Firma stabile opzionale tramite i secret `AMPYPAD_KEYSTORE_B64`, `AMPYPAD_STORE_PASSWORD`, `AMPYPAD_KEY_ALIAS`, `AMPYPAD_KEY_PASSWORD`.
