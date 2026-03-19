# MCP Embeddings Tools (DEPRECATO)

Predecessore di `mcp-vector-tools`. Usare `mcp-vector-tools` per nuove installazioni (supporto multi-provider: Ollama, ONNX, OpenAI).

## Build

```bash
/opt/maven/bin/mvn clean compile
/opt/maven/bin/mvn clean install -Dgpg.skip=true
/opt/maven/bin/mvn clean deploy
```

Java 17+. Maven: `/opt/maven/bin/mvn`.

Migrazione: vedi tabella in [../mcp-vector-tools/CLAUDE.md](../mcp-vector-tools/CLAUDE.md).
