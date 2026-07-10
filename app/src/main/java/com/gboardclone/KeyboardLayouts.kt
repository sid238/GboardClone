package com.gboardclone

object KeyboardLayouts {

    val QWERTY_LOWER = listOf(
        listOf(
            KeyData("q", "q"), KeyData("w", "w"), KeyData("e", "e"),
            KeyData("r", "r"), KeyData("t", "t"), KeyData("y", "y"),
            KeyData("u", "u"), KeyData("i", "i"), KeyData("o", "o"), KeyData("p", "p")
        ),
        listOf(
            KeyData("a", "a"), KeyData("s", "s"), KeyData("d", "d"),
            KeyData("f", "f"), KeyData("g", "g"), KeyData("h", "h"),
            KeyData("j", "j"), KeyData("k", "k"), KeyData("l", "l")
        ),
        listOf(
            KeyData("", "", type = KeyType.SHIFT, widthRatio = 1.3f),
            KeyData("z", "z"), KeyData("x", "x"), KeyData("c", "c"),
            KeyData("v", "v"), KeyData("b", "b"), KeyData("n", "n"),
            KeyData("m", "m"), KeyData("", "", type = KeyType.BACKSPACE, widthRatio = 1.3f)
        ),
        listOf(
            KeyData("?123", "", type = KeyType.NUMBERS, widthRatio = 1.3f),
            KeyData(",", ",", type = KeyType.COMMA),
            KeyData("", "", type = KeyType.SPACE, widthRatio = 5f),
            KeyData(".", ".", type = KeyType.PERIOD),
            KeyData("", "", type = KeyType.ENTER, widthRatio = 1.3f)
        )
    )

    val QWERTY_UPPER = listOf(
        listOf(
            KeyData("Q", "Q"), KeyData("W", "W"), KeyData("E", "E"),
            KeyData("R", "R"), KeyData("T", "T"), KeyData("Y", "Y"),
            KeyData("U", "U"), KeyData("I", "I"), KeyData("O", "O"), KeyData("P", "P")
        ),
        listOf(
            KeyData("A", "A"), KeyData("S", "S"), KeyData("D", "D"),
            KeyData("F", "F"), KeyData("G", "G"), KeyData("H", "H"),
            KeyData("J", "J"), KeyData("K", "K"), KeyData("L", "L")
        ),
        listOf(
            KeyData("", "", type = KeyType.SHIFT, widthRatio = 1.3f),
            KeyData("Z", "Z"), KeyData("X", "X"), KeyData("C", "C"),
            KeyData("V", "V"), KeyData("B", "B"), KeyData("N", "N"),
            KeyData("M", "M"), KeyData("", "", type = KeyType.BACKSPACE, widthRatio = 1.3f)
        ),
        listOf(
            KeyData("?123", "", type = KeyType.NUMBERS, widthRatio = 1.3f),
            KeyData(",", ",", type = KeyType.COMMA),
            KeyData("", "", type = KeyType.SPACE, widthRatio = 5f),
            KeyData(".", ".", type = KeyType.PERIOD),
            KeyData("", "", type = KeyType.ENTER, widthRatio = 1.3f)
        )
    )

    val NUMBERS_LAYOUT = listOf(
        listOf(
            KeyData("1", "1"), KeyData("2", "2"), KeyData("3", "3"),
            KeyData("4", "4"), KeyData("5", "5"), KeyData("6", "6"),
            KeyData("7", "7"), KeyData("8", "8"), KeyData("9", "9"), KeyData("0", "0")
        ),
        listOf(
            KeyData("-", "-", "/"), KeyData("/", "/", "\\"),
            KeyData(":", ":", ";"), KeyData("(", "(", "["),
            KeyData(")", ")", "]"), KeyData("$", "$", "€"),
            KeyData("&", "&"), KeyData("@", "@"), KeyData("\"", "\"", "'")
        ),
        listOf(
            KeyData("#", "#", "№"), KeyData("*", "*"),
            KeyData("+", "+", "-"), KeyData("=", "="),
            KeyData("%", "%"), KeyData("_", "_"),
            KeyData("?", "?", "¿"), KeyData("!", "!", "¡"),
            KeyData("", "", type = KeyType.BACKSPACE, widthRatio = 1.5f)
        ),
        listOf(
            KeyData("ABC", "", type = KeyType.ABC, widthRatio = 1.5f),
            KeyData(",", ",", type = KeyType.COMMA),
            KeyData("", "", type = KeyType.SPACE, widthRatio = 5f),
            KeyData(".", ".", type = KeyType.PERIOD),
            KeyData("", "", type = KeyType.ENTER, widthRatio = 1.5f)
        )
    )

    val SYMBOLS_LAYOUT = listOf(
        listOf(
            KeyData("1", "1"), KeyData("2", "2"), KeyData("3", "3"),
            KeyData("4", "4"), KeyData("5", "5"), KeyData("6", "6"),
            KeyData("7", "7"), KeyData("8", "8"), KeyData("9", "9"), KeyData("0", "0")
        ),
        listOf(
            KeyData("~", "~"), KeyData("`", "`"), KeyData("|", "|"),
            KeyData("·", "·"), KeyData("•", "•"), KeyData("√", "√"),
            KeyData("π", "π"), KeyData("÷", "÷"), KeyData("×", "×")
        ),
        listOf(
            KeyData("{", "{", "["), KeyData("}", "}", "]"),
            KeyData("<", "<"), KeyData(">", ">"),
            KeyData("©", "©", "®"), KeyData("™", "™"),
            KeyData("§", "§"), KeyData("¶", "¶"),
            KeyData("", "", type = KeyType.BACKSPACE, widthRatio = 1.5f)
        ),
        listOf(
            KeyData("ABC", "", type = KeyType.ABC, widthRatio = 1.5f),
            KeyData(",", ",", type = KeyType.COMMA),
            KeyData("", "", type = KeyType.SPACE, widthRatio = 5f),
            KeyData(".", ".", type = KeyType.PERIOD),
            KeyData("", "", type = KeyType.ENTER, widthRatio = 1.5f)
        )
    )
}