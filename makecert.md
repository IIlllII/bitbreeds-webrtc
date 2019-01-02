```
#Create cert and key
openssl req -x509 -newkey rsa:4096 -keyout key.pem -out cert.pem -nodes -sha256

#Must use a password here
openssl pkcs12 -export -in cert.pem -inkey key.pem -name alias -out both.p12

#Import into keystore
keytool -importkeystore -deststorepass websocket -destkeystore ws2-pkcs12.jks -srckeystore both2.p12 -srcstoretype PKCS12

#Debug info
keytool --list  -keystore ws2-pkcs12.jks  -storepass websocket  -v
```