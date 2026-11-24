package com.kenznguyen.smartadskip.ai

/** Pure identity guards shared by Binder callbacks, handshakes, and watchdog decisions. */
internal object NativeProcessSessionPolicy {
    fun isCurrentGeneration(callbackGeneration: Long, currentGeneration: Long): Boolean =
        callbackGeneration == currentGeneration

    fun isValidHandshake(
        responseGeneration: Long,
        currentGeneration: Long,
        remotePid: Int,
        localPid: Int
    ): Boolean =
        isCurrentGeneration(responseGeneration, currentGeneration) &&
            remotePid > 0 && remotePid != localPid

    fun isSameProcess(expectedSession: String?, actualSession: String?): Boolean =
        !expectedSession.isNullOrBlank() &&
            !actualSession.isNullOrBlank() &&
            expectedSession == actualSession

    fun mayTerminate(
        expectedGeneration: Long,
        currentGeneration: Long,
        expectedPid: Int,
        currentPid: Int,
        expectedSession: String?,
        currentSession: String?
    ): Boolean =
        isCurrentGeneration(expectedGeneration, currentGeneration) &&
            expectedPid > 0 && expectedPid == currentPid &&
            isSameProcess(expectedSession, currentSession)
}
