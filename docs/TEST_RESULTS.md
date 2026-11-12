# Resultados de validación

Fecha: 9 de noviembre de 2026.
Proyecto Firebase: iot-aki-d467d, configuración original conservada.
Teléfono de integración: CPH2767, Android 17.

## Comprobaciones ejecutadas

- assembleDebug: APK generado.
- testDebugUnitTest: normalización de UID, formato inválido, ocultamiento, vencimiento
  de heartbeat y decisiones activa/revocada/desconocida.
- lintDebug: sin errores bloqueantes. Hay avisos sobre textos sin extraer a recursos,
  dependencias con versiones más recientes y otros avisos de presentación.
- Reglas de Firestore: 9 pruebas automáticas aprobadas en proyecto demo-smartlock aislado.
- Firebase real desde el teléfono: una prueba de integración aprobada que verificó alta,
  rechazo de alta duplicada, permiso con tarjeta activa, timestamp del servidor,
  recuperación del mismo evento sin cambiar su fecha, revocación, auditoría del actor,
  rechazo de tarjeta revocada y rechazo de tarjeta desconocida.
- Se publicó el archivo firestore.rules en la consola de Firebase con autorización.
- Se habilitó la cuenta que ya tenía sesión en el teléfono como administradora.

La prueba real deja una credencial de nombre Prueba de integración Android, revocada,
sus registros de auditoría y tres eventos simulados. No borra datos ni tarjetas anteriores.

## Correspondencia con el plan del informe

| Prueba | Evidencia disponible | Límite |
|---|---|---|
| P01 Autenticación | Sesión existente de Firebase funciona en el teléfono | Falta repetir ingreso inválido y recuperación con el estudiante |
| P02 Permisos | Simulador no administra tarjetas ni roles, verificado por reglas | Prueba realizada con emulador de Firebase |
| P03 Alta | Alta y duplicado comprobados contra Firebase real | Credencial ficticia de integración |
| P04 Permitido | Decisión permitida y evento confirmado contra Firebase real | Falta repetir la demostración con dos Android a la vez |
| P05 Rechazado | UID desconocido rechazado contra Firebase real | Simulación, sin lector RC522 |
| P06 Revocación | Revocación y nuevo intento rechazado contra Firebase real | Simulación, sin accionamiento físico |
| P07 Desconexión | La app no concede acceso ante fallo de validación | Falta ensayo manual con corte real de red |
| P08 Reintento | Mismo ID recupera el evento original, sin duplicado | Probado en Firebase real y emulador |
| P09 Sesión cerrada | Acceso anónimo denegado en reglas | Falta recorrido manual de cierre e ingreso en ambos teléfonos |
| P10 Integridad | Rechazadas suplantación de actor, cambios de eventos y autoasignación de roles | Pruebas automáticas en emulador |

## Repetir la prueba real

Requiere cuenta administradora iniciada y autorización para añadir registros ficticios:

    ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
    adb -s SERIAL install -r app/build/outputs/apk/debug/app-debug.apk
    adb -s SERIAL install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
    adb -s SERIAL shell am instrument -w -e class com.example.iot.LiveFirebaseTest -e smartLockLive true com.example.iot.test/androidx.test.runner.AndroidJUnitRunner

Se usa instrumentación directa para mantener la app instalada después de la prueba.
connectedDebugAndroidTest puede retirar la app al finalizar según la configuración del
sistema de pruebas de Gradle. No desinstalar ni borrar datos del teléfono para depurar.

## Pendientes para la demostración académica

- Segundo Android con cuenta propia y rol simulador habilitado desde A.
- Medir latencia y estabilidad, con condiciones de red y distancia documentadas.
- Validar con la docente la comunicación mediante Firebase.
- Ejecutar pruebas de pérdida de red y cierre de sesión en ambos dispositivos.
- Publicar el código en GitHub y entregar el TXT del enlace en AAI.
- Hardware RC522, firmware, relé, cerradura y Bluetooth quedan fuera del simulador.

## Actualización del 12 de noviembre

Consulta [la validación complementaria](VALIDACION_2026-11-12.md): teléfono y emulador, serie de diez transacciones, corte de red de 30 segundos, autenticación y revocación de roles.
