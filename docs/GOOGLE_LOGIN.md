# Acceso y registro con Google — 16 de noviembre de 2026

La pantalla de acceso incluye Continuar con Google. Una cuenta que no existe
se registra mediante Firebase Authentication en el primer acceso. Las cuentas
existentes usan signInWithCredential con un ID token obtenido mediante
Credential Manager y GetSignInWithGoogleOption (flujo explícito del botón).

No se guardan ni registran tokens ni contraseñas. No se solicitan permisos de
Drive, Gmail u otros servicios. La app conserva el acceso con correo/contraseña.
Los errores de cancelación, falta de cuenta, proveedor y colisión de credenciales
tienen mensajes sin detalles sensibles.

Los roles siguen en administradores/{UID} y simuladores/{UID}. Iniciar sesión
con Google NO otorga roles y no cambia las reglas de Firestore.
Las cuentas nuevas requieren habilitación por el responsable.
No se implementa una fusión manual de cuentas ni se copian privilegios por email.

Al cerrar sesión se llama a FirebaseAuth.signOut y a
CredentialManager.clearCredentialState antes de volver al login.

## Configuración comprobada

- Proyecto iot-aki-d467d, app com.example.iot.
- Proveedor Google habilitado en Authentication.
- Cliente OAuth web consultado desde la consola; el identificador público
  está en google_server_client_id en strings.xml. No es un secreto de cliente.
- Se registró en Firebase la huella SHA-1 de la firma debug de esta computadora:
  2C:41:AB:09:9A:29:80:E0:CC:3C:B3:5C:E2:23:9A:38:F0:10:DC:45.
- El google-services.json anterior no incluía oauth_client. Por eso el flujo
  usa explícitamente el ID web confirmado en la consola, sin depender del
  recurso generado default_web_client_id.
- Otra computadora, una APK release o Google Play requieren registrar su
  propio certificado de firma. No compartir la clave privada de debug.

Nota de despliegue: las reglas nuevas del CRUD SÍ se publicaron el 16 de
noviembre, después de redactar CRUD_ADMIN_2026-11-16.md y NFC.md. La consola
confirmó la publicación y la versión activa de ese día. personas y usuarios
quedaron cerradas; las advertencias anteriores de publicación pendiente son
históricas y quedan sustituidas por esta actualización.

## Validación

Compilación, lint y pruebas automáticas de lógica/interfaz.
La prueba de interfaz verifica que el botón Google esté definido en XML junto
al acceso con correo y al registro. No selecciona una cuenta real.
La elección de cuenta, consentimiento y primer login real debe completarlos
el usuario en el teléfono. No se afirma que esa prueba se haya completado.

Referencia oficial:
https://firebase.google.com/docs/auth/android/google-signin
https://developer.android.com/identity/sign-in/credential-manager-siwg-implementation
