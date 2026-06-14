---
Kotlin MPP
---

- This codebase is a kotlin multiplatform library
- Follow Kotlin official coding conventions (naming, indentation, imports, etc.).
  - Exception to this, put constants before variables in comparisons
- Always write common code if possible, using `commonMain` for source code files
- Do not use annotations without asking first
- Ensure modularization for better maintainability and scalability.
- Avoid using platform-specific APIs unless necessary; abstract them via expect/actual mechanism.
- Use `kotlin.test` framework for common tests
- Provide API documentation using Dokka for Kotlin libraries.