package com.baoverung.app.gis

/**
 * Standardized TCVN3 to UTF-8 Converter for forestry GIS labels.
 * Ported to Kotlin Multiplatform.
 */
object MapInfoEncodingConverter {

    private val TCVN3_MAP = mapOf(
        // Lowercase
        '\u00B8' to 'à', '\u00B5' to 'ả', '\u00B6' to 'ã', '\u00B7' to 'á', '\u00B9' to 'ạ',
        '\u00BB' to 'ầ', '\u00BC' to 'ẩ', '\u00BD' to 'ẫ', '\u00BE' to 'ấ', '\u00C1' to 'ậ',
        '\u00C4' to 'ằ', '\u00C5' to 'ẳ', '\u00C6' to 'ẵ', '\u00C7' to 'ắ', '\u00C8' to 'ặ',
        '\u00E1' to 'è', '\u00E2' to 'ẻ', '\u00E3' to 'ẽ', '\u00E4' to 'é', '\u00E5' to 'ẹ',
        '\u00E7' to 'ề', '\u00E8' to 'ể', '\u00E9' to 'ễ', '\u00EA' to 'ế', '\u00EB' to 'ệ',
        '\u00CC' to 'ì', '\u00CE' to 'ỉ', '\u00EF' to 'ĩ', '\u00D2' to 'í', '\u00F1' to 'ị',
        '\u00F2' to 'ò', '\u00F3' to 'ỏ', '\u00F4' to 'õ', '\u00F5' to 'ó', '\u00F6' to 'ọ',
        '\u00F8' to 'ồ', '\u00F9' to 'ổ', '\u00FA' to 'ỗ', '\u00FB' to 'ố', '\u00FC' to 'ộ',
        '\u00FE' to 'ờ', '\u00FF' to 'ở', '\u0102' to 'ỡ', '\u0103' to 'ớ', '\u0104' to 'ợ',
        '\u0106' to 'ủ', '\u0107' to 'ũ', '\u0108' to 'ú', '\u0109' to 'ụ',
        '\u0112' to 'ừ', '\u0113' to 'ử', '\u0114' to 'ữ', '\u0115' to 'ứ', '\u0116' to 'ự',
        '\u0118' to 'ỷ', '\u0119' to 'ỹ', '\u011A' to 'ý', '\u011B' to 'ỵ',
        '\u0111' to 'ư', '\u0110' to 'đ',
        
        // Uppercase & Special Base
        '\u00A1' to 'Ă', '\u00A2' to 'Ê', '\u00A3' to 'Ô', '\u00A4' to 'Ơ', '\u00A5' to 'Ư',
        '\u00AA' to 'Â', '\u00CA' to 'Đ',
        
        // Vowel bases (when not tone-marked, these often appear as-is)
        '\u00E6' to 'ê', '\u00F7' to 'ô', '\u00FD' to 'ơ', '\u0105' to 'ù', '\u00D7' to 'í'
    )

    fun decode(input: String): String {
        if (input.isEmpty()) return ""
        val sb = StringBuilder()
        for (char in input) {
            sb.append(TCVN3_MAP[char] ?: char)
        }
        return sb.toString()
    }
}
