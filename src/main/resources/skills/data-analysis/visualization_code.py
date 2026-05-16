import pandas as pd
import matplotlib.pyplot as plt
import seaborn as sns
import numpy as np

# 设置中文字体支持
plt.rcParams['font.sans-serif'] = ['SimHei', 'Arial Unicode MS', 'DejaVu Sans']
plt.rcParams['axes.unicode_minus'] = False

# 读取数据
try:
    df = pd.read_csv('E:\\worksapce\\sts5.1\\legend-smartmind\\legend-smartmind\\src\\main\\resources\\skills\\data-analysis\\sample_data.csv')
except:
    # 如果路径有问题，尝试相对路径
    df = pd.read_csv('sample_data.csv')

# 创建图形布局
fig, axes = plt.subplots(2, 2, figsize=(15, 12))
fig.suptitle('员工数据分析可视化', fontsize=16, fontweight='bold')

# 1. 部门薪资分布 - 箱线图
sns.boxplot(data=df, x='department', y='salary', ax=axes[0,0])
axes[0,0].set_title('各部门薪资分布（箱线图）', fontsize=14)
axes[0,0].set_xlabel('部门')
axes[0,0].set_ylabel('薪资')

# 2. 部门薪资分布 - 小提琴图
sns.violinplot(data=df, x='department', y='salary', ax=axes[0,1])
axes[0,1].set_title('各部门薪资分布（小提琴图）', fontsize=14)
axes[0,1].set_xlabel('部门')
axes[0,1].set_ylabel('薪资')

# 3. 绩效等级分布 - 条形图
performance_counts = df['performance'].value_counts().sort_index()
axes[1,0].bar(performance_counts.index, performance_counts.values)
axes[1,0].set_title('绩效等级分布（条形图）', fontsize=14)
axes[1,0].set_xlabel('绩效等级')
axes[1,0].set_ylabel('人数')
# 在柱子上添加数值标签
for i, v in enumerate(performance_counts.values):
    axes[1,0].text(i, v + 0.1, str(v), ha='center', va='bottom')

# 4. 绩效等级分布 - 饼图
axes[1,1].pie(performance_counts.values, labels=performance_counts.index, autopct='%1.1f%%', startangle=90)
axes[1,1].set_title('绩效等级分布（饼图）', fontsize=14)

# 调整布局
plt.tight_layout()

# 显示图形
plt.show()

# 打印统计摘要
print("=== 数据分析摘要 ===")
print(f"总记录数: {len(df)}")
print(f"\n部门分布:")
print(df['department'].value_counts())
print(f"\n绩效等级分布:")
print(df['performance'].value_counts())
print(f"\n薪资统计:")
print(df['salary'].describe())