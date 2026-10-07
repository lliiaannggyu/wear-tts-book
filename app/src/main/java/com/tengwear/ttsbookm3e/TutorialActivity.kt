package com.tengwear.ttsbookm3e

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.tengwear.ttsbookm3e.ui.TtsbookActionButton
import com.tengwear.ttsbookm3e.ui.theme.TtsbookTheme

class TutorialActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TtsbookTheme { TutorialScreen() } }
    }

    @Composable
    private fun TutorialScreen() {
        val listState = rememberTransformingLazyColumnState()
        val transformationSpec = rememberTransformationSpec()

        AppScaffold {
            ScreenScaffold(scrollState = listState) { contentPadding ->
                TransformingLazyColumn(
                    state = listState,
                    contentPadding = contentPadding,
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    item {
                        ListHeader(
                            modifier = Modifier.transformedHeight(this, transformationSpec),
                        ) {
                            Text(
                                text = "使用教程",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                    }

                    tutorialSection(
                        "阅读界面",
                        listOf(
                            "左半区域点击 → 上一页",
                            "右半区域点击 → 下一页",
                            "中间双击 → 控制面板",
                            "长按任意处 → 播放/暂停朗读",
                            "旋转表冠 → 上下翻页",
                        ),
                    )

                    tutorialSection(
                        "控制面板",
                        listOf(
                            "上一章 / 下一章 切换章节",
                            "添加书签 为当前位置打标签",
                            "书签列表 查看、跳转或删除",
                            "章节列表 滑块定位并跳转",
                            "全局搜索 全库检索正文",
                            "倒计时翻页 定时自动翻页",
                            "语音引擎 切换 TTS 引擎",
                            "阅读设置 字号 / 行数 / 字数",
                            "主题配色 切换种子颜色",
                            "书架 / 教程 / 关于",
                        ),
                    )

                    tutorialSection(
                        "语音引擎",
                        listOf(
                            "系统默认 使用设备自带 TTS",
                            "可切换已安装的第三方引擎",
                            "部分设备需安装中文语音包",
                            "选择后重启应用生效",
                        ),
                    )

                    tutorialSection(
                        "书架管理",
                        listOf(
                            "点击「导入书籍」浏览文件",
                            "导入时显示解析进度和章节数",
                            "点击书籍 → 开始阅读",
                            "长按书籍 → 删除书籍",
                        ),
                    )

                    tutorialSection(
                        "书签功能",
                        listOf(
                            "在阅读界面打开控制面板",
                            "点击「添加书签」选择标签",
                            "书签列表支持按位置定位",
                            "点击书签跳转 / 删除",
                        ),
                    )

                    tutorialSection(
                        "倒计时翻页",
                        listOf(
                            "设置秒数（默认 10 秒）",
                            "点击「开始」启动倒计时",
                            "归零时自动翻页并重置",
                        ),
                    )

                    item {
                        Spacer(Modifier.height(12.dp))
                        TtsbookActionButton(
                            label = "返回",
                            onClick = { finish() },
                            modifier = Modifier.fillMaxWidth(0.8f),
                        )
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }

    /**
     * 教程分组：标题用 primary 色，条目用 onSurfaceVariant，
     * 组与组之间留出间距，不再使用手绘分割线。
     */
    private fun androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope.tutorialSection(
        title: String,
        items: List<String>,
    ) {
        item {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 2.dp),
            )
        }
        items.forEach { line ->
            item {
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp),
                )
            }
        }
        item { Spacer(Modifier.height(6.dp)) }
    }
}
