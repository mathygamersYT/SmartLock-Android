# Smart Lock para Android

Aplicación de la unidad 2 con Kotlin y vistas XML. Conserva el applicationId com.example.iot,
el archivo google-services.json y el proyecto Firebase existente iot-aki-d467d.

## Funciones

- Firebase Authentication: ingreso, registro, recuperación de contraseña y cierre de sesión.
- Administrador: alta de UID RFID, búsqueda, detalle, habilitación y revocación.
- Tarjeta y auditoría se guardan juntas mediante transacciones.
- Historial compartido con filtros de resultado y origen; detalle de cada evento.
- Segundo Android: simulación de lectura, rechazo de credenciales desconocidas o revocadas.
- Verificación de la decisión tanto en la transacción de Android como en las reglas de Firestore.
- Identificador estable para reintentos; eventos simulados inmutables.
- Un administrador puede habilitar o revocar una cuenta de simulador por su UID.
- Estados vacíos, errores, carga, caché y falta de autorización.
- Diseño adaptable a tema claro y oscuro.

## Preparación de Firebase

1. Usar el proyecto ya conectado. No crear otra aplicación ni otro proyecto Firebase.
2. Authentication debe tener activado Correo/contraseña.
3. Publicar firestore.rules. No incluir contraseñas ni claves de cuentas de servicio en el APK.
4. El dueño del proyecto crea administradores/UID_AUTH con activo: true (booleano).
   Una cuenta nueva no se convierte automáticamente en administradora.
5. Instalar el mismo APK en ambos Android.
6. En A ingresar con administrador. En B crear o ingresar con otra cuenta y copiar su UID.
7. En A, Inicio > Vincular o revocar segundo Android, pegar el UID y habilitarlo.
8. En B, Abrir punto de acceso. Su rol permite consultar un UID concreto y registrar
   su decisión; no enumerar tarjetas, cambiar credenciales ni conceder roles.

Las colecciones antiguas personas y usuarios se conservan con el acceso temporal de
laboratorio que ya existía, hasta el 2 de diciembre de 2026. No contienen datos de
Smart Lock. Revisar su cierre por separado al terminar esos ejercicios. Las reglas
protegen las colecciones nuevas independientemente de esas dos excepciones.

## Demostración de dos teléfonos

1. En A agregar Valentina, UID 04:A3:9C:21. Se normaliza a 04A39C21.
2. En B usar ese UID y Simular lectura. Debe mostrar Acceso permitido.
3. En A abrir Historial > Simulados y verificar el identificador y la fecha del evento.
4. En A revocar la credencial y esperar la confirmación de Firebase.
5. En B volver a simular el mismo UID: debe indicar rechazo por revocación.
6. Usar un UID no registrado: rechazo por credencial desconocida.
7. Repetir sin Internet. No se debe mostrar un permiso nuevo ni un éxito sin confirmar.
8. Restablecer la red y reintentar. El mismo identificador evita duplicar un envío incierto.
9. Verificar la auditoría de alta y revocación en A.
10. En A revocar el rol del segundo Android y comprobar que deja de poder generar intentos.

La comunicación es Android A ↔ Wi-Fi/Internet ↔ Firebase ↔ Android B. No es Wi-Fi Direct.
Debe validarse con la docente si esta arquitectura cumple la modalidad de interconexión.
El simulador no abre una cerradura ni informa que una puerta física cambió de estado.

## Datos

- administradores/{uid}: activo. Aprovisionamiento por dueño del proyecto.
- simuladores/{uid}: activo, habilitadoPor, fecha. Solo administradores cambian el rol.
- tarjetas/{uidRFID}: nombre, habilitada, fechaCreacion, actualizadaEn, actualizadaPor,
  ultimaOperacion. UID de 4, 7 o 10 bytes, hexadecimal mayúsculo sin separadores.
- auditoria/{id}: actorUid, tarjetaUid, accion, fecha. Historial de cambios inmutable.
- eventosSimulados/{id}: actorUid, uidRFID, resultado, motivo, origen, dispositivoId, fecha.
  origen=SIMULACION. Motivos ACTIVA, REVOCADA o DESCONOCIDA.
- eventos/{id}: datos físicos, solo escribe un backend confiable.
- dispositivos/puerta-principal: puerta, ultimaConexion, ultimaSincronizacionTarjetas.
  Solo un backend confiable informa telemetría física; no se simula un Arduino conectado.

El historial carga hasta 50 eventos por origen; la lista de credenciales hasta 100.
Las fechas se presentan en la zona horaria del teléfono. El UID se oculta parcialmente
en listas e historial y el administrador puede consultarlo en el detalle de la tarjeta.

## Compilar y comprobar

Requisitos: JDK de Android Studio y SDK configurado en local.properties.

    ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug

APK: app/build/outputs/apk/debug/app-debug.apk. Compilación de depuración, no firma de distribución.

Pruebas de reglas aisladas (no usan datos reales):

    cd tests/firebase
    npm install
    npm test

Necesitan Node y Java. Usan demo-smartlock y puertos 8085 y 9095.
Las pruebas cubren acceso anónimo, roles, inmutabilidad, decisiones falsificadas,
auditoría atómica, revocación e idempotencia. No sustituyen la demostración con dos
Android y la medición de latencia/distancia/estabilidad en el aula.

## Decisiones técnicas

Firebase Authentication gestiona contraseñas; Firestore comparte eventos y credenciales
entre equipos. SQLite sería adecuado para datos locales, pero requeriría un mecanismo
adicional para sincronizar dos teléfonos. API con MySQL permite centralizar la lógica,
pero añade despliegue y mantenimiento de un backend. Firestore permite concentrar este
prototipo académico en Android y en reglas verificables del servicio existente.

Wi-Fi con nube permite operar en redes distintas con Internet. El alcance local depende
del punto de acceso; no se declara una distancia sin medirla. Las suscripciones se
cancelan cuando las pantallas dejan de estar activas para reducir consumo y lecturas.
La caída de Internet impide nuevas validaciones. Bluetooth físico requiere otro protocolo,
firmware, permisos y pruebas; no forma parte de esta demostración.

## Entrega académica

Incluir proyecto, instrucciones y resultados de pruebas en GitHub; subir al AAI un TXT
con el enlace según la guía. No se ha creado ni publicado un repositorio remoto.
No versionar local.properties, node_modules, salidas de build, contraseñas o credenciales
administrativas. google-services.json es la configuración del cliente existente.

## Evidencias de la corrección

Código y entrega: https://github.com/mathygamersYT/SmartLock-Android

Consulta [la validación del 12 de noviembre](docs/VALIDACION_2026-11-12.md) y las [capturas reales](docs/capturas). La APK de depuración está en entrega/SmartLock.apk. La prueba complementaria usa un teléfono y un emulador; quedan pendientes dos teléfonos físicos, distancia, consumo y revisión docente.


## Release de la corrección

[Descargar APK e informe de la Release v1.0.0](https://github.com/mathygamersYT/SmartLock-Android/releases/tag/v1.0.0). Consulta también [la cobertura de las 44 capturas](docs/COBERTURA_CAPTURAS.md), que identifica las diferencias respecto del mockup.
