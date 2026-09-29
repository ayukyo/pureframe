package com.pureframe.player.ui.screens.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pureframe.player.R
import com.pureframe.player.cast.CastDevice

/**
 * 投屏设备选择弹层
 *
 * 展示局域网内发现的 DLNA 设备；空态给「确认同一 Wi-Fi」引导；
 * 下拉刷新按钮触发重扫。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CastDevicePicker(
    devices: List<CastDevice>,
    isScanning: Boolean,
    isCasting: Boolean,
    currentDeviceName: String?,
    onDeviceClick: (CastDevice) -> Unit,
    onDisconnect: () -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1E22)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // 标题行 + 刷新
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.cast_picker_title),
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onRefresh, enabled = !isScanning) {
                    if (isScanning) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.cast_refresh),
                            tint = Color.White
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // 已连接状态：给断开入口
            if (isCasting && currentDeviceName != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF2B2B31),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Tv,
                            contentDescription = null,
                            tint = Color(0xFF7CB342),
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = stringResource(R.string.cast_connected_to, currentDeviceName),
                            color = Color.White,
                            fontSize = 14.sp,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp)
                        )
                        Text(
                            text = stringResource(R.string.cast_disconnect),
                            color = Color(0xFFEF5350),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.clickable { onDisconnect() }
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // 设备列表
            if (devices.isEmpty() && !isScanning) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.cast_no_devices),
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 15.sp
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.cast_same_wifi_hint),
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.height(280.dp)
                ) {
                    items(devices, key = { it.id }) { device ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onDeviceClick(device) }
                                .padding(horizontal = 8.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (device.isTv) Icons.Filled.Tv else Icons.Filled.Speaker,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(24.dp)
                            )
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 14.dp)
                            ) {
                                Text(
                                    text = device.name,
                                    color = Color.White,
                                    fontSize = 15.sp
                                )
                                Text(
                                    // 协议标签：DLNA / Google Cast（UI 上区分来源）
                                    text = stringResource(
                                        if (device.type == com.pureframe.player.cast.RouteType.CAST) {
                                            R.string.cast_googlecast_label
                                        } else {
                                            R.string.cast_dlna_label
                                        }
                                    ),
                                    color = Color.White.copy(alpha = 0.45f),
                                    fontSize = 11.sp
                                )
                            }
                            if (isCasting && device.name == currentDeviceName) {
                                Text(
                                    text = stringResource(R.string.cast_active_label),
                                    color = Color(0xFF7CB342),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
