package com.bepinex.android

import org.junit.Assert.*
import org.junit.Test

class CrashDiagnosisTest {
    @Test fun javaCauseChainUsesDeepestCauseAndItsFrame() {
        val result = CrashDiagnosis.analyze("""
            time=now
            java.lang.RuntimeException: Unable to start activity
                at android.app.ActivityThread.performLaunchActivity(ActivityThread.java:100)
            Caused by: java.lang.IllegalStateException: load failed
                at game.Loader.load(Loader.java:20)
            Caused by: java.lang.UnsatisfiedLinkError: libexample.so not found
                at java.lang.Runtime.loadLibrary0(Runtime.java:1081)
        """.trimIndent(), "")
        assertEquals(CrashDiagnosis.Kind.JAVA, result.kind)
        assertEquals("java.lang.UnsatisfiedLinkError: libexample.so not found", result.exception)
        assertTrue(result.frame!!.contains("Runtime.java:1081"))
    }

    @Test fun nativeReportKeepsFaultAddressAbortMessageAndFrame() {
        val result = CrashDiagnosis.analyze("", """
            signal 6 (SIGABRT), code -1 (SI_QUEUE), fault addr --------
            Abort message: 'JNI DETECTED ERROR IN APPLICATION: use of deleted reference'
              #00 pc 000000000008b320 /apex/com.android.runtime/lib64/bionic/libc.so (abort+164)
              #01 pc 0000000000001234 /data/app/lib/arm64/libfusion.so (stage_fusion+12)
        """.trimIndent(), "CRASH_NATIVE", 6)
        assertEquals(CrashDiagnosis.Kind.NATIVE, result.kind)
        assertTrue(result.signal!!.contains("SI_QUEUE"))
        assertTrue(result.abortMessage!!.contains("deleted reference"))
        assertTrue(result.frame!!.contains("abort+164"))
    }

    @Test fun lowMemoryExitDoesNotBlameAnEarlierException() {
        val result = CrashDiagnosis.analyze("", """
            [Error :ExampleMod] Failed optional initialization
            System.OutOfMemoryException: allocation failed
            at ExampleMod.Start()
        """.trimIndent(), "LOW_MEMORY")
        assertEquals(CrashDiagnosis.Kind.LOW_MEMORY, result.kind)
        assertNull(result.exception)
        assertNull(result.frame)
    }

    @Test fun pluginErrorsAloneAreNotFatalEvidence() {
        val result = CrashDiagnosis.analyze("", "[Error :Plugin] System.NotSupportedException: optional feature", "EXIT_SELF")
        assertEquals(CrashDiagnosis.Kind.UNKNOWN, result.kind)
        assertNull(result.exception)
    }

    @Test fun managedUnhandledExceptionIsRecognized() {
        val result = CrashDiagnosis.analyze("", "Unhandled exception. System.InvalidOperationException: callback failed\n   at ExampleMod.OnClick()")
        assertEquals(CrashDiagnosis.Kind.MANAGED, result.kind)
        assertEquals("System.InvalidOperationException: callback failed", result.exception)
        assertEquals("at ExampleMod.OnClick()", result.frame)
    }

    @Test fun osNativeExitProvidesSignalWithoutLogcat() {
        val result = CrashDiagnosis.analyze("", "", "CRASH_NATIVE", 11)
        assertEquals(CrashDiagnosis.Kind.NATIVE, result.kind)
        assertEquals("SIGSEGV (11)", result.signal)
        assertNull(result.frame)
    }

    @Test fun signalKillDoesNotClaimMemoryExhaustion() {
        val result = CrashDiagnosis.analyze("", "", "SIGNALED", 9)
        assertEquals(CrashDiagnosis.Kind.SIGNAL, result.kind)
        assertEquals("SIGKILL (9)", result.signal)
    }

    @Test fun logcatFilteringRejectsOldSessionsOtherProcessesAndOtherTombstones() {
        val log = """
            1700000000.000 123 123 F libc: Fatal signal 6 (SIGABRT)
            1700000002.000 456 456 E AndroidRuntime: FATAL EXCEPTION: other game
            1700000002.001 123 123 E AndroidRuntime: FATAL EXCEPTION: main
            1700000002.002 123 123 E AndroidRuntime: java.lang.IllegalStateException: current
            1700000002.003 123 123 E AndroidRuntime:     at game.Current.run(Current.java:5)
            1700000002.004 789 789 F DEBUG: *** *** ***
            1700000002.005 789 789 F DEBUG: pid: 456, tid: 456, name: other  >>> other:game <<<
            1700000002.006 789 789 F DEBUG: Abort message: unrelated
            1700000002.007 789 789 F DEBUG: *** *** ***
            1700000002.008 789 789 F DEBUG: pid: 123, tid: 123, name: game  >>> launcher:game <<<
            1700000002.009 789 789 F DEBUG: signal 11 (SIGSEGV), code 1 (SEGV_MAPERR), fault addr 0x0
            1700000002.010 789 789 F DEBUG: #00 pc 00000010 /data/libexample.so (foo+4)
        """.trimIndent()
        val selected = CrashDiagnosis.selectSessionLogcat(log, 123, "launcher:game", 1700000001000)
        assertFalse(selected.contains("SIGABRT"))
        assertFalse(selected.contains("other game"))
        assertFalse(selected.contains("unrelated"))
        assertTrue(selected.contains("libexample.so"))
        val result = CrashDiagnosis.analyze("", selected, "CRASH")
        assertEquals("java.lang.IllegalStateException: current", result.exception)
        assertEquals("at game.Current.run(Current.java:5)", result.frame)
        assertEquals("", CrashDiagnosis.selectSessionLogcat(log, 0, "launcher:game", 0))
    }
}
