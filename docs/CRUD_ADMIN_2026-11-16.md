# CRUD de administrador — 16 de noviembre de 2026

Se trabajó sobre C:/Users/moren/AndroidStudioProjects/IoT.
La copia IoT - backup para clase no se modificó.
Antes de cambiar el original se creó un ZIP con 127 archivos y se verificó
el contenido mediante SHA-256. Se excluyeron cachés y archivos de compilación.

## Funciones

- Crear: UID RFID normalizado y titular de 1 a 80 caracteres.
- Leer: búsqueda por titular/UID y detalle con UID parcialmente oculto.
- Editar: cambia el titular; el UID permanece inmutable. Una edición no habilita
  accidentalmente una tarjeta revocada de forma concurrente.
- Habilitar/revocar: conserva la identidad y el historial.
- Eliminar: baja lógica permanente, eliminada=true y habilitada=false. No borra
  la tarjeta, los intentos ni la auditoría. La casilla Mostrar credenciales
  eliminadas permite consultarlas, pero no editarlas ni restaurarlas.
- Los intentos nuevos de esa tarjeta se rechazan con motivo ELIMINADA.
- Se mantienen los roles y el flujo administrador/simulador entre dos Android.

## Organización y XML

CredentialController administra formularios, confirmaciones y estado de escritura.
CredentialAdapter usa ListAdapter y DiffUtil para reciclar filas XML.
RecordAdapter hace lo mismo para historial y auditoría.
MainActivity conserva autorización, suscripciones y navegación.
AccessRepository contiene las transacciones; AccessPolicy calcula la decisión.

Layouts revisables:
row_credential.xml, row_record.xml, dialog_credential.xml y dialog_simulator.xml.
activity_main.xml usa RecyclerView para las tres listas. Los estados ocultos
tienen tools:visibility y las filas tienen datos tools: de muestra.
Los textos de las pantallas y formularios están en strings.xml.

RecyclerView se eligió por ser el componente de listas Android solicitado por
la docente; ListAdapter/DiffUtil actualizan solo las filas modificadas.
Material Components conserva la apariencia de la app y sus diálogos.

## Reglas y compatibilidad

Cada alta, edición, cambio de permiso y baja exige administrador activo, una
transacción con auditoría, timestamp de servidor e identidad del actor.
Las reglas recalculan los resultados del simulador incluyendo ELIMINADA.
Los clientes no pueden restaurar una tarjeta dada de baja ni borrar registros.
Los documentos antiguos sin eliminada se interpretan como no eliminados;
el primer cambio agrega el campo. No se necesita una migración masiva.
Las colecciones personas y usuarios quedan cerradas para todos los clientes.
Sus documentos existentes no se borran.

IMPORTANTE: las reglas nuevas NO quedaron publicadas en el Firebase real.
El intento de publicación falló porque Firebase CLI no tiene sesión.
Hasta publicarlas, las reglas anteriores rechazarán las nuevas escrituras del CRUD.
Las versiones antiguas de la app deben actualizarse cuando se publiquen las reglas.

Desde PowerShell, en el proyecto original:

    .\tests\firebase\node_modules\.bin\firebase.cmd login
    .\tests\firebase\node_modules\.bin\firebase.cmd deploy --only firestore:rules --project iot-aki-d467d

Este despliegue publica reglas, no borra datos.
Si aún no están las dependencias: entrar a tests/firebase y ejecutar npm ci.

## Verificación realizada

- assembleDebug y lintDebug: correctos; lint sin errores (persisten advertencias).
- 7 pruebas unitarias correctas: 6 de AccessPolicy y 1 de ejemplo existente.
- 15 pruebas de reglas correctas en demo-smartlock, con Auth y Firestore emulados.
  Cubren CRUD autorizado, bloqueo de otros roles, baja permanente, conservación
  del historial, rechazo de acceso eliminado, auditoría inmutable, escrituras
  sin auditoría y cierre de personas/usuarios.
- 2 pruebas instrumentadas CrudLayoutTest correctas en el teléfono CPH2767:
  inflado de formularios XML, tres RecyclerView y acciones deshabilitadas para
  tarjetas eliminadas o sin conexión. Estas pruebas NO escriben en Firebase real.
- No se ejecutó LiveFirebaseTest contra la nube real para esta modificación.
- No se midieron distancia, consumo ni latencia entre dos teléfonos físicos.
- El firmware y la sincronización con cerradura real siguen pendientes.

Comandos reproducibles:

    $env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
    .\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
    .\gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.example.iot.CrudLayoutTest'
    cd tests/firebase
    $env:PATH="$env:JAVA_HOME\bin;$env:PATH"
    npm test

## Historial de desarrollo

Se inicializó un repositorio Git local con el estado recibido y commits
sucesivos de esta implementación. No se reconstruyó un historial previo ni
se publicó el repositorio. Los commits de asistencia usan el autor Codex.
