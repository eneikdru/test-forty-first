with open("docs/architecture/adr-002-coverage-gap-falsification-refusal.md", "r") as f:
    content = f.read()

# Extract the justification section which starts after "## Justification" and ends at EOF.
justification = content.split("## Justification")[1]
words = justification.split()
print(f"Number of words in justification: {len(words)}")
