package com.locus.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import com.locus.app.navigation.LocusNavGraph
import com.locus.app.theme.LocusTheme
import com.locus.core.domain.notes.NoteRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var noteRepository: NoteRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LaunchedEffect(Unit) {
                runCatching {
                    noteRepository.rescan()
                }
            }
            LocusTheme {
                LocusNavGraph()
            }
        }
    }
}
