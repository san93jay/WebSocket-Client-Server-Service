# WebSocket Client-Server Service

A Spring Boot TCP socket application featuring RSA/AES encryption, credential-based authentication, CSV data querying, ISO 8583 binary messaging, and automatic heartbeat with reconnect.

---

## Architecture

```
┌──────────────────────────────────┐              ┌──────────────────────────────────┐
│     websocket-client-service     │              │     websocket-server-service     │
│           (port 8081)            │              │      (configurable TCP port)     │
│                                  │              │                                  │
│  QueryController  (REST API)     │              │  SocketServer                    │
│         ↓                        │◄────TCP─────►│         ↓                        │
│  SocketClient                    │              │  ClientHandler  (per thread)     │
│  HeartbeatService                │              │  UserService                     │
│  CryptoUtil  (volatile AES key)  │              │  CsvService                      │
│  Iso8583Util                     │              │  CryptoUtil  (ThreadLocal AES)   │
│  MessageProtocol                 │              │  Iso8583Util                     │
└──────────────────────────────────┘              │  MessageProtocol                 │
                                                  └──────────────────────────────────┘
```

---

## Connection Flow

```
Client                                         Server
  │                                               │
  │──── TCP Connect ───────────────────────────► │
  │◄─── RSA Public Key        (opcode 0x20) ──── │  2048-bit RSA public key
  │──── Encrypted AES Key     (opcode 0x21) ───► │  AES-128 key encrypted with RSA
  │                                               │  Server decrypts → ThreadLocal AES
  │──── AUTH                  (opcode 0x01) ───► │  "username:password"  (AES encrypted)
  │◄─── AUTH_SUCCESS          (opcode 0x02) ──── │
  │◄─── CSV_ROW_COUNT         (opcode 0x0B) ──── │
  │◄─── CSV_DATA × N rows     (opcode 0x04) ──── │  All rows pushed on connect
  │                                               │
  │         ════════ Query Loop ════════          │
  │──── QUERY                 (opcode 0x06) ───► │  AES encrypted company name
  │◄─── FOUND / NOT_FOUND  (0x06 / 0x07)  ────── │
  │                                               │
  │──── ISO 8583 Request      (opcode 0x05) ───► │  Raw binary — not encrypted
  │◄─── ISO 8583 Response     (opcode 0x05) ──── │
  │                                               │
  │──── PING                  (opcode 0x08) ───► │  Periodic heartbeat
  │◄─── PONG                  (opcode 0x09) ──── │
```

---

## Opcode Reference

| Opcode | Name           | Direction        | Encrypted  |
|--------|----------------|------------------|------------|
| `0x01` | AUTH           | Client → Server  | ✅ AES     |
| `0x02` | AUTH_SUCCESS   | Server → Client  | ✅ AES     |
| `0x03` | AUTH_FAILED    | Server → Client  | ✅ AES     |
| `0x04` | CSV_DATA       | Server → Client  | ✅ AES     |
| `0x05` | ISO8583        | Both             | ❌ Raw     |
| `0x06` | QUERY / FOUND  | Both             | ✅ AES     |
| `0x07` | NOT_FOUND      | Server → Client  | ✅ AES     |
| `0x08` | PING           | Client → Server  | ✅ AES     |
| `0x09` | PONG           | Server → Client  | ✅ AES     |
| `0x0B` | CSV_ROW_COUNT  | Server → Client  | ✅ AES     |
| `0x20` | RSA_PUBLIC_KEY | Server → Client  | ❌ Raw     |
| `0x21` | ENCRYPTED_AES  | Client → Server  | ❌ Raw     |

---

## Message Protocol Wire Format

Every message uses this fixed frame structure:

```
┌────────────┬──────────────────┬──────────────────────┐
│  Opcode    │  Length          │  Payload             │
│  (1 byte)  │  (4 bytes, int)  │  (Length bytes)      │
└────────────┴──────────────────┴──────────────────────┘
```

- **Text payloads** — AES encrypted, Base64 encoded, written as UTF-8 bytes.
- **Binary payloads** — RSA keys, AES key, ISO 8583 frames — written as raw bytes.

---

## ISO 8583 Binary Frame

Balance enquiry uses a simplified fixed-length binary format.

**Request — 27 bytes:**
```
┌──────────────────┬────────────────────────┬────────────────┐
│  MTI             │  Company / Terminal    │  Currency      │
│  0x0200  (4 B)   │  space-padded  (20 B)  │  "840"  (3 B)  │
└──────────────────┴────────────────────────┴────────────────┘
```

**Response — 14 bytes:**
```
┌──────────────────┬────────────────────┬──────────────────────────────┐
│  MTI             │  Response Code     │  Balance (cents)             │
│  0x0210  (4 B)   │  "00"  (2 B)       │  long, big-endian  (8 B)     │
└──────────────────┴────────────────────┴──────────────────────────────┘
```

| Response Code | Meaning             |
|---------------|---------------------|
| `00`          | Approved            |
| `25`          | Company not found   |

---

## Project Structure

```
websocket-server-service/
├── handler/
│   └── ClientHandler.java       # Per-connection thread — handshake, auth, query loop
├── server/
│   └── SocketServer.java        # Accepts TCP connections, spawns ClientHandler threads
├── service/
│   ├── CsvService.java          # Loads CSV from classpath on startup
│   └── UserService.java         # Loads users file, authenticates credentials
├── protocol/
│   └── MessageProtocol.java     # Framing — send/receive raw and encrypted messages
├── utils/
│   ├── CryptoUtil.java          # AES encrypt/decrypt — ThreadLocal key per connection
│   └── Iso8583Util.java         # Build/parse simplified ISO 8583 binary frames
└── resources/
    ├── application.properties
    ├── data.csv                 # Company data
    └── users.txt                # Credentials (username:password per line)

websocket-client-service/
├── client/
│   └── SocketClient.java        # Connects, handshakes, sends queries, auto-reconnects
├── controller/
│   └── QueryController.java     # REST endpoints → SocketClient
├── service/
│   └── HeartbeatService.java    # Sends PING, reads PONG — throws IOException on failure
├── protocol/
│   └── MessageProtocol.java     # Mirrors server protocol
└── utils/
    ├── CryptoUtil.java          # AES encrypt/decrypt — volatile static key (single conn)
    └── Iso8583Util.java         # Build/parse simplified ISO 8583 binary frames
```

---

## Configuration

**Server** `application.properties`:
```yaml
server:
  port: 8080                      # Swagger UI port

spring:
  application:
    name: websocket-server-service

socket:
  port: 9091                      # TCP socket port
  timeout:
    ms: 30000                     # Client inactivity timeout (30s)

csv:
  file: data.csv                  # Classpath CSV file

users:
  file: users.txt                 # Classpath credentials file
```

**Client** `application.properties`:
```yaml
server:
  port: 8081                      # REST API / Swagger UI port

spring:
  application:
    name: websocket-client-service

socket:
  host: localhost
  port: 9091                      # Must match server socket.port

client-username: user1
client-password: password1

reconnect:
  delay:
    ms: 5000                      # Wait 5s before reconnect attempt

heartbeat:
  interval:
    ms: 10000                     # Heartbeat every 10s (must be < server timeout)
```

---

## Data Files

**`users.txt`**
```
user1:password1
user2:password2
admin:admin123
```

**`data.csv`** — Column 0: company name (query key) · Column 1: sector · Column 2: balance
```
CompanyA,Finance,1000
CompanyB,Technology,2000
CompanyC,Retail,1500
CompanyD,Healthcare,2500
CompanyE,Energy,3000
```

---

## REST API

Swagger UI is available at `http://localhost:8081/swagger-ui.html` once the client service is running.

**Query company by name:**
```bash
curl -X POST http://localhost:8081/query \
  -H "Content-Type: text/plain" \
  -d "CompanyA"

# Response
CompanyA,Finance,1000
```

**ISO 8583 balance enquiry:**
```bash
curl http://localhost:8081/query/balance/CompanyA

# Response
Balance=1000.00 USD
```

---

## Security Notes

| Concern | Current Behaviour | Production Recommendation |
|---|---|---|
| Session key | RSA-2048 + AES-128 per connection | ✅ Adequate for transport |
| Server concurrency | `ThreadLocal<SecretKey>` per thread | ✅ No cross-connection leakage |
| Client key scope | `volatile SecretKey` (single connection) | ✅ Safe for one active connection |
| Password storage | Plaintext in `users.txt` | Hash with BCrypt |
| AES mode | ECB | Upgrade to AES/GCM (authenticated encryption) |

---

## Reconnect Behaviour

The client recovers automatically from server restarts or network failures:

```
Server drops connection
        ↓
HeartbeatService throws IOException  (no internal catch — propagates to SocketClient)
        ↓
Heartbeat lambda catches → connected=false, streams nulled, socket closed
        ↓
CountDownLatch fires → disconnectLatch.await() unblocks
        ↓
finally block → shuts down scheduler, clears AES key, sleeps reconnect.delay.ms
        ↓
while(true) retries → full RSA + Auth + CSV handshake
```
