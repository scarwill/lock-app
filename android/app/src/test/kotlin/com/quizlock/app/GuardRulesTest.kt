package com.quizlock.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuardRulesTest {
    private val label = "Quiz Lock"

    @Test
    fun blocksScreensThatRemoveOrDisableTheApp() {
        assertTrue(GuardRules.shouldBlock(listOf("App info", "Quiz Lock", "Uninstall", "Force stop"), label))
        assertTrue(GuardRules.shouldBlock(listOf("Quiz Lock", "Do you want to uninstall this app?"), label))
        assertTrue(GuardRules.shouldBlock(listOf("Quiz Lock protection", "Use Quiz Lock protection"), label))
        assertTrue(GuardRules.shouldBlock(listOf("Usage access", "Quiz Lock", "Permit usage access"), label))
        assertTrue(GuardRules.shouldBlock(listOf("Device admin apps", "Quiz Lock"), label))
    }

    @Test
    fun leavesOtherScreensAlone() {
        assertFalse(GuardRules.shouldBlock(listOf("Instagram", "Uninstall", "Force stop"), label))
        assertFalse(GuardRules.shouldBlock(listOf("Apps", "Quiz Lock", "YouTube"), label))
        assertFalse(GuardRules.shouldBlock(listOf("Accessibility", "Downloaded apps", "Quiz Lock protection", "On"), label))
    }

    @Test
    fun watchesSettingsInstallersAndLaunchers() {
        assertTrue(GuardRules.isWatched("com.android.settings", emptySet()))
        assertTrue(GuardRules.isWatched("com.google.android.packageinstaller", emptySet()))
        assertTrue(GuardRules.isWatched("com.miui.home", setOf("com.miui.home")))
        assertFalse(GuardRules.isWatched("com.instagram.android", setOf("com.miui.home")))
    }

    @Test
    fun onlyParentModeOrKidsLockIsProtected() {
        assertFalse(GuardRules.protects(lockOn = true, parentMode = false, kidsModeOn = false))
        assertTrue(GuardRules.protects(lockOn = true, parentMode = true, kidsModeOn = false))
        assertFalse(GuardRules.protects(lockOn = false, parentMode = true, kidsModeOn = false))
        assertTrue(GuardRules.protects(lockOn = false, parentMode = false, kidsModeOn = true))
    }
}
