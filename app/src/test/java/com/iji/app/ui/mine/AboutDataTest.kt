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

    @Test
    fun changelogVersionAndDateAreNormalized() {
        changelogData.forEach { entry ->
            assertTrue("版本号格式应为 vX.Y.Z，实际 ${entry.version}", versionPattern.matches(entry.version))
            assertTrue("日期格式应为 yyyy-MM-dd，实际 ${entry.date}", datePattern.matches(entry.date))
            assertTrue("${entry.version} 至少要有一条变更", entry.items.isNotEmpty())
            assertTrue("${entry.version} 缺少一句话概览", entry.summary.isNotBlank())
            entry.items.forEach { item ->
                assertTrue("${entry.version} 有空白变更条目", item.text.isNotBlank())
                assertTrue("${entry.version} 的条目不该带列表符号", !item.text.startsWith("·"))
            }
        }
    }

    @Test
    fun changelogIsSortedNewestFirst() {
        val dates = changelogData.map { it.date }
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
    fun thanksItemsAreDistinctFromLicenseList() {
        val thanksNames = thanksData.map { it.name }
        assertEquals("致谢条目不应重复", thanksNames.size, thanksNames.toSet().size)

        // 依赖库的许可证在许可页单独声明；致谢里出现依赖时也要带上许可证
        thanksData.filter { it.contribution.contains("依赖库") }.forEach {
            assertTrue("${it.name} 是依赖库，致谢里应标许可证", it.license.isNotBlank())
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
