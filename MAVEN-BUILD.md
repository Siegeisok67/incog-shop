# Building Incog-Shop with Maven

## Arch Linux

Install Maven and a JDK capable of compiling Java 25 bytecode:

```bash
sudo pacman -S maven jdk-openjdk
```

Check the active versions:

```bash
java -version
javac -version
mvn -version
```

If you have multiple JDKs installed:

```bash
archlinux-java status
```

Select a JDK 25+ installation if necessary with `archlinux-java set`.

## Build

Run this from the project root (the directory containing `pom.xml`):

```bash
mvn clean package
```

The plugin JAR will be created at:

```text
target/Incog-Shop-1.8.1.jar
```

Only that JAR goes in the server's `plugins/` directory. Vault must be installed on the server. DiscordSRV remains optional for the Discord price-check integration.
