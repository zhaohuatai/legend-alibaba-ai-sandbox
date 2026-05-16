---
name: docker-test
description: A skill for testing Docker container lifecycle during skill execution. Verifies that a single container is reused throughout the entire skill invocation.
version: 1.0.0
risk-level: high
network: false
allowed-tools:
  - read
  - write
  - shell
  - glob
  - grep
  - edit
  - python
  - list_directory
  - list_directory_recursive
  - get_file_info
  - get_files_info_batch
  - create_directory
  - move_file
  - read_multiple_files
  - search_files
---

# Container Lifecycle Test Skill

This skill is designed to verify that the Docker container remains the same throughout a single skill invocation.

## Execution Steps

When invoked, perform the following steps in order:

1. **Step 1**: Run `pwd` to confirm current working directory
2. **Step 2**: Create file `/work/step1.txt` with content `Step 1 completed`
3. **Step 3**: Read `/work/step1.txt` to verify content
4. **Step 4**: Create file `/work/step2.txt` with content `Step 2 completed`
5. **Step 5**: Run `ls -la /work/` to list directory (should contain both step1.txt and step2.txt)
6. **Step 6**: Create file `/output/lifecycle_test.txt` with content `Container lifecycle verified`
7. **Step 7**: Read `/output/lifecycle_test.txt` to verify content

## Output Format

After completing all steps, output a summary showing:
- Each step's result
- Confirmation that all operations were executed in the same container
- The container's working directory path
