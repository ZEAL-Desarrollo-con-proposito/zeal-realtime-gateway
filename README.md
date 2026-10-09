# zeal-realtime-gateway

Real-Time Gateway de ZEAL (puerto **8083**): STOMP sobre SockJS en `/ws`, notificaciones,
presencia, chat y señalización WebRTC. Retos **RT-1** (chat < 500 ms), **RT-2** (push y difusión)
y **RT-3** (señalización 1:1).

## Ejecutar

```bash
cd ../zeal-infra-compose && docker compose up -d   # Redis
./mvnw spring-boot:run
```

Requiere `zeal-wellbeing-service` en `WELLBEING_BASE_URL` (por defecto `http://localhost:8081`) para
autorizar salas y conversaciones, y el mismo `JWT_SECRET` que los demás servicios.

## Seguridad

- **CONNECT** exige `Authorization: Bearer <jwt>`; sin token válido la conexión se rechaza.
- **SUBSCRIBE** autorizado por destino: colas `/user/queue/*` (privadas por persona), tópicos de
  cupos; `/topic/franjas` y `/topic/presencia/*` solo para EMPLEADO/PSICOLOGO;
  `/topic/organizaciones/{empresa}/*` solo para la misma empresa. Cualquier otro destino se rechaza.
- **SEND** solo a `/app/salas/{id}/senales` y `/app/chats/{id}/(mensajes|escribiendo)`.
- Las señales WebRTC se reenvían únicamente al otro participante, y solo con la sala `ACTIVA`
  (lo confirma el Wellbeing Service con el token de quien envía; caché corta de 30 s).

## Destinos

| Cliente se suscribe a | Contenido |
|---|---|
| `/topic/franjas` | `{type: CREADA\|ELIMINADA\|ACTUALIZADA, slot}` |
| `/user/queue/notificaciones` | `{type: NUEVA, notification}` · `{type: LEIDAS, ids}` |
| `/user/queue/cupos-liberados` | `{type: LIBERADO, offer}` · `{type: TOMADO, offerId}` |
| `/user/queue/salas` | `{type: SALA_ACTUALIZADA, reservationId}` · `{type: SENAL, reservationId, signal}` |
| `/user/queue/chats` | `{type: NUEVO, message}` · `{type: ESCRIBIENDO, conversationId, userId, typing}` |
| `/topic/presencia/{userId}` | `{type: PRESENCIA, status: online\|offline}` |
| `/topic/organizaciones/{empresa}/proyectos\|talleres` | `{type: PUBLICADO, project\|workshop}` |
| `/topic/proyectos/{id}/cupos`, `/topic/talleres/{id}/cupos` | `{type: CUPOS, …Id, capacity, occupied, version}` |
| `/user/queue/proyectos\|talleres\|postulaciones\|inscripciones` | avisos para admins y resultados propios |

REST: `GET /api/notificaciones`, `POST /api/notificaciones/{id}/leida`, `POST /api/notificaciones/leidas`,
`GET /api/presencia/{userId}`.

## Arquitectura

Cada canal Redis (`zeal.*`) tiene su `IntegrationEventHandler`; agregar un evento nuevo no modifica
el despachador. Las notificaciones se guardan en Redis (hash por persona, máximo 100). La presencia
y el broker son en memoria: el MVP corre con una instancia. Para escalar horizontalmente, cambiar el
simple broker por un relay STOMP (RabbitMQ) y la presencia a Redis.

## Pruebas

`./mvnw verify` (Docker requerido: Testcontainers levanta Redis). `RealtimeGatewayIT` usa un cliente
SockJS/STOMP real.
