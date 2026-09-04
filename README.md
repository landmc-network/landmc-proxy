# LandMC Proxy

Główny plugin Velocity sieci LandMC — warstwa wejściowa: routing graczy, tryb serwisowy,
rejestr obecności i komunikacja z instancjami Paper.

To cienka warstwa funkcjonalna nad [`landmc-platform`](https://github.com/landmc-network/landmc-platform).
Nie ma tu własnego loadera configów, klienta Redisa, systemu komend ani warstwy MiniMessage —
wszystko to dostarcza platforma.

```text
Velocity
    ↓
landmc-proxy
    ↓
landmc-platform
    ↓
LiteCommands / NoticeService / Messaging / PacketEvents
```

## Status

Pierwszy milestone gotowy i uruchomiony na **Velocity 4.1.1**: plugin wstaje, ładuje
konfigurację, startuje messaging, rejestruje komendy i listenery, a przy `end` schodzi
czysto — bez pozostawionych wątków i połączeń.

```text
[landmc-proxy]: LandMC Proxy starting...
[landmc-proxy]: Loaded configuration.
[landmc-proxy]: Messaging connected (Redis).
[landmc-proxy]: PacketEvents ready (owner: true).
[landmc-proxy]: Registered 4 commands.
[landmc-proxy]: Registered 3 backend servers.
[landmc-proxy]: LandMC Proxy ready (252 ms).
```

## Wymagania

| Element | Wersja |
|---|---|
| Velocity | 4.1.1 |
| Java | 25 — tego wymaga bytecode Velocity 4 i `platform-proxy` |
| `landmc-platform` | 1.0.0-SNAPSHOT |
| Redis | opcjonalny, patrz *Messaging* |
| PacketEvents | opcjonalny plugin proxy |

## Build

Platforma musi być dostępna lokalnie:

```bash
cd ../landmc-platform && ./gradlew publishToMavenLocal
```

```bash
./gradlew build
```

Wynik: `build/libs/landmc-proxy.jar` — gotowy do wrzucenia do `plugins/`.

Jar zawiera wyłącznie `pl/landmc/**`. Okaeri, LiteCommands, Multification i Jedis są
zrelokowane pod `pl.landmc.proxy.libs`, żeby nie zderzyły się z innym pluginem. Adventure,
Gson i SLF4J **nie** są pakowane — dostarcza je Velocity, a Jedis ciągnie SLF4J 1.7.x, który
przesłoniłby 2.x proxy i wyłożył logowanie.

## Konfiguracja

`plugins/landmc-proxy/config.yml`:

```yaml
proxy:
  server-id: "proxy-1"        # ID tego proxy w sieci, musi byc unikalne

routing:
  fallback-server: "lobby-1"  # serwer wejsciowy i awaryjny

maintenance:
  enabled: false
  bypass-permission: "landmc.maintenance.bypass"

messaging:
  enabled: true
  redis:
    host: "127.0.0.1"
    port: 6379
    channel-prefix: "landmc"
```

`plugins/landmc-proxy/messages.yml` zawiera komunikaty domenowe proxy oraz sekcję `platform:`
z komunikatami technicznymi całej sieci. Zmiana `platform.prefix` przestawia wygląd wszystkich
komunikatów frameworkowych naraz.

## Komendy

| Komenda | Uprawnienie | Działanie |
|---|---|---|
| `/server` | `landmc.command.server` | lista backendów |
| `/server <serwer>` | `landmc.command.server` | przeniesienie gracza |
| `/lobby`, `/hub` | — | przeniesienie na fallback |
| `/maintenance on\|off\|status` | `landmc.command.maintenance` | tryb serwisowy |
| `/testmessage <serwer>` | `landmc.command.testmessage` | diagnostyka messagingu |

Wszystkie przez LiteCommands z `platform-proxy`. Nieprawidłowe użycie, brak uprawnień i błąd
wykonania obsługuje wspólna warstwa platformy, więc te komunikaty są identyczne na proxy
i na każdej instancji Paper.

## Serwisy

**`ServerRegistry`** — lookup backendu po ID. Nie jest cache'em nad Velocity: dokłada
niewrażliwość na wielkość liter (gracz wpisze `/server Lobby-1`) i jedno miejsce zamiast
`Optional` rozsianego po komendach.

**`RoutingService`** — transfer gracza i fallback. Nie zna trybu serwisowego ani komunikatów;
zwraca `TransferResult`, a wołający zamienia go na wiadomość.

**`MaintenanceService`** — flaga plus uprawnienie omijające. Stan jest zapisywany do
`config.yml` przez `ConfigService`, więc restart proxy nie kończy po cichu okna serwisowego.

**`PlayerPresenceService`** — indeks `UUID → serverId`, implementuje `PlayerLocator`
z platformy. To dzięki niemu wiadomość kierowana do gracza trafia do jednej instancji zamiast
do wszystkich. Świadomie lokalny: sieć z kilkoma proxy będzie potrzebowała indeksu w Redisie,
ale interfejs zostanie ten sam.

## Messaging

Szyna pochodzi z `platform-messaging`. W tym projekcie nie ma ani jednej linijki obsługi
Redisa.

`messaging.enabled: false` **nie** wyłącza szyny — proxy używa wtedy transportu w obrębie
procesu. Plugin wstaje bez Redisa i wszystkie wywołania działają, tylko nie widzą innych
instancji. Alternatywa, czyli `null` zamiast szyny, oznaczałaby sprawdzanie `null` przed każdą
publikacją.

Diagnostyka: `/testmessage proxy-1` odpytuje samo proxy, bo rejestruje ono handler `test.ping`.
To pełna droga przez prawdziwy transport, możliwa do sprawdzenia zanim powstanie pierwsza
instancja Paper. Konsument po stronie Paper rejestruje tę samą parę i odpowiada za swoje ID.

```text
/testmessage skyblock-1
    ↓  test.ping (REQUEST, correlationId)
Redis
    ↓
Paper  ──►  test.pong
    ↓
odpowiedź kończy future, komunikat trafia do gracza
```

Odpowiedź jest komponowana, nigdy `join()`/`get()` — blokowanie wątku, na którym Velocity
wywołało komendę, zatrzymałoby całą sieć na czas timeoutu.

### Gdzie należą `PingMessage` i `PongMessage`

Na razie w tym repo. Obie strony sieci ich potrzebują, więc gdy projekt Paper zacznie
odpowiadać, para przenosi się do wspólnego modułu network API. Nie trafiły do
`platform-messaging`, bo platforma dostarcza szynę, a nie ruch — inaczej każdy przyszły
projekt dziedziczyłby słownictwo, którego nie używa.

## PacketEvents

Opcjonalna zależność pluginowa, nie shadowana. PacketEvents ma własny `velocity-plugin.json`,
więc druga kopia w tym jarze biłaby się z zainstalowaną. Bez niego proxy startuje normalnie
i loguje, że integracje pakietowe są wyłączone — żadna funkcja jeszcze z nich nie korzysta.

## Czego tu nie ma

Zgodnie z zakresem pierwszego etapu: kolejek, znajomych, party, czatu globalnego, systemu
banów, autoryzacji, anti-botu, anti-VPN, zaawansowanego load balancingu, ekonomii cross-server
ani GUI. Fallback jest pojedynczy — łańcuch failover między wieloma lobby wymaga polityki,
której nikt jeszcze nie ustalił.

## Testy

```bash
./gradlew test
```

15 testów, bez potrzeby stawiania proxy czy Redisa:

- `ProxyConfigurationTest` — ładowanie `config.yml` i `messages.yml` w kolejności, której używa
  bootstrap, w tym serializacja pól `Notice`,
- `MaintenanceServiceTest` — przełączanie i utrwalanie stanu na dysku,
- `PingPongTest` — `test.ping → test.pong` przez transport w procesie, razem z timeoutem
  requestu.
