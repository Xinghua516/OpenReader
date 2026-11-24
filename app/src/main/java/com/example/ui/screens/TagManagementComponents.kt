package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.ViewStream
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.entity.SmartRuleConditionType
import com.example.data.local.entity.SmartRuleEntity
import com.example.data.local.entity.TagEntity
import com.example.data.local.entity.TagWithCount
import com.example.ui.theme.TagPresetColors

@Composable
internal fun SmartRuleCard(
    rule: SmartRuleEntity,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit
) {
    val icon = when (rule.conditionType) {
        SmartRuleConditionType.FORMAT_EQUALS -> {
            val v = rule.conditionValue
            if (v.contains("EPUB", ignoreCase = true) ||
                v.contains("TXT", ignoreCase = true) ||
                v.contains("MD", ignoreCase = true) ||
                v.contains("PDF", ignoreCase = true)
            ) {
                Icons.AutoMirrored.Outlined.MenuBook
            } else {
                Icons.Outlined.FolderZip
            }
        }
        else -> Icons.Outlined.ViewStream
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = rule.name,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = rule.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = rule.isEnabled,
                onCheckedChange = onToggle
            )
        }
    }
}

@Composable
internal fun TagPoolPill(
    tagWithCount: TagWithCount,
    isBatchMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val tag = tagWithCount.tag
    val dotColor = Color(tag.colorHex)
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (isSelected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLowest
        },
        modifier = if (isSelected) {
            Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
        } else {
            Modifier
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (isBatchMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = null,
                    modifier = Modifier.size(16.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
            }
            Text(
                text = tag.name,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Text(
                    text = "${tagWithCount.bookCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun TagQuickActionSheet(
    tagWithCount: TagWithCount,
    onDismiss: () -> Unit,
    onViewBooksWithTag: () -> Unit,
    onSave: (TagEntity) -> Unit,
    onDelete: () -> Unit
) {
    val tag = tagWithCount.tag
    var name by remember(tag.id) { mutableStateOf(tag.name) }
    var category by remember(tag.id) { mutableStateOf(tag.categoryGroup) }
    var colorHex by remember(tag.id) { mutableLongStateOf(tag.colorHex) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "编辑标签 · ${tagWithCount.bookCount} 本关联",
                    style = MaterialTheme.typography.titleLarge
                )
                FilledTonalButton(onClick = onViewBooksWithTag) {
                    Text("在书架查看")
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("标签名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = category,
                onValueChange = { category = it },
                label = { Text("所属分组") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Text("标签色标", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TagPresetColors.forEach { preset ->
                    val selected = colorHex == preset
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(preset))
                            .clickable { colorHex = preset }
                            .then(
                                if (selected) {
                                    Modifier.border(2.5.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                } else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (selected) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = onDelete) {
                    Text("删除标签", color = MaterialTheme.colorScheme.error)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDismiss) { Text("取消") }
                    Button(
                        onClick = {
                            if (name.isNotBlank()) {
                                onSave(tag.copy(name = name, categoryGroup = category, colorHex = colorHex))
                            }
                        }
                    ) {
                        Text("保存")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TagEditorDialog(
    initialTag: TagEntity?,
    onDismiss: () -> Unit,
    onSave: (String, String, Long) -> Unit
) {
    var name by remember { mutableStateOf(initialTag?.name.orEmpty()) }
    var category by remember { mutableStateOf(initialTag?.categoryGroup ?: "题材分类") }
    var selectedColor by remember { mutableLongStateOf(initialTag?.colorHex ?: TagPresetColors.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建标签") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("标签名称") },
                    placeholder = { Text("如：赛博朋克 / 短篇集") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("分类分组") },
                    singleLine = true
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("题材分类", "阅读状态", "格式与载体").forEach { preset ->
                        FilterChip(
                            selected = category == preset,
                            onClick = { category = preset },
                            label = { Text(preset) }
                        )
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TagPresetColors.forEach { color ->
                        val active = selectedColor == color
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(color))
                                .clickable { selectedColor = color }
                                .then(
                                    if (active) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                    else Modifier
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (active) {
                                Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(name, category, selectedColor)
                    }
                }
            ) {
                Text("创建")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SmartRuleEditorDialog(
    initialRule: SmartRuleEntity?,
    onDismiss: () -> Unit,
    onSave: (String, String, SmartRuleConditionType, String, String, String) -> Unit,
    onDelete: (() -> Unit)?
) {
    var name by remember { mutableStateOf(initialRule?.name.orEmpty()) }
    var desc by remember { mutableStateOf(initialRule?.description.orEmpty()) }
    var condType by remember {
        mutableStateOf(initialRule?.conditionType ?: SmartRuleConditionType.TITLE_CONTAINS)
    }
    var condValue by remember { mutableStateOf(initialRule?.conditionValue.orEmpty()) }
    var targetTag by remember { mutableStateOf(initialRule?.targetTagName.orEmpty()) }
    var targetCategory by remember { mutableStateOf(initialRule?.targetCategoryGroup.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialRule == null) "新建智能归类规则" else "编辑智能归类规则") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("规则名称") },
                    placeholder = { Text("如：包含 \"全彩\" → 彩漫") },
                    singleLine = true
                )
                Text("匹配条件类型", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SmartRuleConditionType.entries.forEach { type ->
                        FilterChip(
                            selected = condType == type,
                            onClick = { condType = type },
                            label = { Text(type.label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                OutlinedTextField(
                    value = condValue,
                    onValueChange = { condValue = it },
                    label = { Text("匹配值（逗号分隔多个关键词）") },
                    placeholder = { Text("如：webtoon,条漫 或 EPUB") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = targetTag,
                    onValueChange = { targetTag = it },
                    label = { Text("自动添加标签") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = targetCategory,
                    onValueChange = { targetCategory = it },
                    label = { Text("自动归类分组（可选）") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && condValue.isNotBlank()) {
                        onSave(name, desc, condType, condValue, targetTag, targetCategory)
                    }
                }
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text("删除", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        }
    )
}

@Composable
internal fun MergeTagsDialog(
    candidates: List<TagWithCount>,
    onDismiss: () -> Unit,
    onSelectTarget: (Long) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("合并标签") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "选择一个保留的目标标签，其余选中标签的书籍关联将全部合并至目标标签：",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                candidates.forEach { item ->
                    Surface(
                        onClick = { onSelectTarget(item.tag.id) },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(item.tag.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "${item.bookCount} 本",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MoveCategoryDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var targetCategory by remember { mutableStateOf("题材分类") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("批量移动分组") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = targetCategory,
                    onValueChange = { targetCategory = it },
                    label = { Text("目标分组名称") },
                    singleLine = true
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("题材分类", "阅读状态", "格式与载体", "自定义收藏").forEach { preset ->
                        FilterChip(
                            selected = targetCategory == preset,
                            onClick = { targetCategory = preset },
                            label = { Text(preset) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(targetCategory) }) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
