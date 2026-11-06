package main

import (
	"encoding/json"
	"strings"
	"testing"
)

func TestFormatUsesRealGofmt(t *testing.T) {
	src := "package main\nimport \"fmt\"\nfunc main(){\nfmt.Println(  \"hi\" )\n}\n"
	r := formatSource(request{ID: 3, Path: "main.go", Source: src})
	if r.Error != "" || r.ID != 3 {
		t.Fatalf("%+v", r)
	}
	want := "package main\n\nimport \"fmt\"\n\nfunc main() {\n\tfmt.Println(\"hi\")\n}\n"
	if r.Formatted != want {
		t.Fatalf("got:\n%q\nwant:\n%q", r.Formatted, want)
	}
}

func TestFormatIsIdempotentAndRejectsBrokenSource(t *testing.T) {
	src := "package p\n\nfunc F(a int) int { return a }\n"
	once := formatSource(request{Source: src})
	if once.Error != "" || once.Formatted != src {
		t.Fatalf("not stable: %+v", once)
	}
	broken := formatSource(request{Source: "package p\nfunc F( {"})
	if broken.Error == "" || broken.Formatted != "" {
		t.Fatalf("broken source must not format: %+v", broken)
	}
}

func TestFormatRejectsUnicodeAndSizeLimits(t *testing.T) {
	if r := formatSource(request{Source: strings.Repeat("x", maxSourceBytes+1)}); r.Error == "" {
		t.Fatal("size limit missing")
	}
	if r := formatSource(request{Source: "package p\nvar s = \"\xff\"\n"}); r.Error == "" {
		t.Fatal("invalid UTF-8 accepted")
	}
}

func TestSymbolsCarryOffsetsLinesAndReceivers(t *testing.T) {
	src := "package main\n\nimport (\n\t\"fmt\"\n\t\"os\"\n)\n\ntype Point struct {\n\tX int\n\tY int\n}\n\nfunc (p *Point) Move(dx int) {\n\tp.X += dx\n}\n\nfunc main() {\n\tfmt.Println(os.Args)\n}\n\nvar Version = \"1\"\n\nconst Limit = 10\n"
	r := symbolsOf(request{ID: 9, Path: "main.go", Source: src})
	if r.Error != "" || r.ID != 9 {
		t.Fatalf("%+v", r)
	}
	byName := map[string]symbol{}
	for _, s := range r.Symbols {
		byName[s.Name] = s
		if s.Line < 1 || s.Column < 1 || s.Start < 0 || s.End < s.Start || s.End > len(src) {
			t.Fatalf("invalid offsets: %+v", s)
		}
		if s.Kind == "" || s.Name == "" {
			t.Fatalf("empty symbol: %+v", s)
		}
	}
	if got := byName["Move"]; got.Kind != "method" || got.Receiver != "*Point" {
		t.Fatalf("method receiver: %+v", got)
	}
	if got := byName["Point"]; got.Kind != "type" || got.Line != 8 {
		t.Fatalf("type: %+v", got)
	}
	if got := byName["X"]; got.Kind != "field" || got.Receiver != "Point" || got.Detail != "int" {
		t.Fatalf("field: %+v", got)
	}
	for _, name := range []string{"fmt", "os", "main", "Version", "Limit", "Y"} {
		if _, ok := byName[name]; !ok {
			t.Fatalf("missing symbol %q in %+v", name, r.Symbols)
		}
	}
	for _, s := range r.Symbols {
		if s.Name == "main" && s.Kind != "func" {
			t.Fatalf("main kind: %+v", s)
		}
		if s.Name == "fmt" && s.Kind != "import" {
			t.Fatalf("import kind: %+v", s)
		}
	}
}

func TestSymbolsSkipBlankIdentifiersAndBlankImports(t *testing.T) {
	src := "package p\n\nimport _ \"net/http/pprof\"\n\nfunc F() {\n\t_ = 1\n}\n"
	r := symbolsOf(request{Source: src})
	for _, s := range r.Symbols {
		if s.Name == "_" {
			t.Fatalf("blank identifier kept: %+v", s)
		}
	}
}

func TestSymbolsRejectBrokenSource(t *testing.T) {
	if r := symbolsOf(request{Source: "package p\nfunc ("}); r.Error == "" {
		t.Fatalf("broken source accepted: %+v", r)
	}
}

func TestUnknownOperationFallsBackToSyntaxCheck(t *testing.T) {
	src := "package p\nfunc F() {}\n"
	for _, op := range []string{"", "check", "unexpected"} {
		r := checkRequestForOp(t, request{ID: 1, Path: "x.go", Source: src, Op: op})
		if r.Error != "" || len(r.Problems) != 0 {
			t.Fatalf("op %q: %+v", op, r)
		}
	}
}

func checkRequestForOp(t *testing.T, req request) response {
	t.Helper()
	var in, out strings.Builder
	encoder := json.NewEncoder(&in)
	if err := encoder.Encode(req); err != nil {
		t.Fatal(err)
	}
	if err := serve(strings.NewReader(in.String()), &out); err != nil {
		t.Fatal(err)
	}
	var result response
	if err := json.NewDecoder(strings.NewReader(out.String())).Decode(&result); err != nil {
		t.Fatal(err)
	}
	return result
}
