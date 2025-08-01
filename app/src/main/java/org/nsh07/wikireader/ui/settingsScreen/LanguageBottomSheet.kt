package org.nsh07.wikireader.ui.settingsScreen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.shapes
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.nsh07.wikireader.R
import org.nsh07.wikireader.data.LanguageData.langCodes
import org.nsh07.wikireader.data.LanguageData.langNames
import org.nsh07.wikireader.data.LanguageData.wikipediaNames
import org.nsh07.wikireader.data.langCodeToName
import org.nsh07.wikireader.ui.theme.WRShapeDefaults.bottomListItemShape
import org.nsh07.wikireader.ui.theme.WRShapeDefaults.middleListItemShape
import org.nsh07.wikireader.ui.theme.WRShapeDefaults.topListItemShape
import org.nsh07.wikireader.ui.theme.WikiReaderTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageBottomSheet(
    lang: String,
    searchStr: String,
    searchQuery: String,
    setShowSheet: (Boolean) -> Unit,
    setLang: (String) -> Unit,
    setSearchStr: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedOption by remember { mutableStateOf(langCodeToName(lang)) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val bottomSheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = {
            setShowSheet(false)
            setSearchStr("")
        },
        sheetState = bottomSheetState,
        modifier = modifier
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.chooseWikipediaLanguage),
                style = MaterialTheme.typography.labelLarge
            )
            LanguageSearchBar(
                searchStr = searchStr,
                setSearchStr = setSearchStr,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(shapes.large)
            ) {
                itemsIndexed(
                    langNames,
                    key = { _: Int, it: String -> it }
                ) { index: Int, it: String ->
                    if (it.contains(searchQuery, ignoreCase = true)) {
                        ListItem(
                            headlineContent = {
                                Text(it)
                            },
                            supportingContent = { Text(wikipediaNames[index]) },
                            trailingContent = {
                                if (selectedOption == it) Icon(
                                    Icons.Outlined.Check,
                                    contentDescription = stringResource(R.string.selectedLabel)
                                )
                            },
                            colors =
                                if (selectedOption == it) ListItemDefaults.colors(containerColor = colorScheme.primaryContainer)
                                else ListItemDefaults.colors(),
                            modifier = Modifier
                                .clip(
                                    if (index == 0) topListItemShape
                                    else if (index == langNames.size - 1) bottomListItemShape
                                    else middleListItemShape
                                )
                                .clickable(
                                    onClick = {
                                        setLang(langCodes[index])
                                        scope
                                            .launch { bottomSheetState.hide() }
                                            .invokeOnCompletion {
                                                if (!bottomSheetState.isVisible) {
                                                    setShowSheet(false)
                                                    setSearchStr("")
                                                }
                                            }
                                    }
                                )
                        )
                        Spacer(Modifier.height(2.dp))
                    }
                }
            }
            Spacer(Modifier.weight(1f))
        }
    }
    LaunchedEffect(searchQuery) {
        listState.scrollToItem(0)
    }
}

@Preview
@Composable
fun LanguageSheetPreview() {
    WikiReaderTheme {
        LanguageBottomSheet(
            lang = "en", searchStr = "", searchQuery = "",
            {}, {}, {})
    }
}
