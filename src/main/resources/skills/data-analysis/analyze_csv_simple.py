import csv
import sys

file_path = r'E:\worksapce\sts5.1\legend-smartmind\legend-smartmind\src\main\resources\skills\data-analysis\sample_data.csv'

with open(file_path, 'r', encoding='utf-8') as f:
    reader = csv.reader(f)
    headers = next(reader)
    rows = list(reader)

# 1. Column names and inferred types (heuristic: try int/float, else str)
def infer_type(val):
    val = val.strip()
    try:
        int(val)
        return 'int'
    except ValueError:
        try:
            float(val)
            return 'float'
        except ValueError:
            return 'str'

# Get sample values for type inference (first non-header row)
if rows:
    sample_row = rows[0]
    types = [infer_type(v) for v in sample_row]
    col_info = [f'{h} ({t})' for h, t in zip(headers, types)]
else:
    col_info = [f'{h} (str)' for h in headers]

# 2. Row count
n_rows = len(rows)

# 3. First 5 rows (including header for clarity)
first5_lines = [','.join(headers)] + [','.join(row) for row in rows[:5]]

# Build report
report = f"""CSV Analysis Report:\n\n1. Column Names and Data Types:\n{', '.join(col_info)}\n\n2. Number of Rows:\n{n_rows}\n\n3. First 5 Rows:\n{'\\n'.join(first5_lines)}\n"""

print(report)
