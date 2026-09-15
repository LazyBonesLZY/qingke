package cn.edu.gzus.qingke.data

import java.math.BigInteger

internal fun lyuapEncryptJvm(password: String, modulusHex: String, exponentHex: String): String {
    val modulus = BigInteger(modulusHex, 16)
    val exponent = BigInteger(exponentHex, 16)
    val highDigit = ((modulus.bitLength() + 15) / 16).coerceAtLeast(1) - 1
    val chunkSize = 2 * highDigit
    val codes = password.map { it.code } + List((chunkSize - password.length % chunkSize) % chunkSize) { 0 }
    return codes.chunked(chunkSize).joinToString(" ") { chunk ->
        var block = BigInteger.ZERO
        var shift = 0
        var index = 0
        while (index < chunk.size) {
            var word = chunk[index]
            index += 1
            if (index < chunk.size) {
                word += chunk[index] shl 8
                index += 1
            }
            block = block.add(BigInteger.valueOf(word.toLong()).shiftLeft(shift))
            shift += 16
        }
        val crypt = block.modPow(exponent, modulus)
        crypt.toString(16).padStart((highDigit + 1) * 4, '0')
    }
}
