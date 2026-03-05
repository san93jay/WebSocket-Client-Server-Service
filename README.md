# WebSocket Client-Server Service

A Spring Boot TCP socket application with RSA/AES encryption, authentication, CSV data querying, ISO 8583 binary messaging, and automatic heartbeat/reconnect.

---

## Architecture

```
┌─────────────────────────────┐         ┌──────────────────────────────┐
│   websocket-client-service  │         │   websocket-server-service   │
│   (port 8081)               │         │   (configurable socket port) │
│                             │         │                              │
│  QueryController (REST)     │         │  SocketServer                │
│       ↓                     │◄───────►│       ↓                      │
│  SocketClient               │  TCP    │  ClientHandler (per thread)  │
│  HeartbeatService           │         │  UserService                 │
│  CryptoUtil (AES)           │         │  CsvService                  │
│  Iso8583Util                │         │  CryptoUtil (ThreadLocal AES)│
│  MessageProtocol            │         │  Iso8583Util                 │
└─────────────────────────────┘         │  MessageProtocol             │
                                        └──────────────────────────────┘
```

---

## Connection Flow

```
Client                                    Server
  │                                          │
  │──── TCP Connect ─────────────────────►  │
  │◄─── RSA Public Key (opcode 0x20) ──────  │  Server sends 2048-bit RSA public key
  │──── Encrypted AES Key (opcode 0x21) ──► │  Client encrypts AES-128 key with RSA
  │                                          │  Server decrypts, sets ThreadLocal AES key
  │──── AUTH (opcode 0x01, AES encrypted) ► │  "username:password"
  │◄─── AUTH_SUCCESS (opcode 0x02) ─────── │
  │◄─── CSV_ROW_COUNT (opcode 0x0B) ─────  │
  │◄─── CSV_DATA rows (opcode 0x04) ──────  │  All rows sent on connect
  │                                          │
  │  ──── Query Loop ────────────────────── │
  │──── QUERY (opcode 0x06, encrypted) ───► │
  │◄─── FOUND (0x06) or NOT FOUND (0x07) ─  │
  │                                          │
  │──── ISO 8583 Request (opcode 0x05) ───► │  Raw binary, not encrypted
  │◄─── ISO 8583 Response (opcode 0x05) ──  │
  │                                          │
  │──── PING (opcode 0x08) ───────────────► │  Periodic heartbeat
  │◄─── PONG (opcode 0x09) ───────────────  │
```

---

## Opcode Reference

| Opcode | Name            | Direction       | Encrypted |
|--------|-----------------|-----------------|-----------|
| 0x01   | AUTH            | Client → Server | ✅        |
| 0x02   | AUTH_SUCCESS    | Server → Client | ✅        |
| 0x03   | AUTH_FAILED     | Server → Client | ✅        |
| 0x04   | CSV_DATA        | Server → Client | ✅        |
| 0x05   | ISO8583         | Both            | ❌ (raw)  |
| 0x06   | QUERY / FOUND   | Both            | ✅        |
| 0x07   | NOT_FOUND       | Server → Client | ✅        |
| 0x08   | PING            | Client → Server | ✅        |
| 0x09   | PONG            | Server → Client | ✅        |
| 0x0B   | CSV_ROW_COUNT   | Server → Client | ✅        |
| 0x20   | RSA_PUBLIC_KEY  | Server → Client | ❌ (raw)  |
| 0x21   | ENCRYPTED_AES   | Client → Server | ❌ (raw)  |

---

## Message Protocol Wire Format

Every message follows this frame structure:

```
┌──────────┬────────────────┬─────────────────────┐
│ Opcode   │ Length         │ Payload              │
│ (1 byte) │ (4 bytes, int) │ (Length bytes)       │
└──────────┴────────────────┴─────────────────────┘
```

Text payloads are AES-encrypted then Base64-encoded before being written as UTF-8.
Binary payloads (RSA key, AES key, ISO 8583 frames) are written as raw bytes.

---

## ISO 8583 Binary Frame

Balance enquiry uses a simplified fixed-length binary format.

**Request (27 bytes):**
```
┌─────────────┬──────────────────────┬──────────────┐
│ MTI         │ Company / Terminal   │ Currency     │
│ 0x0200 (4B) │ 20 bytes, space-pad  │ "840" (3B)   │
└─────────────┴──────────────────────┴──────────────┘
```

**Response (14 bytes):**
```
┌─────────────┬──────────────┬────────────────────────┐
│ MTI         │ Response Code│ Balance (cents)         │
│ 0x0210 (4B) │ "00" (2B)    │ long, 8 bytes big-endian│
└─────────────┴──────────────┴────────────────────────┘
```

Response codes: `00` = Approved, `25` = Company not found.

---

## Project Structure

```
websocket-server-service/
├── handler/
│   └── ClientHandler.java          # Per-connection thread: handshake, auth, query loop
├── server/
│   └── SocketServer.java           # Accepts TCP connections, spawns ClientHandler threads
├── service/
│   ├── CsvService.java             # Loads CSV from classpath on startup
│   └── UserService.java            # Loads users file, authenticates credentials
├── protocol/
│   └── MessageProtocol.java        # Framing: send/receive raw and encrypted messages
├── utils/
│   ├── CryptoUtil.java             # AES encrypt/decrypt — ThreadLocal key per connection
│   └── Iso8583Util.java            # Build/parse ISO 8583 binary frames
└── resources/
    ├── application.properties
    ├── data.csv                    # CSV data file
    └── users.txt                   # username:password per line

websocket-client-service/
├── client/
│   └── SocketClient.java           # Connects, handshakes, sends queries, reconnects
├── controller/
│   └── QueryController.java        # REST endpoints → SocketClient
├── service/
│   └── HeartbeatService.java       # Sends PING, reads PONG — throws on failure
├── protocol/
│   └── MessageProtocol.java        # Mirrors server protocol
└── utils/
    ├── CryptoUtil.java             # AES encrypt/decrypt — volatile static key (single conn)
    └── Iso8583Util.java            # Build/parse ISO 8583 binary frames
```

---

## Configuration

**Server** `application.properties`:
```properties
socket.port=9090
socket.timeout.ms=60000
csv.file=data.csv
users.file=users.txt
```

**Client** `application.properties`:
```properties
server.port=8081
socket.host=localhost
socket.port=9090
client-username=user1
client-password=password1
reconnect.delay.ms=5000
heartbeat.interval.ms=10000
```

---

## CSV Data Format

```
CompanyA,Finance,1000
CompanyB,Technology,2000
CompanyC,Retail,1500
CompanyD,Healthcare,2500
CompanyE,Energy,3000
```

Column 0 = company name (query key), Column 1 = sector, Column 2 = balance.

---

## REST API

Start the client service and use Swagger UI at `http://localhost:8081/swagger-ui.html`.

**Query by company name:**
```bash
curl -X POST http://localhost:8081/query \
  -H "Content-Type: text/plain" \
  -d "CompanyA"
# → CompanyA,Finance,1000
```

**ISO 8583 balance enquiry:**
```bash
curl http://localhost:8081/query/balance/CompanyA
# → Balance=1000.00 USD
```

---

## Security Notes

- Each client connection negotiates its own AES-128 session key via RSA-2048.
- The server uses `ThreadLocal<SecretKey>` so concurrent connections cannot interfere.
- The client uses `volatile SecretKey` (single connection, multiple HTTP threads).
- Passwords are stored in plaintext in `users.txt` — use hashed passwords for production.
- AES is used in ECB mode — consider AES/GCM for production to prevent replay attacks.

---

## Reconnect Behaviour

The client automatically reconnects after server restart or network failure:

1. Heartbeat `IOException` propagates out of `HeartbeatService` (no internal catch).
2. `SocketClient` catches it, sets `connected = false`, nulls streams, closes socket.
3. `CountDownLatch` fires, unblocking the main connection thread.
4. `finally` block shuts down the heartbeat scheduler, clears the AES key, waits `reconnect.delay.ms`.
5. The `while(true)` loop retries a full RSA + auth + CSV handshake.
