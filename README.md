# Post-contenido — Unidad 7: Patrones Arquitectónicos I

## Descripción

Repositorio del post-contenido de la Unidad 7 de Patrones de Diseño de Software — UDES.  
Un único proyecto Spring Boot (`multas-biblioteca-api`) para la gestión de multas de biblioteca universitaria, con dos partes:

- **Parte 1:** API REST con arquitectura en capas (Model, Repository, Service, Controller) sobre H2.
- **Parte 2:** Pago en línea de multas con dos pasarelas intercambiables mediante un puerto de dominio y adaptadores (Opción C).

## Estructura del repositorio

```
ascanio-post1-u7/
├── .gitignore
├── README.md
└── multas-biblioteca-api/
    ├── pom.xml
    └── src/main/java/com/example/multas/
        ├── MultasApplication.java
        ├── controller/              ← Capa Presentación (@RestController)
        │   ├── MultaController.java
        │   ├── GenerarMultaRequest.java
        │   └── GlobalExceptionHandler.java
        ├── service/                 ← Capa Aplicación (@Service)
        │   └── MultaService.java
        ├── model/                   ← Capa Dominio (entidades y excepciones)
        │   ├── Multa.java
        │   ├── EstadoMulta.java
        │   ├── MultaNotFoundException.java
        │   ├── LimiteMultasPendientesException.java
        │   └── MultaYaPagadaException.java
        ├── repository/              ← Capa Infraestructura JPA
        │   └── MultaRepository.java
        ├── domain/                  ← Puerto de dominio (Java puro, sin Spring)
        │   ├── port/
        │   │   └── PasarelaPagoPort.java
        │   ├── ResultadoPago.java
        │   └── PagoRechazadoException.java
        └── infrastructure/          ← Adaptadores de salida (conocen Spring y HTTP)
            ├── pago/
            │   ├── PagosUdesAdapter.java
            │   └── WompiAdapter.java
            └── config/
                └── RestTemplateConfig.java
```

## Parte 1 — Arquitectura en Capas

### Descripción

API REST que permite registrar multas por devolución tardía de libros, calcular su monto automáticamente y marcarlas como pagadas. Las cuatro capas tienen responsabilidades claras y sin dependencias cruzadas:

| Capa | Paquete | Responsabilidad |
|---|---|---|
| Presentación | `controller/` | Exponer endpoints REST, validar entrada HTTP, traducir excepciones a códigos de estado |
| Aplicación | `service/` | Orquestar casos de uso, aplicar reglas de negocio que requieren datos externos |
| Dominio | `model/` | Entidades JPA, enumeraciones y excepciones de negocio |
| Infraestructura | `repository/` | Acceso a la base de datos con Spring Data JPA |

### Endpoints

| Método | Ruta | Descripción | Código exitoso |
|---|---|---|---|
| GET | `/api/multas` | Lista todas las multas | 200 |
| GET | `/api/multas/{id}` | Busca multa por ID | 200 |
| GET | `/api/multas/estudiante/{estudianteId}` | Lista multas de un estudiante | 200 |
| POST | `/api/multas` | Genera nueva multa | 201 |
| PATCH | `/api/multas/{id}/pagar` | Pago en ventanilla | 200 |
| POST | `/api/multas/{id}/pagar-en-linea` | Pago con pasarela activa | 200 |

### Errores manejados

| Situación | Código HTTP |
|---|---|
| Multa no encontrada | 404 Not Found |
| Estudiante con 3+ multas pendientes | 409 Conflict |
| Multa ya pagada | 409 Conflict |
| Pago rechazado por la pasarela | 402 Payment Required |
| Datos de entrada inválidos | 400 Bad Request |

## Parte 2 — Pago en Línea con Dos Pasarelas

### Decisión adoptada: Opción C — Puerto de dominio con dos adaptadores

Se implementó la **Opción C**: un puerto de salida `PasarelaPagoPort` en `domain/port/`, con dos adaptadores concretos en `infrastructure/pago/`:

- `PagosUdesAdapter` — activo cuando `app.pagos.proveedor=pagosudes` (por defecto)
- `WompiAdapter` — activo cuando `app.pagos.proveedor=wompi`

La pasarela se selecciona en tiempo de arranque con `@ConditionalOnProperty`. **Cambiar de pasarela no requiere modificar `MultaController` ni `MultaService`** — solo editar `application.properties` y reiniciar.

### Cómo cambiar de pasarela

```properties
# application.properties
app.pagos.proveedor=wompi   # cambiar a wompi activa WompiAdapter
```

## Cómo ejecutar

```bash
cd multas-biblioteca-api
mvn clean package
mvn spring-boot:run
```

La API queda disponible en `http://localhost:8080`.  
La consola H2 está en `http://localhost:8080/h2-console`:
- JDBC URL: `jdbc:h2:mem:multas_biblioteca_db`
- Usuario: `sa`
- Contraseña: *(vacía)*

## Ejemplos de uso con curl

```bash
# 1. Generar multa (201 Created)
curl -s -X POST http://localhost:8080/api/multas \
  -H "Content-Type: application/json" \
  -d '{"estudianteId":"EST001","concepto":"Devolucion tardia - Fisica Universitaria","diasAtraso":5}'

# 2. Listar todas las multas (200 OK)
curl -s http://localhost:8080/api/multas

# 3. Buscar multa por ID (200 OK o 404)
curl -s http://localhost:8080/api/multas/1

# 4. Listar multas de un estudiante
curl -s http://localhost:8080/api/multas/estudiante/EST001

# 5. Pagar en ventanilla (200 OK)
curl -s -X PATCH http://localhost:8080/api/multas/1/pagar

# 6. Intentar cuarta multa pendiente (409 Conflict)
curl -s -X POST http://localhost:8080/api/multas \
  -H "Content-Type: application/json" \
  -d '{"estudianteId":"EST001","concepto":"Cuarta multa","diasAtraso":2}'

# 7. Datos inválidos — sin estudianteId (400 Bad Request)
curl -s -X POST http://localhost:8080/api/multas \
  -H "Content-Type: application/json" \
  -d '{"concepto":"Sin estudiante","diasAtraso":3}'

# 8. Pago en linea (con pasarela simulada apagada → 402 Payment Required)
curl -s -X POST http://localhost:8080/api/multas/2/pagar-en-linea
```

## Decisiones de diseño

### Punto de decisión 1 — Cálculo del monto: ¿entidad o Service?

`Multa.calcularMonto(int diasAtraso)` vive como método estático de la entidad `Multa` porque la regla (días × \$500, tope \$15 000) depende **únicamente** del número de días recibido como parámetro, sin necesitar ningún dato externo (ningún Repository, ningún Service, ninguna configuración).

La alternativa habría sido definirla como método privado de `MultaService`. Eso funciona técnicamente, pero genera un **modelo anémico**: `Multa` quedaría reducida a un contenedor de datos sin comportamiento propio, y la fórmula quedaría acoplada al Service. Si en el futuro se necesitara mostrar un presupuesto antes de confirmar la multa, habría que instanciar el Service completo —con sus dependencias— solo para ejecutar una multiplicación.

**Criterio aplicado:** si la regla no necesita ningún colaborador externo, es candidata a vivir en el propio objeto de dominio.

### Punto de decisión 2 — Conteo de multas pendientes: ¿consulta agregada o filtrado en memoria?

La regla "no más de 3 multas pendientes por estudiante" se resuelve con `MultaRepository.countByEstudianteIdAndEstado`, que Spring Data JPA traduce a un `COUNT(*)` en SQL. La alternativa habría sido `findByEstudianteId` seguido de filtrado con streams.

La razón no es solo de rendimiento con datos de prueba: es de **diseño**. Con cientos de multas históricas por estudiante, la alternativa en memoria traería todos los registros del estudiante a la JVM para descartar la mayoría. La decisión de negocio la toma `MultaService`, pero el dato que esa decisión necesita se resuelve donde es eficiente: en el motor de base de datos.

### Punto de decisión 3 — Selección del adaptador activo: `@ConditionalOnProperty` vs. `Map` de proveedores

Se usó `@ConditionalOnProperty` para que solo uno de los dos beans exista en el contexto de Spring según el valor de `app.pagos.proveedor`. Esto permite que `MultaService` inyecte un único `PasarelaPagoPort` por constructor, sin `@Qualifier` ni lógica condicional propia.

La alternativa válida habría sido inyectar un `Map<String, PasarelaPagoPort>` en `MultaService` y elegir el adaptador en tiempo de ejecución. Eso da más flexibilidad (cambiar de pasarela sin reiniciar), pero se descartó porque el requisito real — una pasarela fija por sede durante el piloto — no necesita ese grado de dinamismo, y habría significado que `MultaService` conociera las claves de configuración (`"pagosudes"`, `"wompi"`) de cada proveedor, filtrando hacia el dominio detalles de infraestructura.

### Punto de decisión 4 — Diseño del puerto y del tipo de resultado

`ResultadoPago` usa el campo `referenciaExterna` en vez de `idTransaccion` (nombre de PagosUDES) o `reference` (nombre de Wompi). Este nombre neutral es deliberado: si el puerto devolviera el DTO propio de PagosUDES, `WompiAdapter` tendría que mapear `reference` → `idTransaccion`, introduciendo en el dominio un nombre que no le pertenece. Con `referenciaExterna`, agregar una tercera pasarela o eliminar una no requiere tocar `MultaService` ni `MultaController`.

### Trade-off considerado — Parte 2

La **Opción B** (interfaz `PasarelaPago` dentro de `service/`, implementaciones seleccionadas por `@ConditionalOnProperty`) habría resuelto la intercambiabilidad con menos archivos nuevos y sin introducir el paquete `domain/`. Se descartó porque cada pasarela tiene un formato HTTP distinto que inevitablemente hay que traducir en algún punto: con la Opción B esa traducción quedaría dentro de implementaciones del paquete `service/`, borrando la frontera entre lógica de negocio e integración con el proveedor externo.

Con la **Opción C** (elegida), `domain/` es Java puro — sin Spring, sin RestTemplate — y compila sin ninguna dependencia de infraestructura. Ese límite estricto tiene un costo: dos paquetes nuevos (`domain/`, `infrastructure/`), más clases que en la Opción B, y una curva de aprendizaje del patrón de puertos y adaptadores. Si el piloto terminara y quedara una sola pasarela definitiva, el equipo podría eliminar el adaptador inactivo y simplificar la interfaz; el costo de revertir sería bajo porque el puerto aisló el cambio en un único punto.

## Conclusiones

La Parte 1 demostró que la arquitectura en capas no se reduce a separar clases en carpetas: lo decisivo es dónde vive cada regla de negocio. Colocar `calcularMonto` en la entidad y `countByEstudianteIdAndEstado` en el repositorio no fue arbitrario, sino el resultado de responder preguntas concretas sobre dependencias y eficiencia. La Parte 2 mostró que arquitectura en capas y hexagonal no son mutuamente excluyentes: se aplicó el puerto de dominio únicamente en la porción del sistema donde la complejidad lo justificaba — las pasarelas de pago con contratos HTTP distintos — sin migrar el resto del proyecto. La decisión más difícil no fue elegir entre A, B y C, sino reconocer en qué punto la variabilidad del requisito supera el umbral a partir del cual introducir el puerto paga su costo adicional en líneas de código y nuevos paquetes.

## Herramientas utilizadas

- Java 17, Spring Boot 3.2.5, Spring Data JPA, H2 Database, RestTemplate
- Apache Maven 3.8+, curl, Git, GitHub
