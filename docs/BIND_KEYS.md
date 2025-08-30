# Bind keys — Stato accessibile dai blocchi

## Coach
- `coach.enabled`                (bool)
- `coach.preset`                 (enum: low | standard | high)
- `coach.verbosity`              (enum: low | standard | high)
- `coach.alerts.hr`              (bool)
- `coach.alerts.pace`            (bool)
- `coach.alerts.split`           (bool)
- `coach.alerts.last400m`        (bool)

## Musica
- `music.provider`               (enum: spotify | openaudio)
- `music.metronome`              (enum: off | out_of_range | always)
- `music.playlist.uri`           (string, opzionale)
- `music.energy.level`           (int 0..2, opzionale)

## Sessione
- `session.goal.mode`            (enum: distance | time)
- `session.goal.distanceKm`      (number)
- `session.goal.timeMin`         (number)

## Profilo
- `profile.defaultDistanceKm`    (number)

> Nota: i blocchi **non** implementano logica; leggono/scrivono questi campi. Il dominio interpreta.
