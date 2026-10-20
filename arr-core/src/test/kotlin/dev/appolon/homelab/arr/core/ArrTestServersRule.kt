package dev.appolon.homelab.arr.core

import org.junit.AssumptionViolatedException
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * JUnit4 rule for live-server tests backed by `local.arr-test.properties`.
 *
 * Register with `@get:Rule val servers = ArrTestServersRule()`. A missing
 * properties file skips the whole test; [require] additionally skips a test
 * when a specific app has no complete entry. Missing credentials are a skip,
 * never a failure.
 *
 * Lives in the test source set (JUnit is test-scoped); other modules use the
 * [ArrTestServers] reader directly with `Assume` when they want manual
 * control.
 */
class ArrTestServersRule : TestRule {

    override fun apply(base: Statement, description: Description): Statement =
        object : Statement() {
            override fun evaluate() {
                if (!ArrTestServers.available) {
                    throw AssumptionViolatedException(
                        ArrTestServers.FILE_NAME + " not present; skipping live-server test"
                    )
                }
                base.evaluate()
            }
        }

    /** The configured server for [app], or skips the test when unconfigured. */
    fun require(app: AppType): ArrTestServer =
        ArrTestServers.server(app) ?: throw AssumptionViolatedException(
            "no ${app.wireName} entry in " + ArrTestServers.FILE_NAME
        )

    /** The configured server for [app], or null without skipping. */
    fun get(app: AppType): ArrTestServer? = ArrTestServers.server(app)

    val radarr: ArrTestServer?
        get() = get(AppType.RADARR)

    val sonarr: ArrTestServer?
        get() = get(AppType.SONARR)

    val lidarr: ArrTestServer?
        get() = get(AppType.LIDARR)

    val prowlarr: ArrTestServer?
        get() = get(AppType.PROWLARR)

    val bazarr: ArrTestServer?
        get() = get(AppType.BAZARR)
}
