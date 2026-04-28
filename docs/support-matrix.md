# Support Matrix

## Baselines Selecionados

| Loader | Versoes alvo |
| --- | --- |
| Forge | `1.7.10`, `1.8.9`, `1.12.2`, `1.16.5`, `1.18.2`, `1.20.1` |
| Fabric | `1.15.2`, `1.16.5`, `1.18.2`, `1.20.1`, `1.21.1`, `1.21.11` |
| Paper | `1.16.5`, `1.18.2`, `1.20.1`, `1.21.1` |

## Implementado Neste Repositorio Hoje

| Modulo | Status | Observacao |
| --- | --- | --- |
| `common` | pronto | Core Java 8 para compartilhamento entre linhas antigas e novas |
| `fabric` | pronto | Build atual para `1.21.11` |
| `paper` | pronto | Baseline Bukkit/Spigot `1.16.5+`, voltado a Paper |
| `forge-*` | planejado | Ainda nao implementado |
| `fabric-*` legados | planejado | Ainda nao implementado |

## Politica

1. Suporte oficial fica nos baselines acima.
2. Patches proximos podem ser aceitos quando o mesmo jar realmente funcionar.
3. Se houver quebra de loader/API, cria-se um modulo novo em vez de hacks condicionais no mesmo modulo.
