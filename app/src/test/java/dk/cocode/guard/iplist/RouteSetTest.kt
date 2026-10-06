package dk.cocode.guard.iplist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RouteSetTest {
    private fun cidr(text: String) = parseCidr(text)!!

    private fun ip(text: String) = cidr(text + if (':' in text) "/128" else "/32").address

    private val drop = listOf(cidr("203.0.113.0/24"))
    private val feodo = listOf(cidr("198.51.100.7/32"))

    @Test
    fun findsTheList() {
        val routes = RouteSet(mapOf("drop_v4" to drop, "feodo" to feodo))
        assertEquals("feodo", routes.listFor(ip("198.51.100.7")))
        assertEquals("drop_v4", routes.listFor(ip("203.0.113.200")))
    }

    @Test
    fun addressOutsideIsNull() {
        assertNull(RouteSet(mapOf("drop_v4" to drop, "feodo" to feodo)).listFor(ip("198.51.100.8")))
    }

    @Test
    fun ipv6Lookup() {
        val routes = RouteSet(mapOf("drop_v6" to listOf(cidr("2001:db8::/32"))))
        assertEquals("drop_v6", routes.listFor(ip("2001:db8::1")))
        assertNull(routes.listFor(ip("2001:db9::1")))
    }

    @Test
    fun routesMergeAcrossLists() {
        val routes = RouteSet(mapOf("drop_v4" to drop, "feodo" to listOf(cidr("203.0.113.9/32"))))
        assertEquals(listOf("203.0.113.0/24"), routes.routes.map { it.toString() })
    }

    @Test
    fun membershipChangeMakesSetsDiffer() {
        val a = RouteSet(mapOf("drop_v4" to drop))
        assertEquals(a, RouteSet(mapOf("drop_v4" to listOf(cidr("203.0.113.0/24")))))
        // Same merged routes, but the address is now attributed to another list.
        org.junit.Assert.assertNotEquals(a, RouteSet(mapOf("feodo" to drop)))
    }

    @Test
    fun sizeCountsMergedRoutes() {
        val routes = RouteSet(
            mapOf("drop_v4" to drop + cidr("203.0.113.0/25"), "feodo" to feodo, "drop_v6" to listOf(cidr("2001:db8::/32"))),
        )
        assertEquals(3, routes.size)
    }
}
