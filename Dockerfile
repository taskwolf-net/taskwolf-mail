FROM openjdk:21

COPY /build/libs/mail-1.0.0-SNAPSHOT.jar mail.jar
COPY /locale/ /locale/