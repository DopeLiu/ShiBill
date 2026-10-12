package com.shibill.app.ui.rules

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.shibill.app.ShiBillApp
import com.shibill.app.data.db.CategoryDao
import com.shibill.app.data.db.CategoryEntity
import com.shibill.app.ui.components.CategoryBadge
import com.shibill.app.ui.components.GroupCard
import com.shibill.app.ui.components.InfoBanner
import com.shibill.app.ui.components.RowDivider
import com.shibill.app.ui.theme.AppSpacing
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CategoryKeywordsViewModel(private val categoryDao: CategoryDao) : ViewModel() {

    val categories: StateFlow<List<CategoryEntity>> = categoryDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun saveKeywords(category: CategoryEntity, keywords: String) = viewModelScope.launch {
        categoryDao.update(category.copy(keywords = keywords.trim()))
    }
}

/** 分类关键词编辑（§6.4：用户可在规则页编辑 keywords） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryKeywordsScreen(navController: NavHostController) {
    val context = LocalContext.current
    val container = (context.applicationContext as ShiBillApp).container
    val vm: CategoryKeywordsViewModel = viewModel {
        CategoryKeywordsViewModel(container.db.categoryDao())
    }
    val categories by vm.categories.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<CategoryEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("分类关键词") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            item(key = "hint") {
                InfoBanner("命中关键词的账单会自动归入该分类（逗号分隔，按分类顺序优先匹配）")
            }
            item(key = "list") {
                GroupCard(modifier = Modifier.padding(top = AppSpacing.md)) {
                    categories.forEachIndexed { index, category ->
                        if (index > 0) RowDivider()
                        ListItem(
                            leadingContent = {
                                CategoryBadge(iconName = category.icon, size = 32.dp)
                            },
                            // 关键词并入标题块：ListItem 判定为"单行"，下限 56dp 且内容超出时自动增高
                            headlineContent = {
                                Column {
                                    Text(category.name, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        text = category.keywords.ifEmpty { "（未设置关键词）" },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                            },
                            trailingContent = {
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable { editing = category }
                        )
                    }
                }
            }
            item(key = "bottom-space") { Spacer(Modifier.height(AppSpacing.xxl)) }
        }
    }

    editing?.let { category ->
        var keywords by remember(category.id) { mutableStateOf(category.keywords) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("编辑「${category.name}」关键词") },
            text = {
                OutlinedTextField(
                    value = keywords,
                    onValueChange = { keywords = it },
                    label = { Text("关键词（逗号分隔）") },
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.saveKeywords(category, keywords)
                    editing = null
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { editing = null }) { Text("取消") }
            }
        )
    }
}
