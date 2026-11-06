package client.wexmanager.feature.editor.golang

import java.io.File

/**
 * Only the bridge files live on shared storage. FIFO, executable and process group live in
 * Termux's executable private TMPDIR. The script itself travels in the explicit command intent.
 */
internal object GoSessionScript {
    private val INPUT_RELAY = """
        while true; do
            for input in "${'$'}WEX_GO_SHARED"/input-*; do
                [ -f "${'$'}input" ] || continue
                cat "${'$'}input" >&3
                rm -f "${'$'}input"
            done
            [ -f "${'$'}WEX_GO_SHARED/eof" ] && exit 0
            sleep 0.1
        done
    """.trimIndent()

    fun create(directory: File, commands: String): String = """
        set +e
        shared=${shellQuote(directory.absolutePath)}
        exec >>"${'$'}shared/output" 2>&1
        state() { printf '%s' "${'$'}1" >"${'$'}shared/state.tmp"; mv -f "${'$'}shared/state.tmp" "${'$'}shared/state"; }
        state starting
        private=${'$'}(mktemp -d "${'$'}{TMPDIR:-/tmp}/wex-go.XXXXXXXX") || { state exit:125; exit 125; }
        child=
        feeder=
        cleanup() {
            if [ -n "${'$'}child" ]; then kill -KILL -- "-${'$'}child" 2>/dev/null; fi
            if [ -n "${'$'}feeder" ]; then kill -KILL -- "-${'$'}feeder" 2>/dev/null; fi
            rm -rf "${'$'}private"
        }
        trap cleanup EXIT
        trap 'state exit:143; exit 143' HUP TERM INT
        mkfifo "${'$'}private/stdin" || { state exit:125; exit 125; }
        exec 3<>"${'$'}private/stdin"
        export WEX_GO_BIN="${'$'}private/program"
        setsid bash -c ${shellQuote("set -o pipefail\n(\n" + commands + "\n) 2>&1 | head -c 8388608")} <"${'$'}private/stdin" 3>&- &
        child=${'$'}!
        export WEX_GO_SHARED="${'$'}shared"
        setsid bash -c ${shellQuote(INPUT_RELAY)} &
        feeder=${'$'}!
        exec 3>&-
        state running
        ticks=0
        stopping=0
        while kill -0 "${'$'}child" 2>/dev/null; do
            if [ -f "${'$'}shared/stop" ]; then
                case "${'$'}stopping" in
                    0) kill -INT -- "-${'$'}child" 2>/dev/null ;;
                    10) kill -TERM -- "-${'$'}child" 2>/dev/null ;;
                    20) kill -KILL -- "-${'$'}child" 2>/dev/null ;;
                esac
                stopping=${'$'}((stopping + 1))
            fi
            ticks=${'$'}((ticks + 1))
            if [ "${'$'}((ticks % 10))" = 0 ]; then
                touch "${'$'}shared/heartbeat"
                bytes=${'$'}(wc -c <"${'$'}shared/output")
                if [ "${'$'}bytes" -ge 8388608 ] && [ ! -f "${'$'}shared/stop" ]; then
                    printf '\n%s\n' 'Лимит журнала 8 MiB: процесс остановлен.'
                    touch "${'$'}shared/stop"
                fi
            fi
            sleep 0.1
        done
        wait "${'$'}child"
        result=${'$'}?
        state "exit:${'$'}result"
        exit "${'$'}result"
    """.trimIndent()
}
