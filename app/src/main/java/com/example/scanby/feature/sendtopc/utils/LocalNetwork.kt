package com.example.scanby.feature.sendtopc.utils

import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

/**
 * 같은 Wi-Fi(LAN)에 있는 다른 기기가 접속할 수 있는, 이 기기의 IPv4 주소를 찾는다.
 * `WifiManager.connectionInfo`(최신 Android에서는 대부분 위치 권한이 필요해짐) 대신 순수
 * 소켓 API인 `NetworkInterface` 순회를 쓴다 — 별도 권한이 필요 없다.
 */
fun getLocalIpv4Address(): String? {
    val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
    for (networkInterface in interfaces) {
        if (!networkInterface.isUp || networkInterface.isLoopback) continue
        val addresses = Collections.list(networkInterface.inetAddresses)
        for (address in addresses) {
            if (!address.isLoopbackAddress && address is Inet4Address) {
                return address.hostAddress
            }
        }
    }
    return null
}
