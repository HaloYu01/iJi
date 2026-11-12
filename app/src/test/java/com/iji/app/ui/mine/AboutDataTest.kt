package com.iji.app.ui.mine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 关于页静态数据的口径校验。
 *
 * 这些断言存在的意义是「格式统一」不是靠自觉：
 * 版本号写错前缀、日期写成 2026/11/3、更新日志顺序颠倒、许可条目漏许可证，
 * 都会在单测里直接挂掉，而不是等用户在关于页看到。
 */
class AboutDataTest {

    private val versionPattern = Regex("""^v\d+\.\d+\.\d+$""")
    private val datePattern = Regex("""^\d{4}-\d{2}-\d{2}$""")
    private val versionRangePattern = Regex("""^v\d+\.\d+\.\d+ Beta → v\d+\.\d+\.\d+ Beta$""")

    @Test
    fun changelogVersionAndDateAreNormalized() {
        changelogData.forEach { entry ->
            if (entry.version.contains("→")) {
                // 开发记录是归档条目：版本号记区间，日期记起止两段
                assertTrue(
                    "开发记录版本应为 Beta 区间，实际 ${entry.version}",
                    versionRangePattern.matches(entry.version),
                )
                val range = entry.date.split(" ~ ")
                assertEquals("开发记录日期应为起止两段：${entry.date}", 2, range.size)
                range.forEach { part ->
                    assertTrue("日期格式应为 yyyy-MM-dd，实际 $part", datePattern.matches(part))
                }
            } else {
                assertTrue("版本号格式应为 vX.Y.Z，实际 ${entry.version}", versionPattern.matches(entry.version))
                assertTrue("日期格式应为 yyyy-MM-dd，实际 ${entry.date}", datePattern.matches(entry.date))
            }
            assertTrue("${entry.version} 至少要有一条变更", entry.items.isNotEmpty())
            entry.items.forEach { line ->
                assertTrue("${entry.version} 有空白变更条目", line.isNotBlank())
                assertTrue("${entry.version} 的条目不该带列表符号", !line.startsWith("·"))
                // 去掉了「新增/修复/优化」标签后，正文里也不能再用这类前缀开头
                assertTrue(
                    "${entry.version} 的条目不该以类型标签开头：$line",
                    listOf("新增", "修复", "优化", "调整", "文档").none { line.startsWith(it) },
                )
            }
        }
    }

    @Test
    fun changelogIsSortedNewestFirst() {
        // 归档条目按区间结束日参与排序，避免区间字符串把顺序比歪
        val dates = changelogData.map { it.date.substringAfterLast(" ~ ") }
        assertEquals("更新日志必须由新到旧", dates.sortedDescending(), dates)

        val versions = changelogData.map { it.version }
        assertEquals("版本号不应重复", versions.size, versions.toSet().size)
    }

    @Test
    fun changelogLookupAcceptsBothPrefixStyles() {
        val newest = changelogData.first()
        assertEquals(newest, changelogOf(newest.version))
        assertEquals(newest, changelogOf(newest.version.removePrefix("v")))
        assertNull(changelogOf(""))
        assertNull(changelogOf("9.9.9"))
    }

    @Test
    fun everyLicenseGroupedItemDeclaresLicense() {
        assertTrue("许可清单不能为空", licenseGroups.isNotEmpty())
        licenseGroups.forEach { group ->
            assertTrue("分组「${group.title}」没有条目", group.items.isNotEmpty())
            group.items.forEach { item ->
                assertTrue("${item.name} 缺少许可证名称", item.license.isNotBlank())
                assertTrue("${item.name} 缺少作者", item.author.isNotBlank())
                assertTrue("${item.name} 缺少用途说明", item.contribution.isNotBlank())
            }
        }
    }

    @Test
    fun thanksItemsOnlyCarryNameAuthorAndRepo() {
        val thanksNames = thanksData.map { it.name }
        assertEquals("致谢条目不应重复", thanksNames.size, thanksNames.toSet().size)

        thanksData.forEach { item ->
            assertTrue("${item.name} 缺少作者", item.author.isNotBlank())
            assertTrue("${item.name} 缺少仓库地址", item.url.startsWith("https://"))
            assertTrue("${item.name} 不应再带贡献说明", item.contribution.isBlank())
            assertTrue("${item.name} 不应再带许可证标注", item.license.isBlank())
        }
    }

    @Test
    fun appLicenseSectionsCoverRequiredTopics() {
        val titles = appLicenseSections.map { it.title }
        listOf("著作权归属", "你可以做什么", "你不可以做什么", "第三方组件", "无担保与责任限制", "条款更新")
            .forEach { required ->
                assertTrue("授权条款缺少章节：$required", titles.contains(required))
            }
        appLicenseSections.forEach { section ->
            assertTrue("章节「${section.title}」没有正文", section.paragraphs.isNotEmpty())
            section.paragraphs.forEach { assertTrue("章节「${section.title}」有空白段落", it.isNotBlank()) }
        }
        // 闭源这件事必须写明，不能只挂第三方许可清单
        assertTrue(
            "授权条款必须声明本应用不开源",
            appLicenseSections.any { s -> s.paragraphs.any { it.contains("不开放源代码") } },
        )
        assertNotNull(appLicenseSections.firstOrNull())
    }
}
