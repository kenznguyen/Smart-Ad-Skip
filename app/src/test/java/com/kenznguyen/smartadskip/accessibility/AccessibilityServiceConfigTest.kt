package com.kenznguyen.smartadskip.accessibility

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityServiceConfigTest {
    @Test
    fun manifestRegistersScreenshotCapableAccessibilityService() {
        val manifest = parseXml("src/main/AndroidManifest.xml")
        val services = manifest.getElementsByTagName("service")
        var serviceFound = false

        for (index in 0 until services.length) {
            val service = services.item(index) as? org.w3c.dom.Element ?: continue
            if (service.getAttributeNS(ANDROID_NAMESPACE, "name") !=
                ".accessibility.SmartAdSkipAccessibilityService"
            ) continue

            serviceFound = true
            assertEquals(
                "android.permission.BIND_ACCESSIBILITY_SERVICE",
                service.getAttributeNS(ANDROID_NAMESPACE, "permission")
            )
            val metadata = service.getElementsByTagName("meta-data")
            var configReference: String? = null
            for (metadataIndex in 0 until metadata.length) {
                val element = metadata.item(metadataIndex) as? org.w3c.dom.Element ?: continue
                if (element.getAttributeNS(ANDROID_NAMESPACE, "name") ==
                    "android.accessibilityservice"
                ) {
                    configReference = element.getAttributeNS(ANDROID_NAMESPACE, "resource")
                    break
                }
            }
            assertEquals("@xml/accessibility_service_config", configReference)
        }

        assertTrue("Accessibility service must be registered in the manifest", serviceFound)
    }

    @Test
    fun accessibilityConfigDeclaresScreenshotAndWindowContentCapabilities() {
        val config = parseXml("src/main/res/xml/accessibility_service_config.xml")
        val service = config.documentElement

        assertNotNull(service)
        assertEquals("accessibility-service", service.tagName)
        assertEquals(
            "true",
            service.getAttributeNS(ANDROID_NAMESPACE, "canRetrieveWindowContent")
        )
        assertEquals(
            "true",
            service.getAttributeNS(ANDROID_NAMESPACE, "canTakeScreenshot")
        )
    }

    private fun parseXml(path: String): org.w3c.dom.Document {
        val file = File(path)
        assertTrue("Expected Android configuration file at ${file.absolutePath}", file.isFile)
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        }
        return factory.newDocumentBuilder().parse(file)
    }

    private companion object {
        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
    }
}
