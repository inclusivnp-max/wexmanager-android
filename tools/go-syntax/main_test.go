package main

import (
	"bytes"
	"encoding/json"
	"strings"
	"testing"
)

func TestValidGoAndUnresolvedImports(t *testing.T) {
	cases := []string{
		"package main\nfunc main() {}\n",
		"package main\nimport \"example.com/not-installed\"\nfunc main() { unknown() }\n",
		"package p\nfunc F[T any](x T) T { return x }\n",
		"package p\n// } (\nvar s = `{{(`\n",
	}
	for _, src := range cases {
		result := check(request{ID: 7, Path: "main.go", Source: src})
		if result.ID != 7 || result.Error != "" || len(result.Problems) != 0 {
			t.Fatalf("%s: %+v", src, result)
		}
	}
}

func TestErrorsAndOffsets(t *testing.T) {
	source := "package main\nfunc main( {\n"
	r := check(request{Source: source})
	if len(r.Problems) == 0 {
		t.Fatal("expected syntax errors")
	}
	for _, p := range r.Problems {
		if p.Line < 2 || p.Column < 1 || p.Start < 0 || p.End > len(source) || p.End <= p.Start || p.Message == "" {
			t.Fatalf("invalid region: %+v", p)
		}
	}
}

func TestUTF16AndPhysicalLines(t *testing.T) {
	source := "package main\n//line imaginary.go:900\nfunc main() {\n s := \"😀\"; привет := @\n}\n"
	result := check(request{Source: source})
	found := false
	for _, p := range result.Problems {
		if strings.Contains(p.Message, "illegal character") {
			found = true
			if p.Line != 4 || p.Column != 23 {
				t.Fatalf("physical UTF-16 location: %+v", p)
			}
		}
	}
	if !found {
		t.Fatalf("missing illegal character: %+v", result)
	}
}

func TestEmptyAndMissingClosingBrace(t *testing.T) {
	for _, src := range []string{"", "package main\nfunc main() {", "package main\nfunc main() {\n"} {
		r := check(request{Source: src})
		if len(r.Problems) == 0 {
			t.Fatalf("invalid accepted: %q", src)
		}
		for _, p := range r.Problems {
			if p.Start < 0 || p.End < p.Start || p.End > len(src) {
				t.Fatalf("invalid EOF region: %+v", p)
			}
		}
	}
}

func TestStreamingAndRecovery(t *testing.T) {
	var in, out bytes.Buffer
	in.WriteString("not-json\n")
	for i, src := range []string{"package p\nfunc Broken( {}", "package p\nfunc Fixed() {}"} {
		if err := json.NewEncoder(&in).Encode(request{ID: int64(i + 1), Path: "unsaved.go", Source: src}); err != nil {
			t.Fatal(err)
		}
	}
	if err := serve(&in, &out); err != nil {
		t.Fatal(err)
	}
	decoder := json.NewDecoder(&out)
	for i := 0; i < 3; i++ {
		var r response
		if err := decoder.Decode(&r); err != nil {
			t.Fatal(err)
		}
		if i == 0 && r.Error == "" || i == 1 && len(r.Problems) == 0 || i == 2 && (r.ID != 2 || len(r.Problems) != 0) {
			t.Fatalf("response %d: %+v", i, r)
		}
	}
}

func TestSizeAndProblemLimits(t *testing.T) {
	r := check(request{Source: strings.Repeat("x", maxSourceBytes+1)})
	if r.Error == "" || len(r.Problems) != 0 {
		t.Fatalf("size limit: %+v", r)
	}
	r = check(request{Source: "package p\n" + strings.Repeat("var @\n", 500)})
	if len(r.Problems) == 0 || len(r.Problems) > maxProblems {
		t.Fatalf("diagnostic limit: %d", len(r.Problems))
	}
}

func TestEOFUnderlinesCodeNotTrailingNewlines(t *testing.T) {
	source := "package main\nfunc main() {\n\n"
	result := check(request{Source: source})
	if len(result.Problems) == 0 {
		t.Fatal("missing EOF diagnostics")
	}
	for _, p := range result.Problems {
		if p.Start != strings.Index(source, "{") || p.End != p.Start+1 {
			t.Fatalf("invisible EOF underline: %+v", p)
		}
	}
}
