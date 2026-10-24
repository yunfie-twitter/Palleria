"""Extract public signed examples from the normative document, without regenerating them."""
import json
import pathlib
import re
import sys

document = pathlib.Path(sys.argv[1]).read_text(encoding="utf-8")
destination = pathlib.Path(sys.argv[2])
destination.mkdir(parents=True, exist_ok=True)
sections = {"record": "C.5", "transparency": "C.9", "receipt": "C.9",
            "delete_receipt": "C.11", "deletion_bundle": "C.11"}
types = {"record": None, "transparency": "PALLASYNC-TRANSPARENCY-ENTRY-v3",
         "receipt": "PALLASYNC-UPLOAD-RECEIPT-v3", "delete_receipt": "PALLASYNC-DELETE-RECEIPT-v3",
         "deletion_bundle": "PALLASYNC-DELETION-EVIDENCE-v3"}
for name, section in sections.items():
    text = document.split("### " + section + " ", 1)[1].split("\n### ", 1)[0]
    examples = [json.loads(block) for block in re.findall(r"```json\s*\n(.*?)\n```", text, re.S)]
    selected = [example for example in examples if isinstance(example, dict) and example.get("type") == types[name]]
    if len(selected) != 1:
        raise ValueError(f"Ambiguous/missing public vector {name}")
    (destination / (name + ".json")).write_text(
        json.dumps(selected[0], ensure_ascii=False, separators=(",", ":")) + "\n", encoding="utf-8")
