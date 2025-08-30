# ARCH

## Flusso in‑run
Sensors → RuleEngine → Fusion → RateLimit/Cap → Score → Select → PhraseRenderer → TTS/Metronome.

## Hysteresis & rate limit
- HR: +6/20s in, +3/15s out; Pace: ±10s in (90s), ±6s out (45s); Cadence: ±4 in/±2 out.
- Cap 10′: 7 msg (intermedio), 2 attivazioni metronomo.

## Fail-soft
- Nessuna dipendenza obbligatoria da musica/HR. TTS con TTL.