# LandMC Proxy

Plugin proxy dla sieci LandMC, planowany pod Velocity.

Projekt odpowiada za warstwę wejścia gracza do sieci oraz podstawową komunikację między instancjami.

## Odpowiedzialność

- routing graczy między lobby i trybami,
- status serwerów,
- tryb maintenance,
- limity wejść,
- komunikaty sieciowe,
- podstawowa walidacja połączeń,
- integracja z cache i messagingiem,
- przekazywanie graczy do instancji Paper.

## Zależności

Projekt powinien korzystać z bibliotek:

- `platform-api`,
- `platform-common`,
- `platform-config`,
- `platform-cache`,
- `platform-messaging`,
- `platform-proxy`.

## Proponowane moduły

```text
landmc-proxy/
  proxy-plugin/
  proxy-commands/
  proxy-routing/
  proxy-maintenance/
```

## Zasady

- Proxy nie zawiera logiki SkyBlocka.
- Proxy zna tylko serwery, statusy i routing.
- Komunikacja z Paper powinna przechodzić przez wspólne kontrakty z `landmc-platform`.

## Status

Projekt w przygotowaniu.