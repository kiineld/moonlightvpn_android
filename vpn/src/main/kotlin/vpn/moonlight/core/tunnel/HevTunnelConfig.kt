package vpn.moonlight.core.tunnel

/**
 * The YAML handed to hev-socks5-tunnel.
 *
 * Written by hand rather than with a YAML library: it is a fixed five-key
 * document, and adding a serialisation dependency to emit it would be more code
 * than the document itself.
 */
object HevTunnelConfig {

    /**
     * @param mtu must match the MTU set on the VpnService builder, or large
     *  packets are silently dropped.
     */
    fun yaml(
        socksPort: Int,
        mtu: Int,
        socksAddress: String = "127.0.0.1",
        ipv4: String = "198.18.0.1",
        ipv6: String = "fc00::1",
        logLevel: String = "warn",
    ): String = """
        tunnel:
          mtu: $mtu
          ipv4: $ipv4
          ipv6: '$ipv6'
        socks5:
          port: $socksPort
          address: '$socksAddress'
          udp: 'udp'
        misc:
          task-stack-size: 20480
          connect-timeout: 10000
          log-level: '$logLevel'
    """.trimIndent() + "\n"
}
