# resizefunctions – API & Appunti

> Pacchetto: `ai.runow.ui.renderer`  
> Dipendenze: `AppMode.kt` (enum `AppMode`, `LocalAppMode`)  
> Non ridefinisce funzioni di rendering generali.

---

## 1) Cambio modalità – Speed‑Dial

### `@Composable fun AppModeSpeedDial(current: AppMode, onPick: (AppMode) -> Unit, modifier: Modifier = Modifier, expandedInitially: Boolean = false)`

- **Scopo**: unico bottone flottante per passare tra `Real`, `Designer`, `Resize`.
- **Ritorno**: `Unit` (render UI)
- **Chi lo chiama**: `UiScreen` o lo scaffold principale, *una sola volta* per schermata.
- **Note**:
  - Sostituisce il vecchio “knob” laterale.
  - Mostra un mini menù verticale quando espanso.

---

## 2) Avviso trasparente in Resize Mode

### `@Composable fun ResizeHintOverlayController(mode: AppMode, modifier: Modifier = Modifier, timeoutMillis: Long = 10_000L)`

- **Scopo**: mostra automaticamente l’overlay di istruzioni **ogni volta** che `mode == AppMode.Resize`.
- **Comportamento**:
  - Si visualizza entrando in Resize.
  - Sparisce dopo `timeoutMillis` (default 10s) **o** al primo tap sull’overlay.
- **Chi lo chiama**: `UiScreen` (in alto nello Scaffold) passando `mode` corrente (es. da `LocalAppMode.current`).

### `@Composable fun ResizeHintOverlay(onDismiss: () -> Unit, modifier: Modifier = Modifier, timeoutMillis: Long = 10_000L)`

- **Scopo**: sola UI dell’overlay (testo su sfondo trasparente).
- **Chi la chiama**: tipicamente **solo** da `ResizeHintOverlayController`.

---

## 3) Interazione blocchi in Resize Mode (pulse, move, swap)

### `class ResizeMoveState(initialActive: Boolean = false, initialMoveEnabled: Boolean = false)`

- **Proprietà**:
  - `var isActiveForResize: Boolean` – quando `true`, il blocco è “armato” per resize (e mostra *pulse*).
  - `var isMoveEnabled: Boolean` – abilita il drag/spostamento del blocco.
  - `fun activateWithPulse()` – attiva il resize e lancia un breve effetto di *pulse*.
  - `fun deactivate()` – disattiva resize & move.

### `@Composable fun rememberResizeMoveState(): ResizeMoveState`

- **Scopo**: ricordare lo stato per singolo blocco.

### `@Composable fun ResizeMoveInteractor(enabled: Boolean, state: ResizeMoveState, index: Int, neighborsBoundsProvider: () -> List<Rect>, overlapToleranceFraction: Float = 0.35f, onSwapRequest: (from: Int, to: Int) -> Unit, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit)`

- **Scopo**: wrapper da applicare attorno al contenuto di un blocco **solo in Resize Mode**:
  - **Tap prolungato**: attiva resize + *pulse* (se è già attivo, un secondo long‑press abilita lo **spostamento**).
  - **Drag** (se spostamento abilitato): sposta orizzontalmente; se la **sovrapposizione** con un vicino supera `overlapToleranceFraction`, chiama `onSwapRequest(from, to)`.
  - **Pulse**: effetto visivo 1→1.06→1 sulla selezione.
- **Parametri**:
  - `enabled`: passa `mode == AppMode.Resize`.
  - `state`: da `rememberResizeMoveState()`.
  - `index`: indice del blocco nella riga/colonna.
  - `neighborsBoundsProvider`: lambda che deve restituire i **Rect** globali dei fratelli **in ordine** (incluso il blocco corrente); tipicamente calcolati dal genitore (Row/Column) con `onGloballyPositioned`.
  - `overlapToleranceFraction`: soglia (0..1) per decidere lo swap su sovrapposizione.
  - `onSwapRequest`: callback per richiedere lo scambio tra `from` e `to`.
- **Chi lo chiama**: il **genitore** che renderizza i blocchi (es. una riga). Non disegna handle; quelli rimangono dove già presenti (es. `ResizableRow.kt`).

### `fun horizontalOverlapRatio(a: Rect, b: Rect): Float`

- **Scopo**: ritorna la frazione di overlap orizzontale tra due `Rect` (0..1).

---

## Esempi di integrazione

> **Speed‑Dial + Overlay (in UiScreen/scaffold)**

```kotlin
val mode = LocalAppMode.current

Box(Modifier.fillMaxSize()) {
  // contenuto…

  // Overlay di istruzioni
  ResizeHintOverlayController(mode = mode)

  // FAB con 3 modalità
  AppModeSpeedDial(
    current = mode,
    onPick = { picked ->
      // aggiorna CompositionLocal o il tuo stato schermata
      // es: appMode.value = picked
    },
    modifier = Modifier.fillMaxSize()
  )
}