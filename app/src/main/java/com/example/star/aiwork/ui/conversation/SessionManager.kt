package com.example.star.aiwork.ui.conversation

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.star.aiwork.domain.model.SessionEntity

@Composable
fun SessionManager(
    viewModel: ChatViewModel
) {
    val sessions by viewModel.sessions.collectAsState()

    LazyColumn {
        items(sessions) { session ->
            SessionItem(session = session)
        }
    }
}

@Composable
fun SessionItem(session: SessionEntity) {
    Text(text = session.name)
}
