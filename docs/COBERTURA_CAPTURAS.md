# Cobertura de capturas de la APK

44 capturas reales tomadas durante las revisiones del 12 de noviembre de 2026. El informe incluye la matriz de correspondencia con las 24 láminas del mockup.

| Figura | Vista o estado | Captura |
|---|---|---|
| B01 | Inicio de sesión | [04-login-real](capturas/04-login-real.png) |
| B02 | Validación del formulario | [05-validacion-correo](capturas/05-validacion-correo.png) |
| B03 | Cuenta sin administración | [06-cuenta-sin-permisos](capturas/06-cuenta-sin-permisos.png) |
| B04 | Alta de credencial | [03-alta-completada](capturas/03-alta-completada.png) |
| B05 | Búsqueda y estado activo | [15-busqueda-credencial](capturas/15-busqueda-credencial.png) |
| B06 | Detalle de la credencial | [16-detalle-credencial](capturas/16-detalle-credencial.png) |
| B07 | Acceso permitido | [09-acceso-permitido](capturas/09-acceso-permitido.png) |
| B08 | Evento recibido en el teléfono | [11-detalle-evento](capturas/11-detalle-evento.png) |
| B09 | Confirmar revocación | [17-confirmacion-revocacion](capturas/17-confirmacion-revocacion.png) |
| B10 | Revocación confirmada | [18-credencial-revocada](capturas/18-credencial-revocada.png) |
| B11 | Rechazo después de revocar | [19-rechazo-revocada](capturas/19-rechazo-revocada.png) |
| B12 | UID no registrado | [20-rechazo-desconocida](capturas/20-rechazo-desconocida.png) |
| B13 | Historial en el teléfono | [10-historial-compartido](capturas/10-historial-compartido.png) |
| B14 | Auditoría de cambios | [21-auditoria](capturas/21-auditoria.png) |
| B15 | Red interrumpida | [13-sin-conexion](capturas/13-sin-conexion.png) |
| B16 | Conexión restablecida | [14-reconexion](capturas/14-reconexion.png) |
| B17 | Resumen de administración | [22-resumen](capturas/22-resumen.png) |
| B18 | Sin datos del Arduino | [23-estado-dispositivo](capturas/23-estado-dispositivo.png) |
| B19 | Permiso de simulador retirado | [24-rol-revocado](capturas/24-rol-revocado.png) |
| B20 | Clave incorrecta rechazada | [26-clave-incorrecta](capturas/26-clave-incorrecta.png) |
| B21 | Búsqueda sin resultados | [27-busqueda-sin-resultados](capturas/27-busqueda-sin-resultados.png) |
| B22 | Nombre obligatorio | [28-alta-campos-vacios](capturas/28-alta-campos-vacios.png) |
| B23 | UID inválido en el alta | [29-uid-invalido](capturas/29-uid-invalido.png) |
| B24 | Credencial duplicada | [30-credencial-duplicada](capturas/30-credencial-duplicada.png) |
| B25 | Confirmar habilitación | [31-confirmar-habilitacion](capturas/31-confirmar-habilitacion.png) |
| B26 | Cambio cancelado | [32-cancelar-cambio](capturas/32-cancelar-cambio.png) |
| B27 | Habilitación confirmada | [33-credencial-habilitada](capturas/33-credencial-habilitada.png) |
| B28 | Opciones del historial | [34-opciones-filtro](capturas/34-opciones-filtro.png) |
| B29 | Filtro de permitidos | [35-filtro-permitidos](capturas/35-filtro-permitidos.png) |
| B30 | Filtro de rechazados | [36-filtro-rechazados](capturas/36-filtro-rechazados.png) |
| B31 | Filtro de simulaciones | [37-filtro-simulados](capturas/37-filtro-simulados.png) |
| B32 | Historial sin resultados | [38-historial-sin-eventos](capturas/38-historial-sin-eventos.png) |
| B33 | Detalle de intento rechazado | [39-detalle-rechazado](capturas/39-detalle-rechazado.png) |
| B34 | Contraseña obligatoria | [40-password-vacia](capturas/40-password-vacia.png) |
| B35 | Contraseña de registro corta | [41-password-corta](capturas/41-password-corta.png) |
| B36 | Recuperación solicitada | [42-recuperacion-solicitada](capturas/42-recuperacion-solicitada.png) |
| B37 | Autenticación en curso | [43-autenticacion-en-curso](capturas/43-autenticacion-en-curso.png) |
| B38 | UID inválido en el simulador | [44-validacion-uid-simulador](capturas/44-validacion-uid-simulador.png) |
| B39 | Simulador autorizado | [45-listo-para-validar](capturas/45-listo-para-validar.png) |
| B40 | Validación en curso | [46-validando-credencial](capturas/46-validando-credencial.png) |
| B41 | Error de validación | [47-no-se-pudo-validar](capturas/47-no-se-pudo-validar.png) |
| B42 | Reintento confirmado | [48-reintento-confirmado](capturas/48-reintento-confirmado.png) |
| B43 | Vincular segundo Android | [49-vinculacion-segundo-android](capturas/49-vinculacion-segundo-android.png) |
| B44 | UID de cuenta inválido | [50-uid-cuenta-invalido](capturas/50-uid-cuenta-invalido.png) |

## Diferencias que siguen identificadas

- A09 Ajustes: no hay pestaña Ajustes. La vinculación está en Inicio (B43, B44).
- A18 Carga de credenciales: sin captura específica. La espera de autenticación B37 es otro estado.
- A19 Lista vacía: B21 muestra búsqueda sin coincidencias y B32 historial sin resultados. No se borraron credenciales para vaciar la colección.
- A20 Error de carga: sin captura específica de la lista. B41 es un error de validación del simulador.

La recuperación de contraseña se solicitó a una cuenta ficticia y Firebase devolvió su respuesta; no se acredita recepción del mensaje. La espera de autenticación se capturó con el emulador configurado con latencia artificial de 1500 ms; no es evidencia de rendimiento real.

La APK oculta parte del correo de la sesión. Se corrigió además el estado de autorización para que no reemplace una validación en curso ni el mensaje No se pudo validar. Se repitieron compilación, cinco pruebas de lógica y lint, y se verificó visualmente el error y el reintento.

Las pruebas nuevas usan un teléfono y un emulador. Continúan pendientes dos teléfonos físicos, medición de distancia y consumo y presentación/validación docente.
