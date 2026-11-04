package org.chmreader.core

import org.junit.Assert.*
import org.junit.Test

class UpdatePolicyTest {
    private val policy=UpdatePolicy("Kreigen8/chm-reader-android","org.chmreader")
    private fun release()=ReleaseUpdate(3,"1.2.0","org.chmreader","https://github.com/Kreigen8/chm-reader-android/releases/download/v1.2.0/CHM-Reader-1.2.0.apk","a".repeat(64),1000000,"Исправления")
    @Test fun validReleaseAndGitHubRedirectsAreAccepted() {
        assertEquals(release(),policy.validate(release()))
        assertTrue(policy.allowNetworkUrl("https://release-assets.githubusercontent.com/github-production-release-asset/123?token=example"))
        assertEquals("https://github.com/Kreigen8/chm-reader-android/releases/latest/download/update.json",policy.manifestUrl)
    }
    @Test fun otherRepositoriesAndUnsafeDownloadsAreRejected() {
        for(url in listOf("http://github.com/Kreigen8/chm-reader-android/releases/download/v1.2.0/a.apk","https://github.com.evil.org/Kreigen8/chm-reader-android/releases/download/v1.2.0/a.apk","https://github.com/attacker/chm-reader-android/releases/download/v1.2.0/a.apk","https://github.com/Kreigen8/chm-reader-android/releases/download/../a.apk","https://github.com/Kreigen8/chm-reader-android/releases/download/v1/a.apk?redirect=x","file:///storage/a.apk")) {
            assertTrue(url,runCatching { policy.validate(release().copy(apkUrl=url)) }.isFailure)
        }
        assertFalse(policy.allowNetworkUrl("https://release-assets.githubusercontent.com.evil.org/a"))
        assertFalse(policy.allowNetworkUrl("https://user:password@github.com/a"))
        assertFalse(policy.allowNetworkUrl("http://github.com/a"))
    }
    @Test fun wrongPackageOversizeMissingHashAndInvalidVersionsAreRejected() {
        for(r in listOf(release().copy(packageName="other.app"),release().copy(size=0),release().copy(size=policy.maxApkBytes+1),release().copy(sha256="not-a-hash"),release().copy(versionCode=0),release().copy(versionName=""))) assertTrue(runCatching { policy.validate(r) }.isFailure)
    }
}
