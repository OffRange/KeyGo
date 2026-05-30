# The devnied emvnfccard library logs through slf4j-api but ships no slf4j
# binding, so org.slf4j.impl.StaticLoggerBinder is absent at build time. slf4j
# degrades to a no-op logger at runtime; tell R8 not to fail on the missing class.
-dontwarn org.slf4j.**
