package client.wexmanager.feature.editor.golang

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Новые действия IDE: сборка, vet, теги, окружение, флаги тестов и разбор ошибок компилятора. */
class GoIdeActionsTest {
    @get:Rule val temp = TemporaryFolder()

    private fun project(module: Boolean = true, name: String = "main.go"): GoProject {
        val dir = temp.newFolder()
        if (module) File(dir, "go.mod").writeText("module example.com/test\n\ngo 1.22\n")
        return GoProject.detect(File(dir, name).apply { writeText("package main\nfunc main() {}\n") }.path)
    }

    @Test fun buildActionWritesBinaryWithProjectName() {
        val p = project(false)
        val plan = GoRunPlan.create(p, GoRunConfig(), GoAction.BUILD)
        assertTrue(plan.commands.contains("go build"))
        assertTrue(plan.commands.contains(projectBinaryName(p)))
        assertTrue(plan.preview.startsWith("go build -o ${projectBinaryName(p)} "))
        assertTrue(plan.preview.contains(p.file.name))
    }

    @Test fun buildOutputNameIsValidatedAndUsed() {
        val p = project(false)
        val plan = GoRunPlan.create(p, GoRunConfig(buildOutput = "server"), GoAction.BUILD)
        assertTrue(plan.commands.contains(File(p.root, "server").absolutePath))
        assertThrows(IllegalArgumentException::class.java) {
            GoRunPlan.create(p, GoRunConfig(buildOutput = "nested/server"), GoAction.BUILD)
        }
        assertThrows(IllegalArgumentException::class.java) {
            GoRunPlan.create(p, GoRunConfig(buildOutput = "../escape"), GoAction.BUILD)
        }
    }

    @Test fun binaryNameFollowsDirectoryNotFileName() {
        val dir = temp.newFolder("My-App")
        File(dir, "main.go").writeText("package main\n")
        val p = GoProject.detect(File(dir, "main.go").path)
        assertEquals("my-app", projectBinaryName(p))
    }

    @Test fun vetRunsOverWholeModuleWhenFileHasModule() {
        val p = project()
        val filePlan = GoRunPlan.create(p, GoRunConfig(scope = GoRunScope.FILE), GoAction.VET)
        assertTrue(filePlan.commands.contains("go vet './...'"))
        val plain = project(false)
        val plainPlan = GoRunPlan.create(plain, GoRunConfig(), GoAction.VET)
        assertTrue(plainPlan.commands.contains("go vet"))
        assertFalse(plainPlan.commands.contains("./..."))
    }

    @Test fun buildTagsReachBuildVetAndTest() {
        val p = project()
        val config = GoRunConfig(buildTags = "android, integration")
        assertTrue(GoRunPlan.create(p, config, GoAction.BUILD).commands.contains("-tags 'android integration'"))
        assertTrue(GoRunPlan.create(p, config, GoAction.VET).commands.contains("-tags 'android integration'"))
        assertTrue(GoRunPlan.create(p, config, GoAction.TEST).commands.contains("-tags 'android integration'"))
        assertTrue(GoRunPlan.create(p, config, GoAction.RUN).commands.contains("-tags 'android integration'"))
    }

    @Test fun testFlagsAreParsedNotEvaluated() {
        val p = project()
        val plan = GoRunPlan.create(p, GoRunConfig(testFlags = "-v -run 'Test Name'"), GoAction.TEST)
        // В скрипт флаги попадают с надёжным экранированием, в предпросмотр — читаемо.
        assertTrue(plan.commands.contains("go test '-v' '-run' 'Test Name' ./..."))
        assertEquals("go test -v -run 'Test Name' ./...", plan.preview)
        // Значение из кавычек не исполняется оболочкой.
        val injected = GoRunPlan.create(p, GoRunConfig(testFlags = "'\$(touch nope)'"), GoAction.TEST)
        assertTrue(injected.commands.contains("'\$(touch nope)'"))
        assertFalse(injected.commands.contains("\$(touch nope) "))
    }

    @Test fun environmentIsExportedBeforeGoRuns() {
        val p = project()
        val plan = GoRunPlan.create(p, GoRunConfig(envVars = "GOOS=linux\n# комментарий\nCGO_ENABLED=0"), GoAction.RUN)
        val envAt = plan.commands.indexOf("export GOOS='linux'")
        val goAt = plan.commands.indexOf("go build")
        assertTrue(envAt in 0 until goAt)
        assertTrue(plan.commands.contains("export CGO_ENABLED='0'"))
    }

    @Test fun environmentQuotesAndCommentsAreHandled() {
        assertEquals(listOf("A" to "hello world", "B" to "x=y"), parseGoEnv("A=\"hello world\"\n# skip\nB='x=y'"))
        assertEquals(emptyList<Pair<String, String>>(), parseGoEnv("   \n\n"))
        assertThrows(IllegalArgumentException::class.java) { parseGoEnv("JUSTKEY") }
        assertThrows(IllegalArgumentException::class.java) { parseGoEnv("1BAD=x") }
        assertThrows(IllegalArgumentException::class.java) { parseGoEnv("A=1\nA=2") }
    }

    @Test fun tagsRejectUnsafeCharacters() {
        assertEquals(listOf("android", "integration"), parseGoTags("android integration"))
        assertEquals(listOf("a", "b"), parseGoTags("a,b"))
        assertEquals(listOf("go1.22"), parseGoTags("go1.22"))
        assertThrows(IllegalArgumentException::class.java) { parseGoTags("bad;rm -rf") }
        assertThrows(IllegalArgumentException::class.java) { parseGoTags("\$(whoami)") }
    }

    @Test fun compilerErrorsBecomeClickablePositions() {
        val output = """
            # example.com/app
            ./main.go:12:5: undefined: fmt
            /abs/path/pkg/util.go:3:1: syntax error: unexpected }
            not a diagnostic line
            main.go:1:1: too many errors
        """.trimIndent()
        val errors = parseGoErrors(output)
        assertEquals(3, errors.size)
        assertEquals(GoError("main.go", 12, 5, "undefined: fmt"), errors[0])
        assertEquals("abs/path/pkg/util.go", errors[1].file)
        assertEquals(3, errors[1].line)
        assertEquals(1, errors[2].line)
    }

    @Test fun compilerErrorsDeduplicateAndDefaultColumn() {
        val errors = parseGoErrors("./a.go:2: undefined: x\n./a.go:2: undefined: x\n./a.go:5:4: broken")
        assertEquals(2, errors.size)
        assertEquals(1, errors[0].column)
        assertEquals("undefined: x", errors[0].message)
    }

    @Test fun compilerErrorsResolveRelativePathsAgainstProject() {
        val p = project(false)
        val nested = File(p.root, "pkg/sub").apply { mkdirs() }
        File(nested, "util.go").writeText("package sub\n")
        val byRoot = GoError("pkg/sub/util.go", 1, 1, "x").resolveIn(p.root, p.file)
        assertEquals(File(nested, "util.go").canonicalFile, byRoot?.canonicalFile)
        val bySibling = GoError("main.go", 1, 1, "x").resolveIn(p.root, p.file)
        assertEquals(p.file.canonicalFile, bySibling?.canonicalFile)
    }

    @Test fun formatActionIsNotPartOfTermuxPlan() {
        // FMT уходит во встроенный сервис, но план остаётся валидным для тестов и CLI.
        val p = project(false)
        val plan = GoRunPlan.create(p, GoRunConfig(), GoAction.FMT)
        assertTrue(plan.commands.contains("gofmt -w"))
        assertEquals("gofmt -w main.go", plan.preview)
    }

    @Test fun everyActionProducesNonEmptyPreview() {
        val p = project()
        val config = GoRunConfig()
        listOf(GoAction.RUN, GoAction.TEST, GoAction.BUILD, GoAction.VET, GoAction.FMT, GoAction.TIDY, GoAction.CONSOLE).forEach { action ->
            val plan = GoRunPlan.create(p, config, action)
            assertTrue("$action", plan.preview.isNotBlank())
            assertTrue("$action", plan.commands.startsWith("set -e"))
        }
    }
}
