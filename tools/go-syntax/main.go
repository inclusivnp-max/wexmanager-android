// WexManager's offline Go service. Sources are parsed, never executed.
package main

import (
	"bufio"
	"bytes"
	"encoding/json"
	"fmt"
	"go/ast"
	"go/format"
	"go/parser"
	"go/scanner"
	"go/token"
	"io"
	"os"
	"runtime"
	"strings"
	"unicode"
	"unicode/utf8"
)

const maxSourceBytes = 1 << 20
const maxProblems = 100
const maxSymbols = 2000

type request struct {
	ID     int64  `json:"id"`
	Path   string `json:"path"`
	Source string `json:"source"`
	Op     string `json:"op"`
}

type problem struct {
	Start   int    `json:"start"`
	End     int    `json:"end"`
	Line    int    `json:"line"`
	Column  int    `json:"column"`
	Message string `json:"message"`
}

type symbol struct {
	Name     string `json:"name"`
	Kind     string `json:"kind"`
	Detail   string `json:"detail,omitempty"`
	Line     int    `json:"line"`
	Column   int    `json:"column"`
	Start    int    `json:"start"`
	End      int    `json:"end"`
	Receiver string `json:"receiver,omitempty"`
}

type response struct {
	ID        int64     `json:"id"`
	Problems  []problem `json:"problems"`
	Error     string    `json:"error,omitempty"`
	Formatted string    `json:"formatted,omitempty"`
	Symbols   []symbol  `json:"symbols,omitempty"`
}

func units(r rune) int {
	if r > 0xffff {
		return 2
	}
	return 1
}

func position(source string, offset int) (index, line, column int) {
	line, column = 1, 1
	for _, r := range source[:offset] {
		n := units(r)
		index += n
		if r == '\n' {
			line++
			column = 1
		} else {
			column += n
		}
	}
	return
}

func region(source string, offset int) (int, int) {
	if len(source) == 0 {
		return 0, 0
	}
	for offset >= len(source) || source[offset] == '\n' || source[offset] == '\r' {
		if offset == 0 {
			break
		}
		_, n := utf8.DecodeLastRuneInString(source[:offset])
		offset -= n
	}
	start, _, _ := position(source, offset)
	r, size := utf8.DecodeRuneInString(source[offset:])
	end := start + units(r)
	if unicode.IsLetter(r) || r == '_' || unicode.IsDigit(r) {
		for _, r := range source[offset+size:] {
			if !unicode.IsLetter(r) && !unicode.IsDigit(r) && r != '_' {
				break
			}
			end += units(r)
			if end-start > 80 {
				break
			}
		}
	}
	return start, end
}

func check(req request) (result response) {
	result.ID = req.ID
	result.Problems = []problem{}
	defer func() {
		if r := recover(); r != nil {
			result.Error = fmt.Sprintf("Parser limit: %v", r)
			result.Problems = []problem{}
		}
	}()
	if len(req.Source) > maxSourceBytes {
		result.Error = "Автопроверка поддерживает Go-файлы до 1 MiB"
		return
	}
	if !utf8.ValidString(req.Source) {
		result.Error = "Исходник должен быть UTF-8"
		return
	}
	_, err := parser.ParseFile(token.NewFileSet(), req.Path, req.Source, parser.AllErrors|parser.SkipObjectResolution)
	if err == nil {
		return
	}
	errors, ok := err.(scanner.ErrorList)
	if !ok {
		result.Error = err.Error()
		return
	}
	seen := map[string]bool{}
	for _, item := range errors {
		offset := item.Pos.Offset
		if offset < 0 {
			offset = 0
		}
		if offset > len(req.Source) {
			offset = len(req.Source)
		}
		for offset > 0 && offset < len(req.Source) && !utf8.RuneStart(req.Source[offset]) {
			offset--
		}
		start, end := region(req.Source, offset)
		_, line, column := position(req.Source, offset)
		key := fmt.Sprintf("%d:%s", start, item.Msg)
		if seen[key] {
			continue
		}
		seen[key] = true
		result.Problems = append(result.Problems, problem{start, end, line, column, item.Msg})
		if len(result.Problems) >= maxProblems {
			break
		}
	}
	return
}

func formatSource(req request) (result response) {
	result.ID = req.ID
	result.Problems = []problem{}
	if len(req.Source) > maxSourceBytes {
		result.Error = "Форматирование поддерживает Go-файлы до 1 MiB"
		return
	}
	if !utf8.ValidString(req.Source) {
		result.Error = "Исходник должен быть UTF-8"
		return
	}
	out, err := format.Source([]byte(req.Source))
	if err != nil {
		if list, ok := err.(scanner.ErrorList); ok && len(list) > 0 {
			result.Error = "gofmt: " + list[0].Error()
		} else {
			result.Error = "gofmt: " + err.Error()
		}
		return
	}
	result.Formatted = string(out)
	return
}

func oneLine(s string, limit int) string {
	s = strings.ReplaceAll(s, "\n", " ")
	s = strings.Join(strings.Fields(s), " ")
	if len(s) > limit {
		s = s[:limit] + "…"
	}
	return s
}

func exprString(fset *token.FileSet, e ast.Expr, limit int) string {
	if e == nil {
		return ""
	}
	var buf bytes.Buffer
	if err := format.Node(&buf, fset, e); err != nil {
		return ""
	}
	return oneLine(buf.String(), limit)
}

func symbolsOf(req request) (result response) {
	result.ID = req.ID
	result.Problems = []problem{}
	result.Symbols = []symbol{}
	defer func() {
		if r := recover(); r != nil {
			result.Error = fmt.Sprintf("Parser limit: %v", r)
			result.Symbols = []symbol{}
		}
	}()
	if len(req.Source) > maxSourceBytes {
		result.Error = "Символы поддерживают Go-файлы до 1 MiB"
		return
	}
	if !utf8.ValidString(req.Source) {
		result.Error = "Исходник должен быть UTF-8"
		return
	}
	fset := token.NewFileSet()
	f, err := parser.ParseFile(fset, req.Path, req.Source, parser.SkipObjectResolution)
	if err != nil {
		if list, ok := err.(scanner.ErrorList); ok && len(list) > 0 {
			result.Error = list[0].Error()
		} else {
			result.Error = err.Error()
		}
		return
	}
	add := func(name string, kind, detail string, pos token.Pos, end token.Pos, receiver string) {
		if len(result.Symbols) >= maxSymbols || name == "" || name == "_" {
			return
		}
		start := fset.Position(pos)
		if !start.IsValid() {
			return
		}
		finish := fset.Position(end)
		s, _, _ := position(req.Source, start.Offset)
		e, _, _ := position(req.Source, finish.Offset)
		_, line, column := position(req.Source, start.Offset)
		result.Symbols = append(result.Symbols, symbol{
			Name: name, Kind: kind, Detail: oneLine(detail, 160),
			Line: line, Column: column, Start: s, End: e, Receiver: receiver,
		})
	}
	for _, imp := range f.Imports {
		path := strings.Trim(imp.Path.Value, `"`)
		name := ""
		if imp.Name != nil {
			name = imp.Name.Name
		} else {
			if i := strings.LastIndex(path, "/"); i >= 0 {
				name = path[i+1:]
			} else {
				name = path
			}
		}
		if name == "_" || name == "." {
			continue
		}
		add(name, "import", path, imp.Pos(), imp.End(), "")
	}
	for _, decl := range f.Decls {
		switch d := decl.(type) {
		case *ast.FuncDecl:
			var buf bytes.Buffer
			fmt.Fprintf(&buf, "func %s(", d.Name.Name)
			if d.Type != nil {
				params := []string{}
				for _, p := range d.Type.Params.List {
					params = append(params, exprString(fset, p.Type, 48))
				}
				buf.WriteString(strings.Join(params, ", "))
				buf.WriteString(")")
				if res := d.Type.Results; res != nil {
					rets := []string{}
					for _, r := range res.List {
						rets = append(rets, exprString(fset, r.Type, 48))
					}
					if len(rets) > 0 {
						buf.WriteString(" " + strings.Join(rets, ", "))
					}
				}
			}
			kind, recv := "func", ""
			if d.Recv != nil && len(d.Recv.List) > 0 {
				kind = "method"
				recv = exprString(fset, d.Recv.List[0].Type, 48)
			}
			add(d.Name.Name, kind, buf.String(), d.Pos(), d.Name.End(), recv)
		case *ast.GenDecl:
			kind := ""
			switch d.Tok {
			case token.TYPE:
				kind = "type"
			case token.VAR:
				kind = "var"
			case token.CONST:
				kind = "const"
			}
			if kind == "" {
				continue
			}
			for _, spec := range d.Specs {
				switch s := spec.(type) {
				case *ast.TypeSpec:
					detail := "type " + s.Name.Name + " " + exprString(fset, s.Type, 96)
					add(s.Name.Name, "type", detail, s.Pos(), s.Name.End(), "")
					if st, ok := s.Type.(*ast.StructType); ok {
						for _, f := range st.Fields.List {
							for _, n := range f.Names {
								add(n.Name, "field", exprString(fset, f.Type, 96), n.Pos(), n.End(), s.Name.Name)
							}
						}
					}
				case *ast.ValueSpec:
					for _, n := range s.Names {
						detail := kind + " " + n.Name
						if t := exprString(fset, s.Type, 96); t != "" {
							detail += " " + t
						}
						add(n.Name, kind, detail, n.Pos(), n.End(), "")
					}
				}
			}
		}
	}
	return
}

func serve(input io.Reader, output io.Writer) error {
	scan := bufio.NewScanner(input)
	scan.Buffer(make([]byte, 64*1024), 8*maxSourceBytes)
	encoder := json.NewEncoder(output)
	for scan.Scan() {
		var req request
		var result response
		if err := json.Unmarshal(scan.Bytes(), &req); err != nil {
			result = response{ID: req.ID, Problems: []problem{}, Error: "Invalid syntax request"}
		} else {
			switch req.Op {
			case "format":
				result = formatSource(req)
			case "symbols":
				result = symbolsOf(req)
			default:
				result = check(req)
			}
		}
		if err := encoder.Encode(result); err != nil {
			return err
		}
	}
	return scan.Err()
}

func main() {
	runtime.GOMAXPROCS(1)
	if err := serve(os.Stdin, os.Stdout); err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
}
