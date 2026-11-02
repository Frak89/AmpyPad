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

## Zoom
Pizzica con due dita ovunque sul foglio, oppure **− 100% +** nella barra in basso (50% → 200%). Il foglio viene
re-impaginato alla nuova dimensione: testo, righe e disegni restano nitidi. Con lo zoom il foglio scorre anche in orizzontale.

## Forme
Nel pennello tocca **◇**: linea, freccia, rettangolo, rettangolo arrotondato, ellisse, triangolo, rombo, stella, cuore, fumetto,
con contorno, pieno o contorno + velatura (colore, spessore e opacità del pennello; funzionano anche con la simmetria).
Dopo averla disegnata la forma resta **modificabile**: trascinala per spostarla, usa le maniglie per ridimensionarla, ✕ per eliminarla.
**Forme perfette**: disegna a mano un cerchio, un rettangolo o un triangolo e tieni fermo il dito → diventa perfetto (e modificabile).

## Scanner documenti ⎚
Dalla barra in alto (⎚) o da ESPORTA → SCANSIONA DOCUMENTI:
1. **Scatta** con la fotocamera o scegli una o più foto dalla **galleria**
2. **Rilevamento automatico dei bordi** del foglio (anche su sfondi colorati); angoli trascinabili con lente d'ingrandimento
3. **Correzione della prospettiva** e dei **bordi curvi** (pagine di libri, fogli piegati): i punti a metà lato curvano i bordi e il foglio viene "spianato"
4. **Pulizia**: Documento (toglie ombre e luce irregolare), Colori vivi, Grigio, Bianco e nero, Originale · rotazione
5. **PDF multipagina** (qualità leggera / media / alta, file piccoli adatti all'email) in `Download/AmpyPad`
6. **✉ Invia per email**: si apre l'app di posta con destinatario, oggetto e PDF già allegato
Tutto in Java puro, senza OpenCV né librerie esterne.

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
