Plan de trabajo para toon-java
==============================
Este plan asume ejecución dentro de `./java` y sigue la arquitectura descrita en `PROJECT_OVERVIEW.md` y `ARCHITECTURE.md`. Cada bloque puede ejecutarse de forma incremental; marcar como completado cuando corresponda.

1. Preparación del entorno (completado)
   - Crear proyecto Gradle sin dependencias runtime; añadir carpetas standard de un proyecti Gradle. añadir plugin ANTLR y JUnit 5.
   - Configurar estructura de paquetes `org.toonjava` y directorio `org.toonjava.grammar` para código generado.
   - Integrar verificación de estilo básica (spotless/formatter opcional).

2. Gramática TOON (completado)
   - Extraer la gramática ABNF de `SPEC.md` y convertirla a `Toon.g4`.
   - Generar `ToonLexer` y `ToonParser`; confirmar que compila y produce árbol parseable para ejemplos oficiales.
   - Documentar mapeo ABNF → ANTLR en comentarios y en `ARCHITECTURE.md` (si no existe ya).

3. Adaptador de parser (`ToonTokener`) (completado)
   - Implementar clase wrapper que consuma el parser ANTLR y exponga métodos `nextValue`, `nextObject`, `nextArray`, etc.
   - Gestionar tracking de posición (línea/columna) para errores.
   - Añadir pruebas unitarias sobre el tokenizer usando snippets simples.

4. Modelo de datos dinámico (completado)
   - Implementar `ToonObject` (basado en `Map<String, Object>`) y `ToonArray` (basado en `List<Object>`), con API inspirada en `org.json`.
   - Añadir helpers `opt*`, `get*`, mutadores, y centinela `ToonNull` si se necesita.
   - Crear `ToonException` y asegurar que todo el modelo lo usa para reportar errores.

5. Decoder TOON → JSON (completado)
   - Implementar `ToonDecoder` que, usando `ToonTokener`, construya `ToonObject`/`ToonArray`.
   - Añadir conversión opcional a `JsonNode`/`Map` para integrarse con Jackson (sin añadir dependencia obligatoria).
   - Validar contra fixtures `tests/fixtures/decode`; crear tests parametrizados que lean los JSON de fixture.

6. Encoder JSON → TOON
   - Implementar `ToonEncoder` que acepte estructuras JSON genéricas (`Map`, `List`, `JsonNode`).
   - Implementar `ToonWriter` y `ToonStringer` para manejar indentación, delimitadores y headers tabulares.
   - Validar contra fixtures `tests/fixtures/encode` (incluyendo casos de error y opciones).

7. Opciones y strict mode
   - Diseñar objeto de configuración (`ToonOptions`) con `delimiter`, `indent`, `lengthMarker`, `strict`.
   - Asegurar que encoder/decoder respetan estas opciones y fallan correctamente cuando la spec lo exige.

8. Pruebas de integración y cobertura
   - Crear suite de round-trip JSON ↔ TOON usando ejemplos de `examples/`.
   - Añadir métricas básicas (por ejemplo, comparación de tokens) si es útil para validar eficiencia.
   - Ejecutar pruebas en CI local (Gradle `check`).

9. Documentación
   - Actualizar `README.md` del subproyecto con instrucciones de uso, opciones y estado de conformidad.
   - Añadir guía para correr fixtures y reportar resultados a la comunidad TOON.

10. Preparación para release
    - Revisar licencia, headers y metadatos.
    - Generar JAR minimalista y verificar que no incluya dependencias extra.
    - Redactar changelog inicial y plan de publicación (Maven Central o repositorio privado).

Notas
-----
- Mantener todo el código en `./java`.
- Antes de ejecutar tareas, revisar cambios pendientes en la rama para evitar conflictos.
- Reportar cualquier cambio que afecte la spec o requiera RFC según `CONTRIBUTING.md`.
