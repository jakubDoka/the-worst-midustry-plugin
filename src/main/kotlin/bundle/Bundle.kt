package bundle

import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.*

// bundle.Bundle handles simple string loading and also holds custom bundle control
class Bundle(locale: String = "en_US") {
    companion object {
        val defaultBundle = Bundle()
        val bundlePath = "TWS.bundle"
    }

    val bundle: ResourceBundle

    init {
        bundle = ResourceBundle.getBundle(bundlePath, Locale(locale), UTF8Control())
    }

    // get returns key from called bundle or default bundle or returns message that key is missing
    fun get(key: String): String {
        return if (bundle.containsKey(key))
            bundle.getString(key)
        else if (defaultBundle.bundle.containsKey(key))
            defaultBundle.bundle.getString(key)
        else
            "bundle key $key is missing, so be quiet"
    }

    // copied from stack overflow
    class UTF8Control : ResourceBundle.Control() {
        @Throws(IllegalAccessException::class, InstantiationException::class, IOException::class)
        override fun newBundle(baseName: String, locale: Locale, format: String, loader: ClassLoader, reload: Boolean): ResourceBundle {
            // The below is a copy of the default implementation.
            val bundleName = toBundleName(baseName, locale)
            val resourceName = toResourceName(bundleName, "properties")
            var bundle: ResourceBundle? = null
            var stream: InputStream? = null
            if (reload) {
                val url = loader.getResource(resourceName)
                if (url != null) {
                    val connection = url.openConnection()
                    if (connection != null) {
                        connection.useCaches = false
                        stream = connection.getInputStream()
                    }
                }
            } else {
                stream = loader.getResourceAsStream(resourceName)
            }
            if (stream != null) {
                bundle = try {
                    // Only this line is changed to make it to read properties files as UTF-8.
                    PropertyResourceBundle(InputStreamReader(stream, StandardCharsets.UTF_8))
                } finally {
                    stream.close()
                }
            }
            return bundle!!
        }
    }
}

