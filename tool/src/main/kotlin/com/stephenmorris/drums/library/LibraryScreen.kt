package com.stephenmorris.drums.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.stephenmorris.drums.core.PatternData
import com.stephenmorris.drums.pattern.PatternExit
import com.stephenmorris.drums.pattern.PatternScreen
import com.thelightphone.sdk.InitialScreen
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightIcon
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import kotlinx.coroutines.launch

/** The pattern library (spec §7): every pattern, oldest first, with EDIT and ⊕ underneath. */
@InitialScreen
class LibraryScreen(sealedActivity: SealedLightActivity) : LightScreen<Unit, LibraryViewModel>(sealedActivity) {

    override val viewModelClass: Class<LibraryViewModel>
        get() = LibraryViewModel::class.java

    override fun createViewModel(): LibraryViewModel = LibraryViewModel(lightContext)

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val model by viewModel.model.collectAsState()
        LightTheme(colors = themeColors) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background),
            ) {
                model?.let { Library(it) }
            }
        }
    }

    @Composable
    private fun Library(model: LibraryModel) {
        val patterns by model.patterns.collectAsState()
        val isEditing by model.isEditing.collectAsState()
        val scope = rememberCoroutineScope()

        fun open(uuid: String) {
            navigateTo({ PatternScreen(it, uuid) }) { exit ->
                // Duplicate hands back the copy to open; a delete just lands here.
                if (exit is PatternExit.Open) open(exit.uuid)
            }
        }

        fun open(pattern: PatternData) = open(pattern.uuid)

        fun confirmDelete(pattern: PatternData) = navigateTo({ DeleteConfirmScreen(it, pattern.name) }) { confirmed ->
            if (confirmed) {
                viewModel.delete(pattern.uuid)
            }
        }

        val add = LightBarButton.LightIcon(
            LightIcons.ADD,
            onClick = { scope.launch { open(model.create()) } },
            contentDescription = "New pattern",
        )
        Column(modifier = Modifier.fillMaxSize()) {
            LightScrollView(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(start = 1f.gridUnitsAsDp(), end = 1f.gridUnitsAsDp(), top = 1f.gridUnitsAsDp()),
            ) {
                if (patterns.isEmpty()) {
                    LightText("no patterns yet", LightTextVariant.Paragraph)
                }
                patterns.forEach { pattern ->
                    PatternRow(
                        pattern = pattern,
                        isEditing = isEditing,
                        onOpen = { open(pattern) },
                        onDelete = { confirmDelete(pattern) },
                    )
                }
                Spacer(Modifier.height(1f.gridUnitsAsDp()))
            }
            LightBottomBar(
                items = if (patterns.isEmpty()) {
                    listOf(null, add, null)
                } else {
                    listOf(LightBarButton.Text(if (isEditing) "DONE" else "EDIT", onClick = model::toggleEditing), null, add)
                },
            )
        }
    }
}

/**
 * One pattern: its name and meta line. In edit mode, a delete button sits before it, and tapping the row does nothing.
 * The text sets the row's height, so edit mode only moves it over: the delete button stretches to the row's height.
 */
@Composable
private fun PatternRow(pattern: PatternData, isEditing: Boolean, onOpen: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .lightClickable(enabled = !isEditing, onClick = onOpen)
            .padding(vertical = 0.5f.gridUnitsAsDp()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(0.5f.gridUnitsAsDp()),
    ) {
        if (isEditing) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(DELETE_SIZE_UNITS.gridUnitsAsDp())
                    .lightClickable(onClickLabel = "Delete ${pattern.name}", onClick = onDelete),
                contentAlignment = Alignment.Center,
            ) {
                LightIcon(LightIcons.DELETE, size = DELETE_SIZE_UNITS, contentDescription = "Delete ${pattern.name}")
            }
        }
        Column {
            LightText(pattern.name, LightTextVariant.Copy)
            LightText(pattern.libraryMeta, LightTextVariant.Detail)
        }
    }
}

/** The delete button's size, in grid units: about the name's height, smaller than the SDK's 2-unit default. */
private const val DELETE_SIZE_UNITS = 1.25f
