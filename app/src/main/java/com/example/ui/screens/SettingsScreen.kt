package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Merge
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.entity.SmartRuleConditionType
import com.example.data.local.entity.SmartRuleEntity
import com.example.data.local.entity.TagEntity
import com.example.data.local.entity.TagWithCount
import com.example.data.preferences.ComicReadingDirection
import com.example.ui.components.TonalSearchBar
import com.example.ui.components.TonalTopBar
import com.example.ui.viewmodel.LibraryUiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    uiState: LibraryUiState,
    onTagSearchChange: (String) -> Unit,
    onToggleBatchMode: () -> Unit,
    onToggleBatchTagSelection: (Long) -> Unit,
    onCreateTag: (String, String, Long) -> Unit,
    onUpdateTag: (TagEntity) -> Unit,
    onDeleteTags: (List<Long>) -> Unit,
    onMergeSelectedTagsInto: (Long) -> Unit,
    onMoveSelectedTagsToCategory: (String) -> Unit,
    onToggleSmartRule: (Long, Boolean) -> Unit,
    onSaveSmartRule: (Long, String, String, SmartRuleConditionType, String, String, String) -> Unit,
    onDeleteSmartRule: (Long) -> Unit,
    onApplySmartRulesNow: () -> Unit,
    onFilterBookshelfByTag: (TagEntity) -> Unit,
    onSetDarkModeOverride: (Boolean?) -> Unit,
    onSetUseDynamicColor: (Boolean) -> Unit,
    onSetDefaultComicDirection: (ComicReadingDirection) -> Unit,
    onSetDefaultKeepScreenOn: (Boolean) -> Unit,
    onOpenProfileOverview: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showCreateTagDialog by remember { mutableStateOf(false) }
    var editingTag by remember { mutableStateOf<TagWithCount?>(null) }
    var editingRule by remember { mutableStateOf<SmartRuleEntity?>(null) }
    var showCreateRuleDialog by remember { mutableStateOf(false) }
    var showMergeDialog by remember { mutableStateOf(false) }
    var showMoveCategoryDialog by remember { mutableStateOf(false) }

    val enabledRulesCount = uiState.smartRules.count { it.isEnabled }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TonalTopBar(
                title = "设置",
                onProfileClick = onOpenProfileOverview
            )

            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 104.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // Section 1: Tag & Category Management Header
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "标签与归类管理",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = "共 ${uiState.tagsWithCounts.size} 个活跃标签 · $enabledRulesCount 条智能规则生效中",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        FilledTonalButton(
                            onClick = { showCreateTagDialog = true },
                            shape = CircleShape,
                            modifier = Modifier.testTag("create_tag_button")
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("新建标签")
                        }
                    }
                }

                // Tag Search
                item {
                    TonalSearchBar(
                        query = uiState.tagSearchQuery,
                        onQueryChange = onTagSearchChange,
                        placeholder = "搜索标签、题材或格式…",
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "tag_search_input"
                    )
                }

                // Smart Classification Rules
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "智能归类规则",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = onApplySmartRulesNow) {
                                Text("立即整理", style = MaterialTheme.typography.labelMedium)
                            }
                            Surface(
                                onClick = { showCreateRuleDialog = true },
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = "+ 规则",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }

                items(uiState.smartRules, key = { it.id }) { rule ->
                    SmartRuleCard(
                        rule = rule,
                        onToggle = { enabled -> onToggleSmartRule(rule.id, enabled) },
                        onClick = { editingRule = rule }
                    )
                }

                // Tag Pool Header
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Tune,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "标签池管理",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }
                        Text(
                            text = if (uiState.isTagBatchMode) "点击勾选标签" else "点击标签可快速编辑",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Grouped Tags
                uiState.filteredTagGroups.forEach { (groupName, groupTags) ->
                    item(key = "group_$groupName") {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = groupName,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium)
                                    )
                                    Text(
                                        text = "${groupTags.size} 个标签",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    groupTags.forEach { tagWithCount ->
                                        val isSelectedInBatch = tagWithCount.tag.id in uiState.selectedBatchTagIds
                                        TagPoolPill(
                                            tagWithCount = tagWithCount,
                                            isBatchMode = uiState.isTagBatchMode,
                                            isSelected = isSelectedInBatch,
                                            onClick = {
                                                if (uiState.isTagBatchMode) {
                                                    onToggleBatchTagSelection(tagWithCount.tag.id)
                                                } else {
                                                    editingTag = tagWithCount
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Batch Tag Management Card
                item {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "批量归类管理",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Text(
                                    text = "选择多个标签进行合并、分组或清理",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Button(
                                onClick = onToggleBatchMode,
                                shape = CircleShape,
                                modifier = Modifier.testTag("toggle_batch_mode_button")
                            ) {
                                Text(if (uiState.isTagBatchMode) "完成" else "开启批量")
                            }
                        }
                    }
                }

                // Section 2: Appearance & Default Reading Settings
                item {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(top = 6.dp)
                    ) {
                        Text(
                            text = "外观与阅读偏好",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("主题模式", style = MaterialTheme.typography.titleSmall)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FilterChip(
                                        selected = uiState.preferences.darkModeOverride == null,
                                        onClick = { onSetDarkModeOverride(null) },
                                        label = { Text("跟随系统") },
                                        leadingIcon = {
                                            Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                    FilterChip(
                                        selected = uiState.preferences.darkModeOverride == false,
                                        onClick = { onSetDarkModeOverride(false) },
                                        label = { Text("浅色") },
                                        leadingIcon = {
                                            Icon(Icons.Outlined.LightMode, contentDescription = null, modifier = Modifier.size(16.dp))
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                    FilterChip(
                                        selected = uiState.preferences.darkModeOverride == true,
                                        onClick = { onSetDarkModeOverride(true) },
                                        label = { Text("深色") },
                                        leadingIcon = {
                                            Icon(Icons.Outlined.DarkMode, contentDescription = null, modifier = Modifier.size(16.dp))
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Palette,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Android 13+ 动态壁纸取色", style = MaterialTheme.typography.bodyLarge)
                                        Text(
                                            "开启后跟随系统壁纸自动生成 Material You 配色",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = uiState.preferences.useDynamicColor,
                                        onCheckedChange = onSetUseDynamicColor
                                    )
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.SwapHoriz,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text("默认漫画阅读方向", style = MaterialTheme.typography.titleSmall)
                                }
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ComicReadingDirection.entries.forEach { dir ->
                                        FilterChip(
                                            selected = uiState.preferences.comicReadingDirection == dir,
                                            onClick = { onSetDefaultComicDirection(dir) },
                                            label = { Text(dir.label) }
                                        )
                                    }
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.LightMode,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("阅读时默认屏幕常亮", style = MaterialTheme.typography.bodyLarge)
                                        Text(
                                            "防止长篇漫画或小说阅读期间息屏",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = uiState.preferences.keepScreenOn,
                                        onCheckedChange = onSetDefaultKeepScreenOn
                                    )
                                }
                            }
                        }
                    }
                }

                // Section 3: Storage & Library Summary
                item {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Storage,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "内部书库已用 ${uiState.storageStatus.internalLibraryFormatted}",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "设备可用空间 ${uiState.storageStatus.availableGbFormatted} · 已持久化 SAF 授权 ${uiState.storageStatus.persistedSafGrantsCount} 项",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // Batch Action Bar floating above bottom when batch mode is active
        if (uiState.isTagBatchMode) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                tonalElevation = 4.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "已选 ${uiState.selectedBatchTagIds.size} 项",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(
                            onClick = { showMergeDialog = true },
                            enabled = uiState.selectedBatchTagIds.size >= 2
                        ) {
                            Icon(Icons.Outlined.Merge, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("合并")
                        }
                        TextButton(
                            onClick = { showMoveCategoryDialog = true },
                            enabled = uiState.selectedBatchTagIds.isNotEmpty()
                        ) {
                            Icon(Icons.Outlined.DriveFileMove, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("分组")
                        }
                        TextButton(
                            onClick = { onDeleteTags(uiState.selectedBatchTagIds.toList()) },
                            enabled = uiState.selectedBatchTagIds.isNotEmpty()
                        ) {
                            Icon(
                                Icons.Outlined.DeleteOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("删除", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }

    if (showCreateTagDialog) {
        TagEditorDialog(
            initialTag = null,
            onDismiss = { showCreateTagDialog = false },
            onSave = { name, group, color ->
                onCreateTag(name, group, color)
                showCreateTagDialog = false
            }
        )
    }

    editingTag?.let { current ->
        TagQuickActionSheet(
            tagWithCount = current,
            onDismiss = { editingTag = null },
            onViewBooksWithTag = {
                editingTag = null
                onFilterBookshelfByTag(current.tag)
            },
            onSave = { updated ->
                onUpdateTag(updated)
                editingTag = null
            },
            onDelete = {
                onDeleteTags(listOf(current.tag.id))
                editingTag = null
            }
        )
    }

    if (showCreateRuleDialog || editingRule != null) {
        SmartRuleEditorDialog(
            initialRule = editingRule,
            onDismiss = {
                showCreateRuleDialog = false
                editingRule = null
            },
            onSave = { name, desc, type, value, tagName, catGroup ->
                onSaveSmartRule(
                    editingRule?.id ?: 0L,
                    name,
                    desc,
                    type,
                    value,
                    tagName,
                    catGroup
                )
                showCreateRuleDialog = false
                editingRule = null
            },
            onDelete = editingRule?.let { rule ->
                {
                    onDeleteSmartRule(rule.id)
                    editingRule = null
                }
            }
        )
    }

    if (showMergeDialog) {
        MergeTagsDialog(
            candidates = uiState.tagsWithCounts.filter { it.tag.id in uiState.selectedBatchTagIds },
            onDismiss = { showMergeDialog = false },
            onSelectTarget = { targetId ->
                onMergeSelectedTagsInto(targetId)
                showMergeDialog = false
            }
        )
    }

    if (showMoveCategoryDialog) {
        MoveCategoryDialog(
            onDismiss = { showMoveCategoryDialog = false },
            onConfirm = { targetCategory ->
                onMoveSelectedTagsToCategory(targetCategory)
                showMoveCategoryDialog = false
            }
        )
    }
}
