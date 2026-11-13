package com.stephenmorris.drums.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.stephenmorris.drums.core.PatternData
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

        fun open(pattern: PatternData) = navigateTo({ PatternScreen(it, pattern.uuid) })

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

/** One pattern: its name and meta line. In edit mode, a delete button sits before it, and tapping the row does nothing. */
@Composable
private fun PatternRow(pattern: PatternData, isEditing: Boolean, onOpen: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .lightClickable(enabled = !isEditing, onClick = onOpen)
            .padding(vertical = 0.5f.gridUnitsAsDp()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(0.5f.gridUnitsAsDp()),
    ) {
        if (isEditing) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .lightClickable(onClickLabel = "Delete ${pattern.name}", onClick = onDelete),
                contentAlignment = Alignment.Center,
            ) {
                LightIcon(LightIcons.DELETE, contentDescription = "Delete ${pattern.name}")
            }
        }
        Column {
            LightText(pattern.name, LightTextVariant.Copy)
            LightText(pattern.libraryMeta, LightTextVariant.Detail)
        }
    }
}
