package dk.cocode.guard.vpn

/**
 * Replaces a running tunnel without a gap: the new tunnel comes up and its loop starts first, then
 * the old loop stops and only then does its tunnel close. Null when [establish] gives no tunnel; the
 * old one is then left as it was, for the caller to stop protection and clean up. Android-free, so
 * the order is unit-tested; the service plugs in its descriptors and packet loops.
 */
internal fun <T : AutoCloseable, L> replaceTunnel(
    oldTunnel: T?,
    oldLoop: L?,
    establish: () -> T?,
    startLoop: (T) -> L,
    stopLoop: (L) -> Unit,
): Pair<T, L>? {
    val tunnel = establish() ?: return null
    val loop = try {
        startLoop(tunnel)
    } catch (e: Exception) {
        tunnel.close() // never leave a second tunnel open that nothing reads
        throw e
    }
    oldLoop?.let(stopLoop)
    oldTunnel?.close()
    return tunnel to loop
}
