# OrganizerPC

Organizador para la PC de Cobblemon (Minecraft 1.21.1, Fabric). Se integra en la propia PC: barra encima con las cajas y panel a la derecha. **Solo cliente**: no hace falta instalar nada en el servidor.

## Instalación

1. Descarga `OrganizerPC.jar` de la [última versión](https://github.com/mc-albin/OrganizerPC/releases/latest).
2. Ponlo en la carpeta `mods` de tu Minecraft (si tenías `OrganizerPCDiosesmon.jar`, bórralo: tu configuración se conserva).
3. Necesitas Fabric Loader 0.17.2+, Fabric API, Cobblemon 1.7.3 y Java 21.

## Qué hace

- **Categorías**: dicen qué Pokémon van juntos (IVs en 31 o 0, cuántos 31, especie, grupo huevo) y en qué cajas. «Organizar PC» lo coloca todo, siempre con vista previa antes de mover.
- **Ordenar cajas**: por IVs, Pokédex, nivel, grupo huevo, **columnas por IV** (completas o con huecos) y **living dex** (cada familia en orden de Pokédex, dejando vacío el hueco de las especies que faltan).
- **Buscar y filtrar**: especie, grupo huevo, IV de cada estadística (`ps31`, `vel0`), nº de 31 (`3x31`), nº de Pokédex (`25`, `1-151`), género, shiny y tamaño. Las cajas con resultados se iluminan.
- **Marcas de IVs** en cada Pokémon, en tu equipo (pantalla, datos y debajo del nombre de tus Pokémon sacados).
- Cajas bloqueadas, cajas de captura, mover a mano, AFK, tema de colores y tamaño de la interfaz.

## Actualizaciones

Al iniciar el juego, el mod mira si hay una versión nueva en las releases de este repositorio. Si la hay, avisa al abrir la PC y en **Ajustes** sale el botón **Actualizar**. Se descarga, se comprueba su huella SHA-256 y se instala sola al cerrar el juego.

Para publicar una versión (PowerShell, en esta carpeta): `.\publicar.ps1 1.1.1 "Qué cambió"`.

## Cómo mueve los Pokémon

Usa los mismos paquetes que la PC original de Cobblemon (mover a hueco vacío, intercambiar, renombrar caja), uno a uno, y espera la confirmación del servidor en cada paso. Si la PC cambia a mitad, se detiene. No crea ni borra Pokémon.

## Compilar

Con Java 21: `gradlew build`. El jar queda en `build/libs/OrganizerPC.jar`.
