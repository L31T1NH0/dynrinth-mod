# Dynrinth

Instalador de listas do Dynrinth para Minecraft, com base compartilhada preparada para loaders e versões antigas sem perder o suporte moderno.

## Estado Atual

- `common/`: core compartilhado compatível com Java 8
- `fabric/`: build atual para Fabric `1.21.11`
- `paper/`: build portátil em API Bukkit/Spigot `1.16.5+`, voltado a servidores Paper
- `docs/`: arquitetura, matriz de suporte e releases

## Comandos

```text
/dynrinth <code>
/dynrinth <code> force
/dynrinth remove <code>
```

## Comportamento

- Aceita codigos com 8 ou 10 caracteres.
- Em codigos com 10 caracteres, valida a versao do Minecraft antes do download.
- `force` ignora o mismatch de versao.
- Resolve as versoes pela API do Modrinth.
- Baixa arquivos em paralelo e valida SHA-1 quando disponivel.
- Registra apenas arquivos realmente baixados pelo Dynrinth em `dynrinth-packs.json`.
- Bloqueia nomes de arquivo inseguros para nao escapar de `mods/` ou `plugins/`.

## Estrutura

```text
common/          core compartilhado
fabric/          modulo Fabric atual
paper/           modulo Bukkit/Paper portatil
docs/            documentacao tecnica
docs/releases/   notas de release
```

## Requisitos

- Java 21+ para o build completo atual
- Gradle Wrapper

## Build

```bash
./gradlew build
```

Build por modulo:

```bash
./gradlew :fabric:build
./gradlew :paper:build
```

Artefatos em `fabric/build/libs/` e `paper/build/libs/`.

## Matriz Alvo

- Forge: `1.7.10`, `1.8.9`, `1.12.2`, `1.16.5`, `1.18.2`, `1.20.1`
- Fabric: `1.15.2`, `1.16.5`, `1.18.2`, `1.20.1`, `1.21.1`, `1.21.11`
- Paper: `1.16.5`, `1.18.2`, `1.20.1`, `1.21.1`

Documentacao relacionada:

- `docs/architecture.md`
- `docs/support-matrix.md`
- `docs/releases/v2.19.0.md`

## Observacoes

- API Dynrinth: `https://dynrinth.vercel.app`
- API Modrinth: `https://api.modrinth.com/v2`
- Os jars de Fabric e Paper embutem a logica comum do projeto.
- Os modulos legados de Forge/Fabric ainda nao foram adicionados; o repositório foi preparado para essa expansao.

## Licenca

MIT
