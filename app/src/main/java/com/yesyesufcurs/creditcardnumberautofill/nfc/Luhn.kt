package com.yesyesufcurs.creditcardnumberautofill.nfc

object Luhn {

    fun isValid(number: String): Boolean {
        val n = number.trim()
        if (n.isEmpty() || n.length > 19) return false
        var sum = 0
        var double = false
        for (i in n.length - 1 downTo 0) {
            val c = n[i]
            if (!c.isDigit()) return false
            var d = c - '0'
            if (double) {
                d *= 2
                if (d > 9) d -= 9
            }
            sum += d
            double = !double
        }
        return sum % 10 == 0
    }
}
