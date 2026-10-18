"""Real SQLite engine for production repository host tests; no TV or network."""
import json
import sqlite3
import sys
connection = sqlite3.connect(sys.argv[1], isolation_level=None)
for line in sys.stdin:
    try:
        request = json.loads(line)
        cursor = connection.execute(request['sql'], request.get('args', []))
        print(json.dumps({'rows': cursor.fetchall() if cursor.description else [], 'id': cursor.lastrowid, 'changes': cursor.rowcount, 'columns': [d[0] for d in cursor.description] if cursor.description else []}), flush=True)
    except Exception as error:
        print(json.dumps({'error': str(error)}), flush=True)
