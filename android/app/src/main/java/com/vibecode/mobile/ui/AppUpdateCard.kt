package com.vibecode.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vibecode.mobile.BuildConfig
import com.vibecode.mobile.data.AppUpdate
import com.vibecode.mobile.data.AppUpdater
import kotlinx.coroutines.launch

@Composable
fun AppUpdateCard(modifier: Modifier = Modifier) {
    val appContext = LocalContext.current.applicationContext
    val updater = remember { AppUpdater(appContext) }
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var installing by remember { mutableStateOf(false) }
    var update by remember { mutableStateOf<AppUpdate?>(null) }
    var status by remember { mutableStateOf<String?>(null) }

    Card(modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().then(Modifier),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    "Cập nhật VibeCode",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "Bản hiện tại ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "Cập nhật đúng cách sẽ giữ nguyên danh sách VPS đã lưu.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            status?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            checking = true
                            status = null
                            try {
                                update = updater.checkForUpdate()
                                status = update?.let { "Có bản ${it.versionName}." }
                                    ?: "Bạn đang dùng bản mới nhất."
                            } catch (t: Throwable) {
                                status = "Không kiểm tra được cập nhật: ${t.message ?: "lỗi không xác định"}"
                            } finally {
                                checking = false
                            }
                        }
                    },
                    enabled = !checking && !installing,
                ) {
                    Text("Kiểm tra cập nhật")
                }

                update?.let { available ->
                    Button(
                        onClick = {
                            if (!updater.canInstallPackages()) {
                                status = "Hãy bật 'Cho phép từ nguồn này', quay lại VibeCode rồi bấm Cập nhật lần nữa."
                                updater.openInstallPermission()
                            } else {
                                scope.launch {
                                    installing = true
                                    status = "Đang tải bản ${available.versionName}..."
                                    try {
                                        val apk = updater.download(available)
                                        status = "Đã tải xong. Mở trình cài đặt..."
                                        updater.install(apk)
                                    } catch (t: Throwable) {
                                        status = "Cập nhật thất bại: ${t.message ?: "lỗi không xác định"}"
                                    } finally {
                                        installing = false
                                    }
                                }
                            }
                        },
                        enabled = !checking && !installing,
                    ) {
                        Text("Cập nhật ${available.versionName}")
                    }
                }

                if (checking || installing) {
                    CircularProgressIndicator()
                }
            }
            Spacer(Modifier.height(2.dp))
        }
    }
}
