import pandas as pd

# Load CSV
file_path = 'E:/worksapce/sts5.1/legend-smartmind/legend-smartmind/src/main/resources/skills/data-analysis/sample_data.csv'
df = pd.read_csv(file_path)

# 1. Columns & dtypes
columns_info = []
for col in df.columns:
    dtype = str(df[col].dtype)
    columns_info.append('{} ({})'.format(col, dtype))
columns_line = ', '.join(columns_info)

# 2. Row count
n_rows = len(df)

# 3. First 5 rows
first5 = df.head(5).to_string(index=False)

# Generate report
report = '''CSV Analysis Report:\n\n1. Column Names and Data Types:\n{}\n\n2. Number of Rows:\n{}\n\n3. First 5 Rows:\n{}\n'''.format(columns_line, n_rows, first5)

print(report)
