package dev.appolon.homelab.arr.net

/** Application policy for feature connections that construct their own factory. */
object ArrNetworkDefaults {
    @Volatile
    var cookieSource: CookieSource = CookieSource.None

    @Volatile
    var globalProxy: () -> ProxyConfig? = { null }
}
