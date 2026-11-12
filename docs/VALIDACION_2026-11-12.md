# Validación complementaria del 12 de noviembre de 2026

Se usó Firebase real del proyecto original. Android A: teléfono CPH2767 con Android 17 y cuenta administradora. Android B: emulador Medium Phone API 37 con una cuenta nueva de prueba y rol de simulador.

## Resultados comprobados

- Registro sin permisos de administración; validación de correo vacío y rechazo de contraseña incorrecta.
- Rol de simulador habilitado desde A y retirado al terminar; en B se deshabilita la validación después de retirarlo.
- Alta de Valentina demo, UID 04A39C21. El evento permitido 139dc285a0214ad8918049c07f510056 se contrastó en el simulador B y en el detalle del historial de A.
- Serie de 10 intentos: 10 confirmaciones, 10 ID distintos y 10 documentos comprobados directamente en Firebase. Mediana 140 ms; máximo 467 ms.
- Ese tiempo es el indicado por SimulatorActivity hasta confirmar la transacción; no es la latencia hasta aparecer en A ni una medición del alcance inalámbrico.
- Wi-Fi y datos de B deshabilitados 30 segundos: botón de validación deshabilitado y ninguna nueva validación permitida. Recuperada la conexión, se confirmó un nuevo intento.
- Se revocó 04A39C21 desde A y B rechazó esa misma credencial. FFFFFFFF se rechazó como desconocida. Alta y revocación visibles en Auditoría.
- Cierre de sesión en B y rechazo de una contraseña incorrecta. Se conserva abierta la sesión administradora de A.
- Corregido un mensaje de estado: Esperando autorización ahora pasa a Listo para validar al confirmar el rol. La APK se recompiló e instaló en ambos clientes.
- assembleDebug, testDebugUnitTest y lintDebug aprobados. Se mantienen avisos no bloqueantes de lint. AccessPolicyTest: cinco pruebas, cero fallos.

## Evidencias

Las capturas de docs/capturas corresponden a la APK real. Los JSON de docs/evidencias contienen la serie, documentos verificados, corte de red, revocación y autenticación. No se publican contraseñas ni tokens de sesión.

## Límites y pendientes

La prueba usa un teléfono y un emulador; no demuestra dos teléfonos físicos ni una presentación en clase. No se midieron distancia al router, consumo de batería ni latencia hasta el otro cliente. La recuperación por correo y la validación con la docente siguen pendientes. No se accionó una cerradura ni se conectó un Arduino.

Al terminar, tanto la credencial ficticia como el rol de la cuenta de prueba quedaron revocados. Los datos anteriores se conservaron. La evaluación no se ha enviado a AAI.
