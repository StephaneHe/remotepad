import sys, base64, pathlib
s = pathlib.Path(sys.argv[2])
d = pathlib.Path(sys.argv[1])
d.parent.mkdir(parents=True, exist_ok=True)
d.write_bytes(base64.b64decode(s.read_text().strip()))
print(str(d) + ": " + str(d.stat().st_size) + " bytes")
s.unlink()
