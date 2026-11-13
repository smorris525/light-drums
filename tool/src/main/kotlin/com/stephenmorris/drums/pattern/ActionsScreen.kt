package com.stephenmorris.drums.pattern

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.stephenmorris.drums.DrumsViewModel
import com.stephenmorris.drums.core.PatternData
import com.stephenmorris.drums.library.DeleteConfirmScreen
import com.stephenmorris.drums.session.PatternSession
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SealedLightContext
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The actions menu's view model: duplicate and delete, through the store and the saver. */
class ActionsViewModel(context: SealedLightContext) : DrumsViewModel<PatternExit?>(context) {
    /** A copy of [pattern] at the bottom of the library; the write finishes even if the screen goes. */
    suspend fun duplicate(pattern: PatternData): PatternData {
        val ready = ready.filterNotNull().first()
        return withContext(NonCancellable) { ready.store.duplicate(pattern) }
    }

    /** Stops the pattern if it's playing, and deletes it behind any unfinished saves. */
    suspend fun delete(uuid: String) {
        val ready = ready.filterNotNull().first()
        ready.transport.patternDeleted(uuid)
        ready.saver.delete(uuid)
    }
}

/**
 * Actions (spec §12): Duplicate and Delete for the pattern. Duplicate opens the copy; Delete asks first, then
 * returns to the library.
 */
class ActionsScreen(sealedActivity: SealedLightActivity, private val session: PatternSession) :
    LightScreen<PatternExit?, ActionsViewModel>(sealedActivity) {

    override val viewModelClass: Class<ActionsViewModel>
        get() = ActionsViewModel::class.java

    override fun createViewModel() = ActionsViewModel(lightContext)

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val pattern by session.pattern.collectAsState()
        val scope = rememberCoroutineScope()
        LightTheme(colors = themeColors) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background),
            ) {
                LightTopBar(
                    leftButton = LightBarButton.LightIcon(LightIcons.BACK, onClick = { goBack(null) }, contentDescription = "Back"),
                    center = LightTopBarCenter.Text(pattern.name),
                )
                Column(
                    modifier = Modifier.padding(horizontal = 1f.gridUnitsAsDp(), vertical = 1f.gridUnitsAsDp()),
                    verticalArrangement = Arrangement.spacedBy(1f.gridUnitsAsDp()),
                ) {
                    Action("Duplicate") {
                        scope.launch { goBack(PatternExit.Open(viewModel.duplicate(session.pattern.value).uuid)) }
                    }
                    Action("Delete") {
                        val current = session.pattern.value
                        navigateTo({ DeleteConfirmScreen(it, current.name) }) { confirmed ->
                            if (confirmed == true) {
                                viewModel.viewModelScopeLaunch { viewModel.delete(current.uuid) }
                                goBack(PatternExit.Deleted)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Action(label: String, onClick: () -> Unit) {
    LightText(
        label,
        LightTextVariant.Heading,
        modifier = Modifier.fillMaxWidth().lightClickable(onClickLabel = label, onClick = onClick),
    )
}
