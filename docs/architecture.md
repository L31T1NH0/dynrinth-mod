# Architecture

## Objetivo

Manter uma base unica para resolucao, download, validacao e rastreamento, com adapters pequenos por loader e por era de API.

## Layout Atual

```text
common/          core compartilhado
fabric/          modulo Fabric atual (1.21.11)
paper/           modulo Bukkit/Paper portatil (baseline 1.16.5)
docs/            documentacao e releases
```

## Decisoes

1. `common/` fica compatível com Java 8.
2. Cada loader/era ganha modulo proprio, em vez de um jar tentar cobrir tudo.
3. Paper usa API Bukkit/Spigot antiga para maximizar portabilidade em Paper moderno.
4. Fabric moderno pode continuar em Java 21 e APIs atuais sem contaminar o core.

## Proxima Expansao Recomendada

1. `forge-1122`
2. `forge-1165`
3. `fabric-1152`
4. `fabric-1165`
5. `fabric-1201`

## Regra de Compatibilidade

- Versoes antigas: compatibilidade via modulo dedicado.
- Versoes modernas: compatibilidade via modulo por baseline forte.
- Patches intermediarios so entram quando houver teste real ou demanda clara.
