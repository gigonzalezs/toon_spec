Objetivo:
Crear una port de la libreria org.json para java, para que en vez de trabajar con JSON, trabaje con TOON. se llamara 'toon-java'.

Que es TOON:
TOON es Token-Oriented Object Notation (TOON), formato compacto y legible orientado a pasar datos estructurados a LLMs reduciendo el uso de tokens

Arquitectura:
Estructura de paquetes ligera: org.json se organiza en un único paquete plano (org.json) donde conviven todas las clases públicas principales. No hay submódulos ni jerarquía profunda; esto facilita entender dependencias y reduce el coste de mantenimiento. Para toon-java, replicarías este layout con clases como ToonObject, ToonArray, ToonTokener, etc., todas en un paquete simple (org.toonjava), evitando división artificial en encoder/decoder hasta que la funcionalidad lo requiera.

Modelo de datos centrado en contenedores dinámicos:

JSONObject encapsula un Map<String, Object> y provee APIs de acceso tipadas (getString, optInt, etc.) apoyándose en casting.
JSONArray delega en un List<Object> para elementos heterogéneos.
Los valores admitidos son tipos Java nativos (String, Number, Boolean, JSONObject, JSONArray, JSONObject.NULL). No existe binding con POJOs ni generics avanzados.
Para TOON, la idea sería ofrecer contenedores análogos (ToonObject, ToonArray) que trabajen sobre estructuras básicas, mapeando valores TOON → tipos JSON primarios y viceversa, sin intentar convertirlos a clases de dominio.
Tokenizador sencillo (push-based): JSONTokener consume carácter a carácter, manteniendo índices y métodos utilitarios (nextValue, nextString, skipTo). No usa generadores de parsers; la gramática se implementa manualmente con condicionales. Para TOON quieres un ToonTokener que aplique la gramática derivada del .g4, pero conservando la filosofía: estado mínimo, lectura secuencial, sin dependencias externas. Incluso si ANTLR genera las clases, puedes envolverlas en un adaptador minimalista que exponga métodos tipo nextValue() y de paso valide indentación, delimitadores y encabezados tabulares.

Conversión a texto y desde texto sin configuración extra: JSONObject.toString() y JSONArray.toString() serializan directamente con reglas fijas (indent opcional). La deserialización parte del Tokener y construye el modelo en memoria. No hay concepto de ObjectMapper configurable. En TOON replicarías dos caminos puros:

ToonString → ToonArray/ToonObject → exportable como cadena o JSON (String).
JSON (String/Reader) → Toon* → String TOON.
Cualquier opción (delimitador, indentación, length marker) puede exponerse como parámetros en los métodos toString(int indentFactor, String delimiter, ...) sin gestores globales.
Clases utilitarias complementarias:

JSONStringer y JSONString ayudan a construir JSON incremental o proveer serialización personalizada.
JSONWriter gestiona escritura estructurada con indentación.
Para TOON, diseña utilidades equivalentes: un ToonStringer para construir secuencias tabulares/indentadas programáticamente, un ToonWriter que administre indentación y conteos de filas, y quizá una interfaz ToonString para objetos que sepan renderizarse a TOON.
Errores declarados con JSONException: Una única excepción unchecked encapsula fallos de parseo o acceso. Mantiene el código limpio y deja al consumidor decidir si captura o no. Para TOON, define ToonException con constructores que acepten mensajes y causas (incluye metadata como línea/columna cuando surja del parser ANTLR) para facilitar debugging.

Sin dependencias externas: El core org.json compila sólo con JDK estándar. Este aislamiento reduce superficie de ataque y simplifica distribución (solo un JAR). Replica esto manteniendo toon-java libre de dependencias en runtime (ANTLR necesaria solo para generar código en build); usa clases estándar (java.io, java.util) para I/O y colecciones.

Filosofía:
API mínima: Pocas clases, métodos cortos, sin patrones complejos. Refleja la idea de que la biblioteca es una capa de utilidad, no un framework. Documenta esta filosofía en README.md para que futuros colaboradores (humanos o agentes) entiendan que cualquier cambio debe preservar la simplicidad, ausencia de configuración global y dependencia cero.

Este esquema dará a toon-java una identidad familiar para desarrolladores Java acostumbrados a org.json y alineará el proyecto con la expectativa de la comunidad TOON de tener implementaciones simples, auditables y sin adornos.

Todo el desarrollo se hace dentro de la carpeta ./java
