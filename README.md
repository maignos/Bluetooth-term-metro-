# Termómetro Bluetooth para Android

Proyecto fuente para Android Studio. Recibe texto enviado por Arduino Uno mediante Bluetooth clásico SPP con HC-05 o HC-06.

## Uso
1. Descomprime el ZIP y abre la carpeta `TermometroBluetooth` en Android Studio.
2. Deja que Gradle sincronice y descargue las dependencias.
3. En Android, empareja primero el módulo desde Ajustes > Bluetooth. PIN común: `1234` o `0000`, según el módulo.
4. Ejecuta la app en un teléfono Android físico.
5. Concede permiso Bluetooth, pulsa **Buscar emparejados**, selecciona el módulo y pulsa **Conectar**.

## Formato de datos
El código Arduino anterior envía líneas como `Temperatura: 25.75 C` usando `bluetooth.println(...)`. La app muestra cada línea recibida y destaca la temperatura actual.

## Notas
- Este ZIP contiene código fuente, no un APK compilado.
- Para compilar necesitas Android Studio y conexión a internet la primera vez.
- Es para Bluetooth clásico SPP (HC-05/HC-06), no para módulos BLE como HM-10.
