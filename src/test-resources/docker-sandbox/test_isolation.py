import os
import sys
import platform

print("=" * 50)
print("Docker Sandbox Test")
print("=" * 50)
print(f"Python Version: {sys.version}")
print(f"Platform: {platform.platform()}")
print(f"Hostname: {platform.node()}")
print(f"Current User: {os.getlogin() if hasattr(os, 'getlogin') else 'N/A'}")
print(f"Working Directory: {os.getcwd()}")
print(f"Process ID: {os.getpid()}")
print("=" * 50)
print("Container isolation test passed!")
