"""Create an honest, initially pending Appendix A/B/C/D acceptance ledger from the authority."""
import hashlib
import json
from pathlib import Path
import re
import sys
from coverage_map import annotate

source = Path(sys.argv[1]).read_bytes()
document = source.decode("utf-8")
checklist = document.split("## Appendix A. ",1)[1].split("## Appendix B. ",1)[0]
tests = document.split("## Appendix B. ",1)[1].split("## Appendix C. ",1)[0]
items = re.findall(r"^- (.+)$",checklist,re.M)
cases = []
for line in tests.splitlines():
    match = re.match(r"\| (IT-\d\d) \| (.*?) \| (.*?) \|$",line)
    if match:
        cases.append({"id":match[1],"scenario":match[2],"expected":match[3],
                      "palleria":"not_run","python":"not_run","isolated_relay":"not_run",
                      "result":"pending_three_party_execution","artifacts":[]})
assert len(cases) == 50
vectors = re.findall(r"^### (C\.\d+) (.+)$",document,re.M)
examples = re.findall(r"^### (D\.\d+) (.+)$",document,re.M)
ledger = {"document_id":"PALLASYNC-3.0-R1","protocol":"pallasync/3","spec_sha256":hashlib.sha256(source).hexdigest(),
          "conformance_claim":False,
          "checklist":[{"id":f"A-{i:02}","requirement":item,"sections":[],"implementation":[],"tests":[],"result":"pending"}
                       for i,item in enumerate(items,1)],
          "interoperability":cases,
          "vectors":[{"id":key,"name":name,"result":"pending_complete_vector_execution","artifacts":[]} for key,name in vectors],
          "examples":[{"id":key,"name":name,"result":"pending_complete_example_execution","artifacts":[]} for key,name in examples]}
target = Path(sys.argv[2])
target.parent.mkdir(parents=True,exist_ok=True)
# Retain recorded outcomes/artifacts on regeneration only under the exact unchanged authority.
if target.exists():
    old = json.loads(target.read_text(encoding="utf-8"))
    if old.get("spec_sha256") != ledger["spec_sha256"]:
        raise ValueError("Authority changed; acceptance outcomes require explicit review")
    for category in ("checklist", "interoperability", "vectors", "examples"):
        existing = {row["id"]: row for row in old[category]}
        for row in ledger[category]:
            saved = existing[row["id"]]
            for key, value in saved.items():
                if key not in ("id", "requirement", "scenario", "expected", "name"):
                    row[key] = value
for category in ("checklist", "vectors", "examples"):
    for row in ledger[category]:
        for field in ("requirement", "name"):
            if field in row:
                row[field] = row[field].rstrip("\r")
annotate(ledger)
target.write_text(json.dumps(ledger,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
