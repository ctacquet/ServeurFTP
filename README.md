# ServeurFTP
Création d'un serveur FTP à l'aide de Java

L'objectif du projet est désormais de mettre en œuvre un serveur conforme au protocole applicatif File Transfer Protocol (FTP). Ce serveur doit donc utiliser l'API Socket TCP pour échanger avec un client FTP (e.g., Filezilla) pour stocker et envoyer des fichiers en respectant le standard FTP.

Le projet est constitué d'une classe de serveur qui permet à autant de clients que possible de se connecter. Chaque client est représenté sous la forme d'une session FTP.

Cette session est une autre classe de ce projet. Elle contient tous les flux liées à l'envoi et la réception de données et de messages.



## Commandes prises en charge

Dans la classe session nous avons toutes les commandes possibles pour un serveur FTP que voici :

- USER
- PASS
- CWD
- LIST
- PWD
- PASV
- SYST
- FEAT
- OPTS
- PORT
- RETR
- MKD
- DELETE
- TYPE
- STOR
- QUIT

## Lien de la vidéo de démonstration

https://youtu.be/IRpAIPgA54k
