# ZEAL Realtime Gateway

Gateway Spring Boot con autenticación JWT centralizada para los servicios ZEAL.

## Login local

`POST /auth/login` recibe `username` y `password`, y devuelve un JWT firmado y el rol:

```json
{
	"username": "admin",
	"password": "Admin123!"
}
```

La respuesta contiene `token` y `role`. Cuentas semilla de desarrollo:

| Rol | Usuario | Contraseña por defecto |
| --- | --- | --- |
| ADMIN | `admin` | `Admin123!` |
| PSICOLOGO | `psicologo1`, `psicologo2` | `Psicologo123!` |
| EMPLEADO | `empleado1`, `empleado2`, `empleado3` | `Empleado123!` |

Las contraseñas se pueden reemplazar con `ZEAL_ADMIN_PASSWORD`, `ZEAL_PSICOLOGO1_PASSWORD`, `ZEAL_PSICOLOGO2_PASSWORD`, `ZEAL_EMPLEADO1_PASSWORD`, `ZEAL_EMPLEADO2_PASSWORD` y `ZEAL_EMPLEADO3_PASSWORD`. Estas cuentas son solo semilla en memoria y no deben usarse en producción.

## Autorización distribuida

Envía el token con `Authorization: Bearer <token>`. El gateway protege `/api/admin/**`, `/api/psicologo/**` y `/api/empleado/**` para los roles respectivos. El JWT HS256 incluye el username como `sub`, el rol en `role` y una expiración configurable con `ZEAL_JWT_EXPIRATION_MS`.

`zeal-wellbeing-service` y `zeal-marketplace-service` deben validar la firma y expiración del JWT con el mismo secreto Base64 configurado en `ZEAL_JWT_SECRET` y mapear `role` a `ROLE_ADMIN`, `ROLE_PSICOLOGO` o `ROLE_EMPLEADO`. Configura un secreto aleatorio de al menos 256 bits compartido por variable de entorno; el valor por defecto del repositorio es únicamente para desarrollo local.

## Pruebas

Ejecuta `mvnw.cmd test` en Windows o `./mvnw test` en Unix.