"""Generate literal protocol §19 codes; table headings/statuses are not error codes."""
import argparse
import re
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument("spec", type=Path)
parser.add_argument("output", type=Path)
args = parser.parse_args()
spec = args.spec.read_text(encoding="utf-8")
section = spec[spec.index("### 19.2"):spec.index("### 19.4")]
codes = set()
for row in section.splitlines():
    if row.startswith("| "):
        for cell in row.split("|")[1:3]:
            cell = cell.strip()
            if re.fullmatch(r"[A-Z][A-Z0-9]*(?:_[A-Z0-9]+)+", cell):
                codes.add(cell)
assert "ENVELOPE_ACCESS_DENIED" in codes and "C_RECOVERY_SESSION_EXPIRED" in codes
source = "//! Literal §19 catalogue; diagnostics cannot carry arbitrary text.\n"
source += 'pub const CODES: &[&str] = &[\n' + ''.join(f'    "{code}",\n' for code in sorted(codes)) + '];\n'
source += 'pub fn known(code: &str) -> bool { CODES.binary_search(&code).is_ok() }\n'
server_rows = []
for row in spec[spec.index("### 19.2"):spec.index("### 19.3")].splitlines():
    cells = [cell.strip() for cell in row.split("|")[1:-1]]
    if len(cells) == 4 and cells[0].isdigit() and cells[2] in {"true", "false"}:
        server_rows.append((cells[1], int(cells[0]), cells[2]))
assert len(server_rows) == len({row[0] for row in server_rows})
source += 'pub fn server(code: &str) -> Option<(u16, bool)> {\n    match code {\n'
source += ''.join(f'        "{code}" => Some(({status}, {retry})),\n' for code, status, retry in sorted(server_rows))
source += '        _ => None,\n    }\n}\n'
args.output.write_text(source, encoding="utf-8")
