# Lectura y grabación NFC — Smart Lock

## Uso

- Administrador > Credenciales > Agregar > Leer tarjeta por NFC: completa el UID
  físico. Escribe el titular y guarda el registro en Firebase.
- Administrador > Credenciales > Editar titular > Grabar identificación en tarjeta
  NFC: confirma el reemplazo del contenido NDEF y acerca la MISMA tarjeta.
- Punto de acceso > Leer tarjeta por NFC: captura el UID. Pulsa Simular lectura
  para consultar la autorización en Firebase y registrar el intento.

La grabación es una acción física independiente de Guardar titular: cerrar el
formulario después de grabar no deshace lo escrito en la tarjeta.
No es necesario editar el titular para usar el botón de grabación.

## Qué se graba y qué se lee

Se escribe un registro NDEF MIME application/vnd.smartlock.credential con
payload ASCII SMARTLOCK:1:<UID>. No contiene contraseñas, nombres ni claves.
El UID de fabricante no se modifica. La grabación no crea permisos de acceso.
Si una tarjeta contiene ese registro, al leerla se comprueba que el payload
coincida con el UID físico; se rechazan registros Smart Lock inconsistentes.
Una tarjeta sin NDEF se puede registrar por su UID.

Las lecturas y grabaciones admiten NFC-A/B y UID de 4, 7 o 10 bytes.
La escritura depende de que el teléfono exponga Ndef o NdefFormatable.
Se verifica capacidad y protección de escritura. En Ndef se lee después de
escribir para comprobar el mensaje; tras formatear una tarjeta sin NDEF hay que
retirarla y leerla otra vez para comprobarla.
El diálogo confirma expresamente que se reemplaza el contenido NDEF anterior.
No se bloquea permanentemente la tarjeta como solo lectura.

No todas las tarjetas RFID son NFC ni todos los teléfonos soportan las mismas
tecnologías. El RC522 sigue leyendo el UID físico con el firmware existente;
para leer también este registro NDEF en Arduino se necesita implementar el
decodificador NDEF correspondiente. No se añadió ese firmware.
Algunas tarjetas generan UID aleatorio y no son aptas para este registro estable.
El UID no es secreto ni evita clonación.

## Ciclo de vida y permisos

NFC es opcional en el manifiesto; los teléfonos sin NFC conservan ingreso manual.
Solo se escanea después de pulsar el botón, con la app visible.
Se detiene al cancelar, pausar, cerrar el formulario o perder autorización.
Se evita procesar varias veces la misma presentación.
La escritura vuelve a comprobar el permiso de administrador antes de grabar.
Los estados de NFC apagado, hardware ausente, UID incompatible, tarjeta distinta,
solo lectura y fallo de escritura tienen mensajes específicos.
Los eventos continúan como SIMULACION: una lectura NFC real no confirma una
apertura física. La autorización, revocación y eliminación se validan en Firebase.
Las reglas nuevas del CRUD aún requieren la publicación descrita en la guía.

## Verificación

Se comprobó por ADB que el teléfono CPH2767 tiene NFC y está activado.
Se verificaron compilación, lint y pruebas unitarias/instrumentadas.
Los tests cubren UID con ceros iniciales, bytes con signo y longitudes inválidas,
así como serialización NDEF y rechazo de identidad distinta.
No se presentó una tarjeta física durante esta sesión: quedan por comprobar
lectura, escritura y alcance real con las tarjetas del proyecto.

Referencia: Android NfcAdapter y Tag
https://developer.android.com/reference/android/nfc/NfcAdapter
https://developer.android.com/reference/android/nfc/Tag
