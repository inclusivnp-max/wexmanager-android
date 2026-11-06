package client.wexmanager.feature.editor.golang

import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.TimeUnit

class GoRunPlanTest {
    @get:Rule val temp = TemporaryFolder()
    private fun project(module: Boolean = true, name: String = "main.go"): GoProject {
        val dir = temp.newFolder()
        if (module) File(dir, "go.mod").writeText("module example.com/test\n\ngo 1.20\n")
        return GoProject.detect(File(dir, name).apply { writeText("package main\nfunc main() {}\n") }.path)
    }

    @Test fun detectsNearestModuleAndCmdPackage() {
        val p = project()
        val cmd = File(p.root, "cmd/server").apply { mkdirs() }
        val file = File(cmd, "main.go").apply { writeText("package main") }
        val detected = GoProject.detect(file.path)
        assertEquals(p.root, detected.moduleRoot)
        assertEquals(GoRunScope.PACKAGE, detected.defaultScope)
        val plan = GoRunPlan.create(detected, GoRunConfig(scope = GoRunScope.PACKAGE), GoAction.RUN)
        assertTrue(plan.commands.contains("'./cmd/server'"))
        File(cmd, "go.mod").writeText("module nested")
        assertEquals(cmd, GoProject.detect(file.path).moduleRoot)
    }

    @Test fun standaloneDefaultsToFile() {
        val p = project(false)
        assertNull(p.moduleRoot)
        assertEquals(GoRunScope.FILE, p.defaultScope)
        assertFalse(GoRunPlan.create(p, GoRunConfig(), GoAction.RUN).commands.contains("go mod tidy"))
    }

    @Test fun parsesArgumentsWithoutEvaluation() {
        assertEquals(listOf("--name", "hello world", "", "a b", "\$(touch /tmp/nope)", "it's"),
            parseGoArguments("--name \"hello world\" '' a\\ b '\$(touch /tmp/nope)' \"it's\""))
        assertEquals(emptyList<String>(), parseGoArguments("  \t"))
    }

    @Test fun rejectsIncompleteArguments() {
        listOf("'unclosed", "\"unclosed", "last\\").forEach {
            assertThrows(IllegalArgumentException::class.java) { parseGoArguments(it) }
        }
    }

    @Test fun quotesPathsAndShellMetacharacters() {
        val value = "hello ' world; \$(touch nope)\nnext"
        val p = ProcessBuilder("bash", "-c", "printf '%s' ${shellQuote(value)}").start()
        assertEquals(value, p.inputStream.bufferedReader().readText())
        assertEquals(0, p.waitFor())
    }

    @Test fun dependencySyncCanBeDisabled() {
        val p = project()
        assertTrue(GoRunPlan.create(p, GoRunConfig(), GoAction.RUN).commands.contains("go mod tidy"))
        assertFalse(GoRunPlan.create(p, GoRunConfig(syncImports = false), GoAction.RUN).commands.contains("go mod tidy"))
    }

    @Test fun validatesModuleOperationsAndTestFiles() {
        val p = project(false)
        assertThrows(IllegalArgumentException::class.java) { GoRunPlan.create(p, GoRunConfig(), GoAction.GET, "x/y") }
        assertThrows(IllegalArgumentException::class.java) { GoRunPlan.create(p, GoRunConfig(), GoAction.TIDY) }
        assertThrows(IllegalArgumentException::class.java) { GoRunPlan.create(p, GoRunConfig(), GoAction.INIT, "-bad") }
        assertThrows(IllegalArgumentException::class.java) { GoRunPlan.create(project(name = "main_test.go"), GoRunConfig(), GoAction.RUN) }
        assertEquals("go test ./...", GoRunPlan.create(p, GoRunConfig(), GoAction.TEST).preview)
    }

    @Test fun compilesPackageAndStreamsRealGoStdin() {
        val p = project()
        p.file.writeText("""
            package main
            import ("fmt"; "os"; "bufio")
            func main() { var name string; fmt.Fscan(bufio.NewReader(os.Stdin), &name); fmt.Println(greet(name), os.Args[1]) }
        """.trimIndent())
        File(p.root, "greet.go").writeText("package main\nfunc greet(s string) string { return \"hello-\" + s }\n")
        val plan = GoRunPlan.create(p, GoRunConfig(scope = GoRunScope.PACKAGE, arguments = "'two words'"), GoAction.RUN)
        val bridge = temp.newFolder("bridge ' quoted")
        val process = launch(bridge, plan.commands)
        await(bridge) { it == "running" }
        File(bridge, "input-000000000001").writeText("Wex\n")
        assertTrue(process.waitFor(60, TimeUnit.SECONDS))
        assertEquals(File(bridge, "output").readText(), 0, process.exitValue())
        assertTrue(File(bridge, "output").readText().contains("hello-Wex two words"))
        assertEquals("exit:0", File(bridge, "state").readText())
    }

    @Test fun propagatesCompileFailure() {
        val p = project(false)
        p.file.writeText("package main\nfunc main( broken")
        val bridge = temp.newFolder()
        val process = launch(bridge, GoRunPlan.create(p, GoRunConfig(), GoAction.RUN).commands)
        assertTrue(process.waitFor(30, TimeUnit.SECONDS))
        assertNotEquals(0, process.exitValue())
        assertTrue(File(bridge, "state").readText().startsWith("exit:"))
        assertTrue(File(bridge, "output").readText(), File(bridge, "output").readText().contains("syntax error"))
    }

    @Test fun supportsShellCommandsAndEof() {
        val p = project()
        val bridge = temp.newFolder()
        val process = launch(bridge, GoRunPlan.create(p, GoRunConfig(), GoAction.CONSOLE).commands)
        await(bridge) { it == "running" }
        File(bridge, "input-000000000001").writeText("printf 'console-ok\\n'\n")
        File(bridge, "eof").writeText("")
        assertTrue(process.waitFor(10, TimeUnit.SECONDS))
        assertEquals(0, process.exitValue())
        assertTrue(File(bridge, "output").readText().contains("console-ok"))
    }

    @Test fun stopsProcessTreeAndCleansPrivateRuntime() {
        val bridge = temp.newFolder()
        val process = launch(bridge, "sleep 90 &\nwait")
        await(bridge) { it == "running" }
        File(bridge, "stop").writeText("")
        assertTrue("Stop must complete promptly", process.waitFor(8, TimeUnit.SECONDS))
        assertNotEquals(0, process.exitValue())
        assertTrue(File(bridge, "state").readText().startsWith("exit:"))
    }

    @Test fun initializesModuleAndRunsTests() {
        val p = project(false)
        val bridge = temp.newFolder()
        val init = launch(bridge, GoRunPlan.create(p, GoRunConfig(), GoAction.INIT, "example.com/new").commands)
        assertTrue(init.waitFor(20, TimeUnit.SECONDS))
        assertEquals(0, init.exitValue())
        assertTrue(File(p.root, "go.mod").readText().contains("example.com/new"))
        val tests = launch(bridge, GoRunPlan.create(GoProject.detect(p.file.path), GoRunConfig(), GoAction.TEST).commands)
        assertTrue(tests.waitFor(30, TimeUnit.SECONDS))
        assertEquals(File(bridge, "output").readText(), 0, tests.exitValue())
    }

    @Test fun automaticallyResolvesImportedLocalModule() {
        val p = project()
        val dependency = temp.newFolder("dependency")
        File(dependency, "go.mod").writeText("module example.com/greeting\n\ngo 1.20\n")
        File(dependency, "hello.go").writeText("package greeting\nfunc Hello() string { return \"imports-ok\" }\n")
        File(p.root, "go.mod").appendText("\nreplace example.com/greeting => ${dependency.path}\n")
        p.file.writeText("package main\nimport (\"fmt\"; \"example.com/greeting\")\nfunc main() { fmt.Println(greeting.Hello()) }\n")
        val bridge = temp.newFolder()
        val process = launch(bridge, GoRunPlan.create(p, GoRunConfig(scope = GoRunScope.PACKAGE), GoAction.RUN).commands)
        assertTrue(process.waitFor(45, TimeUnit.SECONDS))
        assertEquals(File(bridge, "output").readText(), 0, process.exitValue())
        assertTrue(File(bridge, "output").readText().contains("imports-ok"))
        assertTrue(File(p.root, "go.mod").readText().contains("require example.com/greeting"))
    }

    @Test fun stdinEofAndProgramExitCodeArePreserved() {
        val p = project(false, "name ' with spaces.go")
        p.file.writeText("package main\nimport (\"fmt\"; \"os\"; \"io\")\nfunc main() { b,_ := io.ReadAll(os.Stdin); fmt.Printf(\"input=%s\", b); os.Exit(7) }\n")
        val bridge = temp.newFolder()
        val process = launch(bridge, GoRunPlan.create(p, GoRunConfig(), GoAction.RUN).commands)
        await(bridge) { it == "running" }
        File(bridge, "input-000000000001").writeText("hello stdin\n")
        File(bridge, "eof").writeText("")
        assertTrue(process.waitFor(45, TimeUnit.SECONDS))
        assertEquals(File(bridge, "output").readText(), 7, process.exitValue())
        assertTrue(File(bridge, "output").readText().contains("input=hello stdin"))
        assertEquals("exit:7", File(bridge, "state").readText())
    }

    @Test fun stopWorksEvenWhenStdinPipeIsFull() {
        val bridge = temp.newFolder()
        val process = launch(bridge, "sleep 90")
        await(bridge) { it == "running" }
        File(bridge, "input-000000000001").writeText("x".repeat(300_000))
        Thread.sleep(200)
        File(bridge, "stop").writeText("")
        assertTrue("Blocked stdin must not block Stop", process.waitFor(8, TimeUnit.SECONDS))
        assertNotEquals(0, process.exitValue())
    }

    @Test fun boundsLogWithoutLimitingCompilerFiles() {
        val bridge = temp.newFolder()
        val process = launch(bridge, "yes output")
        assertTrue(process.waitFor(8, TimeUnit.SECONDS))
        assertTrue(File(bridge, "output").length() <= 8_390_000)
        assertTrue(File(bridge, "state").readText().startsWith("exit:"))
    }

    @Test fun consoleHistoryIsBoundedAndRecentCommandsComeFirst() {
        val history = (0..60).map { "command$it" }
        val next = rememberGoCommand(history,"command20")
        assertEquals("command20",next.first())
        assertEquals(40,next.size)
        assertEquals(1,next.count { it == "command20" })
        assertEquals(history,rememberGoCommand(history,"  "))
    }

    private fun launch(bridge: File, commands: String): Process {
        assumeTrue("Bash integration tests require a Unix host", File("/bin/bash").canExecute())
        if (commands.contains("command -v go")) {
            assumeTrue("Go integration tests require Go SDK", runCatching { ProcessBuilder("go", "version").start().waitFor() == 0 }.getOrDefault(false))
        }
        val builder = ProcessBuilder("bash", "-c", GoSessionScript.create(bridge, commands))
        builder.environment()["GOPATH"] = File(temp.root, "go-path").path
        builder.environment()["GOTOOLCHAIN"] = "local"
        builder.environment()["GOFLAGS"] = "-buildvcs=false"
        // Reuse the host standard library cache to avoid rebuilding it for each test.
        builder.environment()["GOCACHE"] = System.getenv("WEX_TEST_GOCACHE") ?: "/tmp/wex-go-test-cache"
        return builder.start()
    }

    private fun await(bridge: File, accept: (String) -> Boolean) {
        val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(8)
        while (System.nanoTime() < until) {
            val status = File(bridge, "state").takeIf { it.exists() }?.readText().orEmpty()
            if (accept(status)) return
            Thread.sleep(30)
        }
        fail("No running state: ${File(bridge, "output").takeIf { it.exists() }?.readText()}")
    }
}
