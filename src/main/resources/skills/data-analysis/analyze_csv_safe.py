import csv
import sys
from collections import defaultdict

file_path = 'E:\\worksapce\\sts5.1\\legend-smartmind\\legend-smartmind\\src\\main\\resources\\skills\\data-analysis\\sample_data.csv'

# Read with UTF-8 (and handle BOM)
rows = []
try:
    with open(file_path, 'r', encoding='utf-8-sig') as f:
        reader = csv.reader(f)
        for row in reader:
            if row:  # skip empty lines
                rows.append(row)
except UnicodeDecodeError:
    # Fallback to utf-8 ignoring errors
    with open(file_path, 'r', encoding='utf-8', errors='replace') as f:
        reader = csv.reader(f)
        for row in reader:
            if row:
                rows.append(row)

if not rows:
    print('Error: Empty or unreadable file.')
    sys.exit(1)

headers = rows[0]
records = rows[1:]

# 1. Columns and inferred types
print('1. Columns and Inferred Data Types:')
types = []
for i, col in enumerate(headers):
    col_vals = [r[i] for r in records if i < len(r) and len(r) > i]
    # Try numeric
    is_numeric = True
    for v in col_vals:
        v_clean = v.strip()
        if not v_clean:
            continue
        if not v_clean.replace('.', '').replace('-', '').isdigit():
            is_numeric = False
            break
    dtype = 'numeric' if is_numeric else 'string'
    types.append((col, dtype))
    print(f'   {col:<12} -> {dtype}')
print()

# 2. Row count
print('2. Data Row Count:')
print(len(records))
print()

# 3. First 5 rows
print('3. First 5 Rows:')
for i, r in enumerate(records[:5]):
    # Replace non-printable chars for display
    clean_r = [cell.encode('utf-8', errors='replace').decode('utf-8', errors='replace') for cell in r]
    print(f'   {i+1}: {clean_r}')
print()

# 4. Write report (UTF-8)
report_path = 'E:\\worksapce\\sts5.1\\legend-smartmind\\legend-smartmind\\src\\main\\resources\\skills\\data-analysis\\analysis_report.txt'
try:
    with open(report_path, 'w', encoding='utf-8') as f:
        f.write('CSV Analysis Report (Lightweight, UTF-8 Safe)\n')
        f.write('=' * 50 + '\n\n')
        
        f.write('1. Columns and Inferred Data Types:\n')
        for col, dtype in types:
            f.write(f'   {col:<12} -> {dtype}\n')
        f.write('\n')
        
        f.write('2. Data Row Count:\n')
        f.write(str(len(records)) + '\n\n')
        
        f.write('3. First 5 Rows:\n')
        for i, r in enumerate(records[:5]):
            f.write(f'   {i+1}: {r}\n')
        f.write('\n')
        
        f.write('4. Notes:\n')
        f.write('- Encoding: UTF-8 (with BOM handling)\n')
        f.write('- Type inference: numeric if all non-empty values look like integers/floats\n')
        f.write('- Non-UTF-8 characters in data are preserved in report via UTF-8 encoding\n')
    
    print(f'Done: Report saved to {report_path}')
except Exception as e:
    print(f'Error writing report: {e}')