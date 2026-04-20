# Whispers

Minimal Java backend for a 1:1 chat application with:
- plain HTTP endpoints for chat metadata, message history, and presence
- a WebSocket endpoint for live messaging and presence fan-out
- PostgreSQL-backed storage for chats, direct messages, private encrypted messages, browser public keys, and user ping timestamps
- in-memory runtime storage for active sessions and typing state

The project intentionally stays small and framework-light. It uses a custom annotation-based MVC layer instead of Spring, while keeping a similar controller style.

## Tech Stack

- Java 25
- Jackson for JSON serialization/deserialization
- PostgreSQL
- Liquibase
- Maven project layout
- OIDC-based authentication for HTTP and WebSocket access

## Entry Point

Application entry point:
- [App.java](/Users/olegraskin/IdeaProjects/whispers/src/main/java/com/oraskin/App.java)

Server bootstrap and wiring:
- [ChatServer.java](/Users/olegraskin/IdeaProjects/whispers/src/main/java/com/oraskin/ChatServer.java)

Default port is `8080`.

## What The Server Does

Current behavior:
- upgrades authenticated `GET /ws/user` to WebSocket
- allows one active WebSocket session per user
- creates 1:1 `DIRECT` chats and separate 1:1 `PRIVATE` chats
- stores direct messages and PRIVATE encrypted payloads in PostgreSQL
- sends live messages to the connected interlocutor when a session exists
- accepts periodic presence pings over WebSocket
- fans out presence updates over WebSocket to users who already have a chat with the sender

Important implementation rules enforced by the service layer:
- a user cannot create a chat with themselves
- a user can only read/send messages in chats they participate in
- `DIRECT` chats require plaintext `text`
- `PRIVATE` chats accept only encrypted payload metadata and never require plaintext message content
- `PRIVATE` chat creation and send flow require active browser public keys for both participants

## Architecture

The code is split into small domains and reusable infrastructure.

### Application Domains

Chat domain:
- repository: [src/main/java/com/oraskin/chat/repository](/Users/olegraskin/IdeaProjects/whispers/src/main/java/com/oraskin/chat/repository)
- service: [src/main/java/com/oraskin/chat/service](/Users/olegraskin/IdeaProjects/whispers/src/main/java/com/oraskin/chat/service)
- values/entities: [src/main/java/com/oraskin/chat/value](/Users/olegraskin/IdeaProjects/whispers/src/main/java/com/oraskin/chat/value) and [src/main/java/com/oraskin/chat/repository/entity](/Users/olegraskin/IdeaProjects/whispers/src/main/java/com/oraskin/chat/repository/entity)

User data and presence:
- [src/main/java/com/oraskin/user/data](/Users/olegraskin/IdeaProjects/whispers/src/main/java/com/oraskin/user/data)

Connected sessions:
- [src/main/java/com/oraskin/user/session](/Users/olegraskin/IdeaProjects/whispers/src/main/java/com/oraskin/user/session)

HTTP and WebSocket resources:
- [src/main/java/com/oraskin/resources](/Users/olegraskin/IdeaProjects/whispers/src/main/java/com/oraskin/resources)

### Reusable Common Layer

Reusable project infrastructure lives under:
- [src/main/java/com/oraskin/common/http](/Users/olegraskin/IdeaProjects/whispers/src/main/java/com/oraskin/common/http)
- [src/main/java/com/oraskin/common/json](/Users/olegraskin/IdeaProjects/whispers/src/main/java/com/oraskin/common/json)
- [src/main/java/com/oraskin/common/mvc](/Users/olegraskin/IdeaProjects/whispers/src/main/java/com/oraskin/common/mvc)
- [src/main/java/com/oraskin/common/websocket](/Users/olegraskin/IdeaProjects/whispers/src/main/java/com/oraskin/common/websocket)

This common layer provides:
- HTTP request parsing and response writing
- JSON serialization via Jackson
- lightweight routing with annotations
- WebSocket handshake and frame parsing
- centralized controller result writing

### Request Flow

HTTP flow:
1. `ChatServer` accepts the socket.
2. `HttpRequestReader` parses the raw request.
3. `HttpApiRouter` finds a `@RestController` mapping.
4. Controller delegates to `ChatService`.
5. `ControllerResultWriter` writes the HTTP response.

WebSocket flow:
1. `ChatServer` detects an upgrade request.
2. `WebSocketConnectionHandler` validates the handshake.
3. `UserConnectionController` opens and registers the user session.
4. `WebSocketConnectionHandler` processes incoming WebSocket text frames.
5. `ChatService` handles `DIRECT` messages while `PrivateChatService` handles `PRIVATE` encrypted messages.
6. Presence pings are also handled over the same WebSocket connection.

## API Summary

The contract is documented in:
- [swagger.yaml](/Users/olegraskin/IdeaProjects/whispers/swagger.yaml)

Current endpoints:
- `GET /ws/user`: upgrade to authenticated WebSocket
- `GET /chats?keyId={keyId}`: list chats for the current user; `DIRECT` chats are always returned, `PRIVATE` chats are returned only for the matching browser key
- `POST /chats?targetUserId={targetUserId}`: create a `DIRECT` chat
- `GET /messages?chatId={chatId}`: read plaintext history for a `DIRECT` chat
- `POST /public-keys`: register or update the current browser public key for `PRIVATE` chats
- `POST /private-chats?targetUserId={targetUserId}&keyId={keyId}`: create or reuse a browser-bound `PRIVATE` chat for the same user pair and browser-key pair
- `GET /private-chats/{chatId}?keyId={keyId}`: fetch browser-bound `PRIVATE` chat key metadata for opening the conversation
- `GET /private-chats/{chatId}/messages?keyId={keyId}`: read encrypted history for a `PRIVATE` chat using the bound browser key
- `GET /users`: list known interlocutors and last ping timestamps

### WebSocket Contract

Connect:
- `ws://localhost:8080/ws/user`

Server sends after successful connect:
```text
CONNECTED:alice
```

Client message payload for a direct chat:
```json
{"type":"MESSAGE","chatId":1,"text":"hello"}
```

Client message payload for a PRIVATE chat:
```json
{
  "type": "PRIVATE_MESSAGE",
  "chatId": 2,
  "privateMessage": {
    "protocolVersion": "v1",
    "encryptionAlgorithm": "AES-GCM",
    "keyWrapAlgorithm": "RSA-OAEP",
    "ciphertext": "base64-ciphertext",
    "nonce": "base64-nonce",
    "senderKeyId": "alice-browser-key",
    "senderMessageKeyEnvelope": "base64-envelope-for-sender",
    "recipientKeyId": "bob-browser-key",
    "recipientMessageKeyEnvelope": "base64-envelope-for-recipient"
  }
}
```

Server may send:
- `MessageRecord` JSON
- `PrivateMessageView` JSON
- `PresenceEvent` JSON
- plain text errors prefixed with `ERROR:`

Presence event example:
```json
{"type":"presence","username":"bob","lastPingTime":"2026-04-10T12:34:56"}
```

## Data Model

Main DTOs and records:
- `ChatSummary(chatId, username, type)`
- `MessageRecord(chatId, senderUserId, text, timestamp)`
- `PrivateMessageView(chatId, senderUsername, chatType, encryptedMessage, timestamp)`
- `SendMessageCommand(chatId, text)`
- `SendPrivateMessageCommand(chatId, encryptedMessage)`
- `PresenceEvent(type, username, lastPingTime)`
- `User(username, firstName, lastName, lastPingTime)`

Persistence is split by concern:
- chats keyed by a database-generated sequence id, typed as `DIRECT` or `PRIVATE`, and optionally bound to browser `keyId`s for `PRIVATE`
- plaintext `DIRECT` messages stored in `messages`
- encrypted `PRIVATE` messages stored in `private_messages`
- browser public keys for `PRIVATE` chats stored in `public_keys`
- users stored in PostgreSQL
- active sessions stored in memory
- typing state stored in memory

## PRIVATE Chat MVP

`PRIVATE` means browser-tied end-to-end encrypted chat metadata. The backend acts as an untrusted relay and storage layer:
- the frontend registers a browser public key with `POST /public-keys`
- the frontend creates a `PRIVATE` chat with its current browser `keyId`
- `/chats?keyId=...` returns only the PRIVATE chats bound to that browser key
- opening a `PRIVATE` chat uses `GET /private-chats/{chatId}?keyId=...` to fetch the browser-bound current-user key metadata and the counterpart public key
- message history uses `GET /private-chats/{chatId}/messages?keyId=...`
- sending uses WebSocket `PRIVATE_MESSAGE` with only encrypted payload fields

The authenticated user for `POST /public-keys` is resolved from the bearer token, like other protected HTTP endpoints. The client should not send `userId` in the request body.

Browser-key behavior:
- users may register multiple browser public keys
- each PRIVATE chat is bound to one browser `keyId` per participant
- the same two users may therefore have multiple PRIVATE chats, one per browser-key pair
- if the frontend asks for `/chats` with a different browser `keyId`, the backend omits those PRIVATE chats
- PRIVATE history/open operations require the `keyId` that the chat is bound to

What the backend stores for `PRIVATE` messages:
- `ciphertext`
- `nonce`
- `protocolVersion`
- `encryptionAlgorithm`
- `keyWrapAlgorithm`
- `senderKeyId`
- `senderMessageKeyEnvelope`
- `recipientKeyId`
- `recipientMessageKeyEnvelope`
- sender/chat/timestamp metadata

What the backend does not store for `PRIVATE` messages:
- plaintext text
- private keys

Why the sender self-envelope exists:
- the frontend generates a per-message symmetric key
- that key is wrapped once for the recipient browser key and once for the sender browser key
- storing the sender envelope lets the sender reopen their own sent history later without the backend ever seeing plaintext or private key material

## Running Locally

### With Maven

If Maven is installed:
```bash
mvn clean test
```

Then run the application from your IDE or by compiling/running the `com.oraskin.App` main class directly.

### With javac

If Maven is unavailable, compile with the Jackson jars available in the local Maven cache:
```bash
javac -cp "$HOME/.m2/repository/com/fasterxml/jackson/core/jackson-databind/2.21.2/jackson-databind-2.21.2.jar:$HOME/.m2/repository/com/fasterxml/jackson/core/jackson-core/2.21.2/jackson-core-2.21.2.jar:$HOME/.m2/repository/com/fasterxml/jackson/core/jackson-annotations/2.21/jackson-annotations-2.21.jar:$HOME/.m2/repository/com/fasterxml/jackson/datatype/jackson-datatype-jsr310/2.21.2/jackson-datatype-jsr310-2.21.2.jar:$HOME/.m2/repository/org/postgresql/postgresql/42.7.7/postgresql-42.7.7.jar:$HOME/.m2/repository/org/liquibase/liquibase-core/4.33.0/liquibase-core-4.33.0.jar" -d out $(find src/main/java -name '*.java')
```

Then run:
```bash
java -cp "out:src/main/resources:$HOME/.m2/repository/com/fasterxml/jackson/core/jackson-databind/2.21.2/jackson-databind-2.21.2.jar:$HOME/.m2/repository/com/fasterxml/jackson/core/jackson-core/2.21.2/jackson-core-2.21.2.jar:$HOME/.m2/repository/com/fasterxml/jackson/core/jackson-annotations/2.21/jackson-annotations-2.21.jar:$HOME/.m2/repository/com/fasterxml/jackson/datatype/jackson-datatype-jsr310/2.21.2/jackson-datatype-jsr310-2.21.2.jar:$HOME/.m2/repository/org/postgresql/postgresql/42.7.7/postgresql-42.7.7.jar:$HOME/.m2/repository/org/liquibase/liquibase-core/4.33.0/liquibase-core-4.33.0.jar" com.oraskin.App
```

### With Docker Compose

For local Postgres infrastructure:
```bash
docker compose up -d
```

This starts PostgreSQL on `localhost:5432` with:
- database: `whispers`
- username: `whispers`
- password: `whispers`

The database state is persisted in the named Docker volume `postgres-data`.

The application now uses PostgreSQL at startup. By default it connects to:
- JDBC URL: `jdbc:postgresql://localhost:5432/whispers`
- username: `whispers`
- password: `whispers`

These values can be overridden with:
- `WHISPERS_DB_URL`
- `WHISPERS_DB_USER`
- `WHISPERS_DB_PASSWORD`

Database schema changes are managed by Liquibase and applied automatically on application startup.

Liquibase changelog structure:
- master file: `src/main/resources/db/changelog/db.changelog-master.xml`
- DDL changes: `src/main/resources/db/changelog/ddl`
- DML changes: `src/main/resources/db/changelog/dml`

Recommended file naming:
- DDL: `0001_ddl_initial_schema.xml`, `0002_ddl_add_user_profile.xml`
- DML: `1001_dml_seed_reference_data.xml`, `1002_dml_backfill_user_names.xml`

The ordering rule is:
- keep DDL and DML in separate folders
- include files explicitly from the master changelog for stable execution order
- reserve lower sequence ranges for DDL and higher ranges for DML

## Development Notes

Current project characteristics:
- controllers are thin and mostly translate transport to service calls
- business rules live in `ChatService`
- repositories are storage-oriented, with PostgreSQL for durable data
- DTOs are mostly immutable records
- the custom MVC layer is intentionally small and reflection-based

Good places for future extension:
- replace in-memory repositories with persistent implementations
- add authentication/authorization
- add tests around service rules and controller routing
- extend user profile data beyond username and ping timestamp
- push full chat/user sync over WebSocket instead of relying partly on polling

## Limitations

Current limitations worth knowing:
- active sessions and typing state are still lost on restart
- only 1:1 chats are supported
- only one live session per user is supported
- no auth, tenanting, or permissions beyond chat participation checks
- no dedicated unit/integration test suite is present yet
- some presence/UI behavior is designed for a simple web client, not a full production presence model
