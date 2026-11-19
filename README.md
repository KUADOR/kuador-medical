# Medical Kuador

Medical Kuador

Proyecto **MEDK** del tablero. Los issues, las etapas y las aprobaciones viven en el tablero; este repo guarda el código y los documentos.

## Ramas

- `develop`: rama de trabajo. Cada issue va en su rama (`feature/MEDK-<número>-<nombre>`) y entra por Pull Request con el CI en verde.
- `main`: lo publicado. Solo entra desde `develop` por Pull Request aprobado por una persona.

## Agentes

Los agentes trabajan desde el cliente de IA de cada persona conectado al tablero. Ver `AGENTS.md`.

## Piezas

| Pieza | Stack | Dónde | Compilar | Probar | Levantar |
|---|---|---|---|---|---|
| api | Java 25 · Spring Boot 4 · PostgreSQL | `api/` | `./mvnw -B package` | `./mvnw -B verify` | `./mvnw spring-boot:run` |
| web | Angular · TypeScript | `web/` | `npm run build` | `npm test -- --watch=false` | `npm start` |
| movil | Flutter | `movil/` | — | — | — |

## Documentos

- [Reglas para agentes](AGENTS.md)
- [Arquitectura](docs/architecture.md)
- [Constitución](.specify/memory/constitution.md)
- [Despliegue](docs/deployment.md)
- [Glosario](docs/glossary.md)
- [Producto](PRODUCT.md)
