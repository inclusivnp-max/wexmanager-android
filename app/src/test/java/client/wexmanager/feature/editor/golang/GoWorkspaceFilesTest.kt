package client.wexmanager.feature.editor.golang

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files

class GoWorkspaceFilesTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun createsReadyToRunProject() {
        val root = GoWorkspaceFiles.createProject(temp.root, "hello-go", "example.com/hello")
        assertTrue(File(root, "go.mod").readText().contains("module example.com/hello"))
        assertTrue(File(root, "main.go").readText().contains("func main()"))
        assertTrue(File(root, ".gitignore").isFile)
    }

    @Test fun neverOverwritesExistingProjectsOrFiles() {
        val root = GoWorkspaceFiles.createProject(temp.root, "project", "example.com/project")
        File(root, "main.go").writeText("original")
        assertThrows(IllegalArgumentException::class.java) { GoWorkspaceFiles.createProject(temp.root, "project", "example.com/new") }
        assertThrows(IllegalArgumentException::class.java) { GoWorkspaceFiles.createEntry(root, root, "main.go", false) }
        assertEquals("original", File(root, "main.go").readText())
    }

    @Test fun rejectsTraversalAndInvalidModuleNames() {
        listOf("..", ".", "a/b", "a\\b", "x\u0000", "  ").forEach { name ->
            assertThrows(IllegalArgumentException::class.java) { GoWorkspaceFiles.validName(name) }
        }
        listOf("bad module", "a/../b", "x\nrequire bad", "a//b").forEach { module ->
            assertThrows(IllegalArgumentException::class.java) { GoWorkspaceFiles.createProject(temp.root, "x", module) }
        }
    }

    @Test fun createsFoldersAndUsesSiblingPackageName() {
        val root = temp.newFolder()
        val dir = GoWorkspaceFiles.createEntry(root, root, "internal", true)
        File(dir, "first.go").writeText("package server\n")
        val file = GoWorkspaceFiles.createEntry(root, dir, "second.go", false)
        assertEquals("package server\n\n", file.readText())
        assertTrue(GoWorkspaceFiles.createEntry(root, root, "README.md", false).isFile)
    }

    @Test fun treeIsLazySortedAndExpandable() {
        val root = temp.newFolder()
        val folder = File(root, "z-folder").apply { mkdir() }
        File(folder, "nested.go").writeText("package main")
        File(root, "b.go").writeText("package main")
        File(root, "a.go").writeText("package main")
        File(root, ".git").apply { mkdir() }
        val collapsed = GoWorkspaceFiles.tree(root, emptySet())
        assertEquals(listOf("z-folder", "a.go", "b.go"), collapsed.nodes.map { it.file.name })
        val expanded = GoWorkspaceFiles.tree(root, setOf(folder.path))
        assertEquals(listOf(0, 1, 0, 0), expanded.nodes.map { it.depth })
        assertTrue(expanded.nodes.first().expanded)
        assertFalse(collapsed.truncated)
    }

    @Test fun filterAndRowLimitBoundTheTree() {
        val root = temp.newFolder()
        (1..8).forEach { File(root, "file$it.go").writeText("package main") }
        assertEquals(listOf("file4.go"), GoWorkspaceFiles.tree(root, emptySet(), "4").nodes.map { it.file.name })
        val limited = GoWorkspaceFiles.tree(root, emptySet(), limit = 3)
        assertEquals(3, limited.nodes.size)
        assertTrue(limited.truncated)
    }

    @Test fun refusesBinaryAndOutsideFiles() {
        val root = temp.newFolder()
        val binary = File(root, "binary").apply { writeBytes(byteArrayOf(1, 0, 3)) }
        assertThrows(IllegalArgumentException::class.java) { GoWorkspaceFiles.textFile(root, binary) }
        val outside = temp.newFile()
        assertThrows(IllegalArgumentException::class.java) { GoWorkspaceFiles.textFile(root, outside) }
        assertThrows(IllegalArgumentException::class.java) { GoWorkspaceFiles.createEntry(root, temp.root, "outside.go", false) }
    }

    @Test fun projectSearchFindsCollapsedFilesAndRanksNames() {
        val root = temp.newFolder()
        val nested = File(root,"cmd/server").apply { mkdirs() }
        File(nested,"main.go").writeText("package main")
        File(root,"main_test.go").writeText("package main")
        File(root,"domain.go").writeText("package main")
        val matches = GoWorkspaceFiles.search(root,"main.go")
        assertEquals("main.go",matches.nodes.first().file.name)
        assertEquals(listOf("main.go","domain.go"),matches.nodes.map { it.file.name })
        assertEquals("main.go",GoWorkspaceFiles.search(root,"cmd/server").nodes.single().file.name)
    }

    @Test fun searchLimitsAndParentRevealAreBounded() {
        val root = temp.newFolder()
        val nested = File(root,"internal/server").apply { mkdirs() }
        val file = File(nested,"run.go").apply { writeText("package main") }
        assertEquals(setOf(nested.path,File(root,"internal").path),GoWorkspaceFiles.parents(root,file))
        assertTrue(GoWorkspaceFiles.parents(root,File(root,"main.go")).isEmpty())
        (1..8).forEach { File(root,"match$it.go").writeText("package main") }
        assertEquals(3,GoWorkspaceFiles.search(root,"match",limit=3).nodes.size)
        assertTrue(GoWorkspaceFiles.search(root,"match",limit=3).truncated)
        assertThrows(IllegalArgumentException::class.java) { GoWorkspaceFiles.parents(root,temp.root) }
    }

    @Test fun symlinksNeverEscapeProjectRoot() {
        val root = temp.newFolder()
        val outside = temp.newFolder()
        File(outside, "secret.go").writeText("package secret")
        val link = File(root, "linked")
        Files.createSymbolicLink(link.toPath(), outside.toPath())
        assertThrows(IllegalArgumentException::class.java) { GoWorkspaceFiles.createEntry(root, link, "new.go", false) }
        assertThrows(IllegalArgumentException::class.java) { GoWorkspaceFiles.textFile(root, File(link, "secret.go")) }
        assertEquals(1, GoWorkspaceFiles.tree(root, setOf(link.path)).nodes.size)
    }
}
