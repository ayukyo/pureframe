package com.pureframe.player.ui.screens.about

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.pureframe.player.R
import com.pureframe.player.ui.screens.settings.openUrl

/**
 * 致谢页面：本应用使用的第三方开源库。
 *
 * 数据硬编码于本地（库列表变动时随代码同步更新），
 * 不做运行时依赖扫描——体积小、无解析开销、内容可控。
 */
private data class OssLibrary(
    val name: String,
    val version: String,
    val license: String,
    val url: String,
    val descriptionRes: Int
)

private val ossLibraries = listOf(
    OssLibrary("AndroidX Media3 / ExoPlayer", "1.10.1", "Apache-2.0",
        "https://github.com/androidx/media", R.string.ack_media3_desc),
    OssLibrary("nextlib (media3 extensions)", "1.10.1-0.13.0", "GPL-3.0",
        "https://github.com/anilbeesetti/nextlib", R.string.ack_nextlib_desc),
    OssLibrary("libtorrent4j", "2.1.0-39", "BSD-2-Clause",
        "https://github.com/libtorrent4j/libtorrent4j", R.string.ack_libtorrent_desc),
    OssLibrary("Jetpack Compose", "BOM 2024.12.01", "Apache-2.0",
        "https://developer.android.com/jetpack/compose", R.string.ack_compose_desc),
    OssLibrary("Hilt", "2.60.1", "Apache-2.0",
        "https://dagger.dev/hilt", R.string.ack_hilt_desc),
    OssLibrary("Room", "2.8.5", "Apache-2.0",
        "https://developer.android.com/training/data-storage/room", R.string.ack_room_desc),
    OssLibrary("Coil", "2.5.0", "Apache-2.0",
        "https://coil-kt.github.io/coil", R.string.ack_coil_desc),
    OssLibrary("UPnPCast", "v1.3.0", "Apache-2.0",
        "https://github.com/yinnho/UPnPCast", R.string.ack_upnpcast_desc),
    OssLibrary("DataStore", "1.0.0", "Apache-2.0",
        "https://developer.android.com/topic/libraries/architecture/datastore", R.string.ack_datastore_desc),
    OssLibrary("Timber", "5.0.1", "Apache-2.0",
        "https://github.com/JakeWharton/timber", R.string.ack_timber_desc),
    OssLibrary("Kotlin Coroutines", "1.7.3", "Apache-2.0",
        "https://github.com/Kotlin/kotlinx.coroutines", R.string.ack_coroutines_desc)
)

/**
 * 致谢：第三方开源库列表
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AcknowledgementsScreen(
    onBack: () -> Unit,
    onOpenLicense: (String) -> Unit
) {
    val context = LocalContext.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_oss_acknowledgements),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.ack_page_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            items(ossLibraries.size) { index ->
                val lib = ossLibraries[index]
                LibraryCard(
                    library = lib,
                    onOpenHomepage = { openUrl(context, lib.url) },
                    onOpenLicense = onOpenLicense
                )
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun LibraryCard(
    library: OssLibrary,
    onOpenHomepage: () -> Unit,
    onOpenLicense: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = library.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                // 许可证徽章，点击查看全文
                Text(
                    text = library.license,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onOpenLicense(library.license) }
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = library.version,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(library.descriptionRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.clickable(onClick = onOpenHomepage),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Link,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.about_library_homepage),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * 开源许可证全文页面：从 assets 读取许可证文本展示。
 * 当前项目依赖只有 GPL-3.0 与 Apache-2.0 两族，对应两份全文。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensesScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current

    // 各许可证全文：懒加载从 assets 读取
    val licenses = listOf(
        "GPL-3.0" to "licenses/GPL-3.0.txt",
        "Apache-2.0" to "licenses/Apache-2.0.txt"
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.about_licenses_title),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            licenses.forEach { (name, assetPath) ->
                item(key = name) {
                    val text = remember(assetPath) {
                        runCatching {
                            context.assets.open(assetPath).bufferedReader().use { it.readText() }
                        }.getOrDefault("")
                    }

                    Column {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
