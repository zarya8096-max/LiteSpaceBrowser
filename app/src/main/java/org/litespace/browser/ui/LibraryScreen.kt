package org.litespace.browser.ui

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.litespace.browser.domain.LiteApp

@Composable fun LibraryScreen(apps: List<LiteApp>, modifier: Modifier = Modifier, onOpen: (LiteApp) -> Unit,
    onEdit: (LiteApp) -> Unit, onPin: (LiteApp) -> Unit, onPrivate: (LiteApp) -> Unit, onDelete: (LiteApp) -> Unit, onCreate: () -> Unit, onFilterActive: (Boolean) -> Unit = {}) {
    val keyboard = LocalSoftwareKeyboardController.current
    var query by rememberSaveable { mutableStateOf("") }
    var favorites by rememberSaveable { mutableStateOf(false) }
    var group by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(query, favorites, group) { onFilterActive(query.isNotBlank() || favorites || group != null) }
    DisposableEffect(Unit) { onDispose { onFilterActive(false) } }
    val groups = remember(apps) { apps.map { it.group }.filter { it.isNotBlank() }.distinct().sorted() }
    LaunchedEffect(groups) { if(group != null && group !in groups) group = null }
    val visible = remember(apps, query, favorites, group) { apps.filter {
        (!favorites || it.favorite) && (group == null || it.group == group) && "${it.name} ${it.url} ${it.group}".contains(query.trim(), true)
    }.sortedWith(compareByDescending<LiteApp> { it.favorite }.thenBy { it.name.lowercase() }) }
    LazyVerticalGrid(columns = GridCells.Adaptive(152.dp), modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 100.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(Brush.linearGradient(listOf(Color(0xFF173D2D), Color(0xFF2D6049))))) {
                Canvas(Modifier.matchParentSize()) {
                    val center = Offset(size.width * .93f, size.height * .27f)
                    for(i in 1..4) drawCircle(Color(0xFFD7EEA9).copy(alpha = .09f), size.minDimension * (.2f + i * .15f), center, style = Stroke(1.2.dp.toPx()))
                    drawCircle(Color(0xFFCFEC9C).copy(alpha = .15f), 28.dp.toPx(), Offset(size.width * .85f, size.height * .8f))
                }
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if(apps.isEmpty()) Surface(color = Color.White.copy(alpha = .1f), shape = CircleShape) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            Box(Modifier.size(6.dp).background(Color(0xFFC9E99C), CircleShape))
                            Text(tr("A LITTLE MORE SPACE", "کمی فضای بیشتر"), color = Color(0xFFDFEDD9), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Text(if(apps.isEmpty()) tr("Your web.\nBeautifully simple.", "وب شما؛\nساده و دلنشین.") else tr("Your web. Your space.", "وب شما. فضای شما."), style = if(apps.isEmpty()) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.headlineSmall, color = Color.White)
                    if(apps.isEmpty()) Text(tr("The places you love. All in one calm space.", "همهٔ مقصدهای محبوب شما، در فضایی آرام."), style = MaterialTheme.typography.bodyMedium, color = Color(0xFFC5D8C6))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(localNumber(apps.size), color = Color(0xFFD8EFAF), style = MaterialTheme.typography.titleLarge)
                        Text(tr("Lite Apps", "برنامهٔ سبک"), color = Color(0xFFDFEDD9), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        if(apps.isNotEmpty()) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text(tr("Find an app or a group", "جستجوی برنامه یا گروه")) },
                leadingIcon = { Icon(Icons.Default.Search, null) }, trailingIcon = { if(query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, tr("Clear search", "پاک‌کردن جستجو")) } },
                singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, autoCorrectEnabled = false), keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }), shape = RoundedCornerShape(20.dp), colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest, focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest))
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(!favorites && group == null, { keyboard?.hide(); favorites = false; group = null }, label = { Text(tr("All apps", "همهٔ برنامه‌ها")) }) }
                item { FilterChip(favorites, { keyboard?.hide(); favorites = !favorites; group = null }, label = { Text(tr("Favorites", "علاقه‌مندی‌ها")) }, leadingIcon = { Icon(Icons.Default.Star, null, Modifier.size(16.dp)) }) }
                items(groups) { name -> FilterChip(group == name, { keyboard?.hide(); group = if(group == name) null else name; favorites = false }, label = { Text(name) }) }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(Modifier.fillMaxWidth().padding(bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Your collection", "مجموعهٔ شما"), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) { Text(localNumber(visible.size), Modifier.padding(horizontal = 11.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium) }
            }
        }
        }
        if(visible.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
            Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    if(apps.isEmpty()) Surface(Modifier.size(64.dp), shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.primaryContainer) { Box(contentAlignment = Alignment.Center) { Icon(if(apps.isEmpty()) Icons.Default.Add else Icons.Default.Search, null, Modifier.size(30.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) } }
                    Text(if(apps.isEmpty()) tr("A lighter way to browse", "راهی سبک‌تر برای مرور") else tr("Nothing here yet", "هنوز چیزی اینجا نیست"), style = MaterialTheme.typography.titleLarge)
                    if(apps.isNotEmpty()) TextButton(onClick = { keyboard?.hide(); query = ""; favorites = false; group = null }) { Text(tr("Show all apps", "نمایش همهٔ برنامه‌ها")) }
                    Text(if(apps.isEmpty()) tr("Give your favorite website a space of its own. Start with its name and address.", "به سایت محبوبتان یک جای اختصاصی بدهید. با نام و نشانی آن شروع کنید.") else tr("Try another search or switch back to all apps.", "عبارت دیگری جستجو کنید یا همهٔ برنامه‌ها را ببینید."), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if(apps.isEmpty()) FilledTonalButton(onClick = onCreate) { Text(tr("Add your first app", "ساخت اولین برنامه")) }

                }
            }
        }
        items(visible, key = { it.id }) { app ->
            var menu by remember { mutableStateOf(false) }
            Card(onClick = { onOpen(app) }, modifier = Modifier.fillMaxWidth().animateItem(), shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f))) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(app, 52.dp)
                        Spacer(Modifier.weight(1f))
                        Box {
                            IconButton(onClick = { menu = true }, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.MoreVert, tr("App actions", "عملیات برنامه"), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                            DropdownMenu(menu, { menu = false }) {
                                DropdownMenuItem(text = { Text(tr("Edit", "ویرایش")) }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { menu = false; onEdit(app) })
                                DropdownMenuItem(text = { Text(tr("Pin to home screen", "افزودن به صفحه اصلی")) }, leadingIcon = { Icon(Icons.Default.Home, null) }, onClick = { menu = false; onPin(app) })
                                DropdownMenuItem(text = { Text(tr("Private session", "نشست خصوصی")) }, leadingIcon = { Icon(Icons.Default.Lock, null) }, onClick = { menu = false; onPrivate(app) })
                                HorizontalDivider()
                                DropdownMenuItem(text = { Text(tr("Delete", "حذف"), color = MaterialTheme.colorScheme.error) }, leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }, onClick = { menu = false; onDelete(app) })
                            }
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(app.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(android.net.Uri.parse(app.url).host ?: app.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if(app.favorite) Icon(Icons.Default.Star, tr("Favorite", "برگزیده"), Modifier.size(15.dp), tint = MaterialTheme.colorScheme.tertiary)
                        if(app.lock) Icon(Icons.Default.Lock, tr("Protected", "محافظت‌شده"), Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(app.group.ifBlank { tr("Lite App", "برنامهٔ سبک") }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
