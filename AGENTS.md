# Project guidance

## Project overview
- Backend service for chat application
- Client web-app: client.html
- Mobile clients: ios, Android
- Entry point: src/main/java/com/oraskin/App.java

## Architecture rules
- Keep controllers thin
- Business logic stays in service layer
- Repositories must not contain business decisions
- Prefer immutable DTOs
- Prefer small changes instead complex solution
- Create folders for specific domain and refactor in case if new domain could be introduced 
- Manage folders and code units inside them according to the DDD approach
- Code maintainability and readability is high priority

## Coding standards
- Java 25
- Use constructor injection
- Prefer explicit types in public APIs
- Follow existing package naming conventions
- On public APIs adjustments - modify swagger.yaml
- Reusable API in code extract and introduce in com.oraskin.common package

## Testing
- Run unit tests first
- Add/adjust tests for behavior changes
- Do not rewrite broad test suites unless necessary
- Prefer focused assertions

## Safe commands
- mvn clean
- mvn test

## Avoid
- Do not change public API without explicit instruction
- Do not upgrade dependencies unless asked
- Do not rename files/modules just for style
- Do not edit CI/CD config unless task requires it

## When working on tasks
1. Read relevant files first
2. Explain plan briefly
3. Make minimal change set
4. Run relevant tests
5. Summarize risks and follow-ups