package com.iji.app.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WebDAV 目录解析单测。
 *
 * 列目录是云备份恢复的入口：解析错了就会「网盘上明明有备份，但列表是空的」，
 * 而这种问题在真机上很难复现（不同服务器的命名空间前缀、href 写法都不一样）。
 * 这里用几种典型响应把解析行为钉死。
 */
class WebDavClientTest {

    private val client = WebDavClient(
        baseUrl = "https://dav.example.com/dav/",
        user = "u",
        pass = "p",
    )

    @Test
    fun parseList_keepsOnlyBackupFiles() {
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <D:multistatus xmlns:D="DAV:">
              <D:response>
                <D:href>/dav/</D:href>
                <D:propstat><D:prop><D:resourcetype><D:collection/></D:resourcetype></D:prop></D:propstat>
              </D:response>
              <D:response>
                <D:href>/dav/iji_backup_1700000000000.ijbak</D:href>
                <D:propstat><D:prop>
                  <D:getcontentlength>2048</D:getcontentlength>
                  <D:getlastmodified>Mon, 16 Oct 2026 10:00:00 GMT</D:getlastmodified>
                </D:prop></D:propstat>
              </D:response>
              <D:response>
                <D:href>/dav/readme.txt</D:href>
              </D:response>
            </D:multistatus>
        """.trimIndent()

        val files = client.parseList(xml, setOf(".ijbak"))

        assertEquals(1, files.size)
        assertEquals("iji_backup_1700000000000.ijbak", files[0].name)
        assertEquals(2048L, files[0].sizeBytes)
        assertEquals("Mon, 16 Oct 2026 10:00:00 GMT", files[0].lastModified)
    }

    @Test
    fun parseList_resolvesRelativeHrefToAbsoluteUrl() {
        val xml = """
            <D:multistatus xmlns:D="DAV:">
              <D:response><D:href>/dav/iji_backup_1.ijbak</D:href></D:response>
            </D:multistatus>
        """.trimIndent()

        val files = client.parseList(xml, setOf(".ijbak"))

        assertEquals("https://dav.example.com/dav/iji_backup_1.ijbak", files[0].url)
    }

    @Test
    fun parseList_handlesNamespaceLessAndUppercasePrefixes() {
        // 有的服务器不写前缀、有的用大写 D:，两种都得认
        val xml = """
            <multistatus>
              <response>
                <href>/dav/iji_backup_2.ijbak</href>
                <propstat><prop><getcontentlength>10</getcontentlength></prop></propstat>
              </response>
            </multistatus>
        """.trimIndent()

        val files = client.parseList(xml, setOf(".ijbak"))

        assertEquals(1, files.size)
        assertEquals("iji_backup_2.ijbak", files[0].name)
        assertEquals(10L, files[0].sizeBytes)
        // 没给 lastmod，保持空串（展示层据此省略时间），不瞎填
        assertEquals("", files[0].lastModified)
    }

    @Test
    fun parseList_decodesPercentEncodedName() {
        val xml = """
            <D:multistatus xmlns:D="DAV:">
              <D:response><D:href>/dav/iji_backup_%E6%B5%8B%E8%AF%95.ijbak</D:href></D:response>
            </D:multistatus>
        """.trimIndent()

        val files = client.parseList(xml, setOf(".ijbak"))

        assertEquals("iji_backup_测试.ijbak", files[0].name)
    }

    @Test
    fun parseList_emptyOrGarbageYieldsEmptyInsteadOfCrash() {
        assertTrue(client.parseList("", setOf(".ijbak")).isEmpty())
        assertTrue(client.parseList("<html>not a propfind response</html>", setOf(".ijbak")).isEmpty())
    }
}
