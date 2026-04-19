# Whispers

Minimal Java backend for a 1:1 chat application with:
- plain HTTP endpoints for chat metadata, message history, and presence
- a WebSocket endpoint for live messaging and presence fan-out
- PostgreSQL-backed storage for chats, messages, and user ping timestamps
- in-memory runtime storage for active sessions and typing state

The project intentionally stays small and framework-light. It uses a custom annotation-based MVC layer instead of Spring, while keeping a similar controller style.

## Tech Stack

- Java 25
- Jackson for JSON serialization/deserialization
- PostgreSQL
- Liquibase
- Maven project layout
- No authorization yet

## Entry Point

Application entry point:
- [App.java](/Users/olegraskin/IdeaProjects/whispers/src/main/java/com/oraskin/App.java)

Server bootstrap and wiring:
- [ChatServer.java](/Users/olegraskin/IdeaProjects/whispers/src/main/java/com/oraskin/ChatServer.java)

Default port is `8080`.

## What The Server Does

Current behavior:
- upgrades `GET /ws/user?userId=...` to WebSocket
- allows one active WebSocket session per user
- creates 1:1 chats only
- stores chat history in memory
- sends live messages to the connected interlocutor
- accepts periodic presence pings over HTTP
- fans out presence updates over WebSocket to users who already have a chat with the sender

Important implementation rules enforced by the service layer:
- a user cannot create a chat with themselves
- a chat can only be created with a currently connected user
- a user can only read/send messages in chats they participate in
- messages are rejected if the recipient is offline

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
5. `ChatService` validates, stores, and routes chat messages.
6. Presence pings are also handled over the same WebSocket connection.

## API Summary

The contract is documented in:
- [swagger.yaml](/Users/olegraskin/IdeaProjects/whispers/swagger.yaml)

Current endpoints:
- `GET /ws/user?userId={userId}`: upgrade to WebSocket
- `GET /chats?userId={userId}`: list chats for user
- `POST /chats?userId={userId}&targetUserId={targetUserId}`: create chat
- `GET /messages?userId={userId}&chatId={chatId}`: read message history
- `GET /users?userId={userId}`: list known interlocutors and last ping timestamps
- WebSocket text frame `{"type":"ping"}`: update last ping time and fan-out presence event

### WebSocket Contract

Connect:
- `ws://localhost:8080/ws/user?userId=alice`

Server sends after successful connect:
```text
CONNECTED:alice
```

Client message payload:
```json
{"chatId":1,"text":"hello"}
```

Server may send:
- `MessageRecord` JSON
- `PresenceEvent` JSON
- plain text errors prefixed with `ERROR:`

Presence event example:
```json
{"type":"presence","username":"bob","lastPingTime":"2026-04-10T12:34:56"}
```

## Data Model

Main DTOs and records:
- `ChatSummary(chatId, username)`
- `MessageRecord(chatId, senderUserId, text, timestamp)`
- `SendMessageCommand(chatId, text)`
- `PresenceEvent(type, username, lastPingTime)`
- `User(username, firstName, lastName, lastPingTime)`

Persistence is split by concern:
- chats keyed by a database-generated sequence id
- messages and users stored in PostgreSQL
- active sessions stored in memory
- typing state stored in memory

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
