Objetivo
========
Este documento traduce los lineamientos de `PROJECT_OVERVIEW.md` a una arquitectura operativa que OpenAI Codex (modo agente) pueda seguir al trabajar dentro de `./java`.

Visión general
--------------
- Librería: `toon-java`, port minimalista de `org.json` orientado a TOON.
- Alcance: conversión JSON ↔ TOON preservando la especificación oficial (SPEC v1.3) y sin gestionar serialización de POJOs.
- Filosofía: API pequeña, sin dependencias de runtime, clases cortas y comprensibles.

Paquetes y módulos
------------------
- Paquete raíz único `org.toonjava` (similar al `org.json` original).
- Archivos generados por ANTLR ubicados en `org.toonjava.grammar` pero expuestos mediante adaptadores del paquete raíz para mantener la interfaz minimalista.
- Estructura mínima esperada:
  - `org.toonjava.ToonObject`
  - `org.toonjava.ToonArray`
  - `org.toonjava.ToonTokener` (envoltorio sobre lexer/parser ANTLR)
  - `org.toonjava.ToonEncoder`
  - `org.toonjava.ToonDecoder`
  - `org.toonjava.ToonWriter` / `org.toonjava.ToonStringer`
  - `org.toonjava.ToonException`

Modelo de datos
---------------
- `ToonObject`: contenedor dinámico respaldado por `Map<String, Object>`.
- `ToonArray`: lista ordenada respaldada por `List<Object>`.
- Tipos admitidos: `String`, `Number`, `Boolean`, `ToonObject`, `ToonArray`, `null` (representado con centinela opcional `ToonNull` si es necesario).
- Sin binding automático a clases de dominio; toda la transformación se realiza sobre estructuras básicas.

Pipeline de parsing y encoding
------------------------------
1. **Entrada TOON → JSON**  
   - `ToonTokener` consume la cadena (usa clases ANTLR generadas a partir de la gramática `.g4` derivada del ABNF).  
   - `ToonDecoder` construye `ToonObject/ToonArray`.  
   - Conversión opcional a `JsonNode`/`Map` para interoperar con bibliotecas JSON externas.

2. **Entrada JSON → TOON**  
   - `ToonEncoder` recibe estructuras JSON (p.ej. `JsonNode`, `Map`, `List`).  
   - Genera nodos `Toon*` y los serializa mediante `ToonWriter` respetando opciones (`delimiter`, `indent`, `lengthMarker`, `strict`).  
   - `ToonStringer` habilita construcción incremental cuando se necesite.

Gramática y ANTLR
-----------------
- La gramática ABNF de `SPEC.md` se transpila a un `.g4` maestro.  
- Se genera `ToonLexer` y `ToonParser` en build-time (Gradle/Maven + plugin ANTLR).  
- `ToonTokener` expone una API manual (métodos `nextValue()`, `nextObject()`, etc.) para que el resto del código no dependa directamente de ANTLR.
- El encabezado normativo (SPEC §6) está mapeado 1:1 en `src/main/antlr/org/toon/grammar/Toon.g4`; los tokens y reglas mantienen los mismos nombres lógicos que en el ABNF (`bracket-seg`, `fields-seg`, `delimsym`, etc.) para facilitar el rastreo de la spec.

Errores y validación
--------------------
- `ToonException` (unchecked) encapsula errores de parseo y validación; incluye información de contexto (línea/columna) obtenida del parser.  
- Se respeta el modo estricto (`strict: true`) y las validaciones de longitud, indentación y delimitadores descritas en la spec y en los fixtures.

Dependencias
------------
- Runtime sin dependencias externas; sólo `java.base`.  
- ANTLR se usa como dependencia de build para generar lexer/parser.  
- Pruebas usan JUnit 5 y utilidades estándar para cargar fixtures JSON.

Pruebas y conformidad
---------------------
- Suite unitaria basada en `tests/fixtures/encode` y `tests/fixtures/decode`.  
- Cada test se etiqueta con sección de la spec correspondiente para trazabilidad.  
- Cobertura adicional con ejemplos de `examples/` y pruebas de round-trip.

Contribución
------------
- Cambios deben preservar simplicidad, token efficiency y compatibilidad.  
- Documentar cualquier ampliación en `PROJECT_OVERVIEW.md` y validar contra la suite de fixtures antes de proponer PR.
