package com.example.timerapp

import kotlinx.coroutines.flow.Flow

class SessionRepository(private val sessionDao: SessionDao) {
    val allSessions: Flow<List<SessionEntity>> = sessionDao.getAllSessions()

    fun getSessionsSince(since: Long): Flow<List<SessionEntity>> = 
        sessionDao.getSessionsSince(since)

    suspend fun insert(session: SessionEntity) {
        sessionDao.insertSession(session)
    }
}
