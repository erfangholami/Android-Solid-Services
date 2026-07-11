package com.erfangholami.androidsolidservices.shared.vocab

import com.erfangholami.androidsolidservices.shared.vocab.XSD.LANG_STRING


/**
 * XML Schema Datatypes (XSD) vocabulary constants.
 * http://www.w3.org/2001/XMLSchema#
 *
 * Used to specify literal datatypes in RDF/Turtle documents on Solid pods
 * (e.g. `"2024-01-01"^^xsd:date`). [LANG_STRING] is an exception: it lives
 * in the RDF namespace but is grouped here for convenience.
 */
public object XSD {
    public const val NAMESPACE: String = "http://www.w3.org/2001/XMLSchema#"

    //String types
    public const val STRING: String = "${NAMESPACE}string"
    public const val NORMALIZED_STRING: String = "${NAMESPACE}normalizedString"
    public const val TOKEN: String = "${NAMESPACE}token"
    public const val LANGUAGE: String = "${NAMESPACE}language"
    public const val ANY_URI: String = "${NAMESPACE}anyURI"

    /** Language-tagged string — uses the RDF namespace, not XSD, but grouped here for convenience. */
    public const val LANG_STRING: String = "http://www.w3.org/1999/02/22-rdf-syntax-ns#langString"

    //Numeric types
    public const val INTEGER: String = "${NAMESPACE}integer"
    public const val LONG: String = "${NAMESPACE}long"
    public const val INT: String = "${NAMESPACE}int"
    public const val SHORT: String = "${NAMESPACE}short"
    public const val BYTE: String = "${NAMESPACE}byte"
    public const val DECIMAL: String = "${NAMESPACE}decimal"
    public const val FLOAT: String = "${NAMESPACE}float"
    public const val DOUBLE: String = "${NAMESPACE}double"
    public const val NON_NEGATIVE_INTEGER: String = "${NAMESPACE}nonNegativeInteger"
    public const val POSITIVE_INTEGER: String = "${NAMESPACE}positiveInteger"
    public const val NON_POSITIVE_INTEGER: String = "${NAMESPACE}nonPositiveInteger"
    public const val NEGATIVE_INTEGER: String = "${NAMESPACE}negativeInteger"
    public const val UNSIGNED_LONG: String = "${NAMESPACE}unsignedLong"
    public const val UNSIGNED_INT: String = "${NAMESPACE}unsignedInt"

    //Boolean
    public const val BOOLEAN: String = "${NAMESPACE}boolean"

    //Date/time types
    public const val DATE_TIME: String = "${NAMESPACE}dateTime"
    public const val DATE_TIME_STAMP: String = "${NAMESPACE}dateTimeStamp"
    public const val DATE: String = "${NAMESPACE}date"
    public const val TIME: String = "${NAMESPACE}time"
    public const val DURATION: String = "${NAMESPACE}duration"
    public const val YEAR_MONTH_DURATION: String = "${NAMESPACE}yearMonthDuration"
    public const val DAY_TIME_DURATION: String = "${NAMESPACE}dayTimeDuration"
    public const val G_YEAR: String = "${NAMESPACE}gYear"
    public const val G_MONTH: String = "${NAMESPACE}gMonth"
    public const val G_DAY: String = "${NAMESPACE}gDay"

    //Binary
    public const val BASE64_BINARY: String = "${NAMESPACE}base64Binary"
    public const val HEX_BINARY: String = "${NAMESPACE}hexBinary"

    /**
     * Returns the XSD datatype matching an ISO-8601 lexical [value]: [DATE_TIME] when
     * it carries a time component (`T` separator), [DATE] for a date-only value.
     *
     * Use this when persisting user-supplied ISO-8601 strings so date-only values are
     * not mistyped as `xsd:dateTime` (an ill-typed literal some servers reject).
     */
    public fun dateTypeFor(value: String): String =
        if (value.contains('T')) DATE_TIME else DATE
}
