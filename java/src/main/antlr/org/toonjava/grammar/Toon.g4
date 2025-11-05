grammar Toon;

@header {
package org.toonjava.grammar;
}

/**
 * Mapeo directo de la gramática ABNF de los encabezados TOON (SPEC §6)
 * a reglas ANTLR.
 *
 * ABNF                           ANTLR
 * -----                          -----
 * header        = [ key ] ...    → header
 * key           = ...            → key
 * bracket-seg   = "[" ... "]"    → bracketSegment
 * fields-seg    = "{" ... "}"    → fieldsSegment
 * fieldname     = key            → fieldName
 * delimsym      = HTAB / "|"     → delimsym
 * delim         = delimsym / "," → delimiter
 * unquoted-key  = ...            → UNQUOTED_KEY token
 * quoted-key    = ...            → STRING token
 */

header
    : key? bracketSegment fieldsSegment? COLON EOF
    ;

key
    : UNQUOTED_KEY
    | STRING
    ;

bracketSegment
    : LBRACK HASH? DIGITS delimsym? RBRACK
    ;

fieldsSegment
    : LBRACE fieldName (delimiter fieldName)* RBRACE
    ;

fieldName
    : key
    ;

delimsym
    : TAB
    | PIPE
    ;

delimiter
    : delimsym
    | COMMA
    ;

LBRACK: '[';
RBRACK: ']';
LBRACE: '{';
RBRACE: '}';
COLON: ':';
COMMA: ',';
PIPE: '|';
HASH: '#';
TAB: '\t';

STRING
    : '"' (ESC | ~["\\\r\n])* '"'
    ;

fragment ESC
    : '\\' ["\\nrt]
    ;

DIGITS
    : DIGIT+
    ;

fragment DIGIT
    : [0-9]
    ;

UNQUOTED_KEY
    : [A-Za-z_] [A-Za-z0-9_.]*
    ;

WS
    : [ \r\n]+ -> skip
    ;
