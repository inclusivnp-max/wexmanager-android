package client.wexmanager.feature.editor.lang

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Индекс Go-проекта: символы пакетов, зависимости из go.mod и защита от тяжёлых сканов. */
class GoWorkspaceIndexTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun moduleInfoFindsNearestGoModAndParsesDependencies() {
        val root = temp.newFolder()
        File(root, "go.mod").writeText(
            """
            module example.com/app

            go 1.22

            require (
                github.com/pkg/errors v0.9.1
                golang.org/x/sync v0.7.0
            )

            require github.com/stretchr/testify v1.9.0
            """.trimIndent()
        )
        val nested = File(root, "internal/pkg").apply { mkdirs() }
        val info = GoWorkspaceIndex.moduleInfo(nested)
        assertNotNull(info)
        assertEquals(root.canonicalFile, info!!.first.canonicalFile)
        assertTrue(info.second.contains("github.com/pkg/errors"))
        assertTrue(info.second.contains("github.com/stretchr/testify"))
        assertFalse(info.second.contains("example.com/app"))
    }

    @Test fun moduleInfoReturnsNullOutsideModule() {
        // Файл без родителей в самой глубокой ветке tmp-дерева избегает реальных go.mod в /tmp.
        val alone = File(temp.root, "no-module/deeper/deepest").apply { mkdirs() }
        val info = GoWorkspaceIndex.moduleInfo(alone)
        // Скан идёт до корня: если в /tmp найдётся настоящий go.mod, считаем это сигналом окружения.
        if (File("/tmp/go.mod").exists() || File("/tmp").listFiles().orEmpty().any { it.isFile && it.name == "go.mod" }) {
            return
        }
        assertNull(info)
        assertNull(GoWorkspaceIndex.moduleInfo(null))
    }

    @Test fun scanCollectsExportedSymbolsPerPackage() {
        val root = temp.newFolder()
        File(root, "go.mod").writeText("module example.com/app\n\ngo 1.22\n")
        File(root, "main.go").writeText("package main\nfunc main() {}\n")
        val util = File(root, "util").apply { mkdirs() }
        File(util, "util.go").writeText(
            """
            package util

            import "strings"

            const MaxSize = 10
            var Default = "x"

            type Client struct { Name string }

            func New() *Client { return &Client{} }
            func (c *Client) Do(input string) (string, error) { return strings.ToUpper(input), nil }
            func hidden() {}
            """.trimIndent()
        )
        val snapshot = GoWorkspaceIndex.scanForTest(root)
        val utilSymbols = snapshot.packages["util"].orEmpty().map { it.text }
        assertTrue("New", "New" in utilSymbols)
        assertTrue("Client", "Client" in utilSymbols)
        assertTrue("MaxSize", "MaxSize" in utilSymbols)
        assertFalse("hidden must stay private", "hidden" in utilSymbols)
        assertFalse("main must stay private", "main" in utilSymbols)
        assertTrue(snapshot.importPaths.any { it.endsWith("example.com/app/util") })
    }

    @Test fun scanSkipsVendorGitAndHiddenDirectories() {
        val root = temp.newFolder()
        File(root, "go.mod").writeText("module example.com/app\n")
        listOf(".git", "vendor", "node_modules", "testdata").forEach { dir ->
            val d = File(root, dir).apply { mkdirs() }
            File(d, "skip.go").writeText("package skipped\nfunc Exported() {}\n")
        }
        File(root, "keep.go").writeText("package app\nfunc Keep() {}\n")
        val snapshot = GoWorkspaceIndex.scanForTest(root)
        assertTrue(snapshot.packages.containsKey("app"))
        assertFalse(snapshot.packages.containsKey("skipped"))
    }

    @Test fun scanIgnoresOversizedAndUnreadableFiles() {
        val root = temp.newFolder()
        File(root, "go.mod").writeText("module example.com/app\n")
        File(root, "big.go").writeText("package big\n" + "//".repeat(600 * 1024))
        File(root, "ok.go").writeText("package ok\nfunc Fine() {}\n")
        val snapshot = GoWorkspaceIndex.scanForTest(root)
        assertTrue(snapshot.packages.containsKey("ok"))
        assertFalse(snapshot.packages.containsKey("big"))
    }

    @Test fun exportedSymbolsAreLimitedAndDeduplicated() {
        val root = temp.newFolder()
        File(root, "go.mod").writeText("module example.com/app\n")
        val body = StringBuilder("package p\n")
        repeat(1200) { body.append("func F$it() {}\n") }
        File(root, "p.go").writeText(body.toString())
        val symbols = GoWorkspaceIndex.scanForTest(root).packages["p"].orEmpty()
        assertTrue(symbols.size <= 600)
        assertEquals(symbols.size, symbols.distinctBy { it.text }.size)
    }

    @Test fun invalidateDropsCache() {
        val root = temp.newFolder()
        File(root, "go.mod").writeText("module example.com/app\n")
        File(root, "a.go").writeText("package app\nfunc Before() {}\n")
        GoWorkspaceIndex.invalidate()
        File(root, "b.go").writeText("package app\nfunc After() {}\n")
        val snapshot = GoWorkspaceIndex.scanForTest(root)
        val names = snapshot.packages["app"].orEmpty().map { it.text }
        assertTrue("Before" in names && "After" in names)
    }
}
