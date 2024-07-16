# CraftAPIClient

## Description

This is Minecraft library for contacting the Mojang API. It features modern features of Java and flexible way of
communicating with Mojang. Furthermore, this project is used for experimenting with Unit-Tests.

## Features

* Compatibility with Minecraft 1.8.8+
* Thread-Safe
* Usage of modern Java 11 HTTP Client features
* UUID and skin querying
* Skin changing
* Throws exceptions to let the user decide how to handle errors
* Default in-memory cache
* Rotating proxies
* Configurable amount of name -> uuid requests before using proxies

## Planned

### Features

* HTTP request interceptor to redirect to this library
  * Then other plugins could re-use our results
* Cache cracked username requests
* Wait a customizable interval for sending bulk requests like in Data Loader (although this increases latency)
* Add support for multiple outgoing IP addresses back
* Add multiple remote APIs besides Mojang:
  * [MineTools](https://api.minetools.eu/)

### Build

* Callback support
* Unit testing for HTTP requests like in [google-http-client](https://github.com/google/google-http-java-client)

## Requirements

* Java 11+
  * Strong usage of the new HTTP client for HTTP/2 support
* GSON
* Guava

## Contribution

This project is open for suggestions (including breaking changes between major version) and contributions. If you have 
an idea to make this library, you are welcome to create an issue ticker or pull request. Please provide units for pull 
requests if possible.

## Maven repository

```xml
<project>
  <repositories>
    <!-- CodeMc -->
    <repository>
        <id>codemc-repo</id>
        <url>https://repo.codemc.org/repository/maven-public/</url>
    </repository>
  </repositories>
  
  <dependencies>
    <!--Common component for contacting the Mojang API-->
    <dependency>
        <groupId>com.github.games647</groupId>
        <artifactId>craftapi</artifactId>
        <version>VERSION</version>
    </dependency>
  </dependencies>
</project>
```

## Credits

* [Fast-UUID](https://github.com/jchambers/fast-uuid) - MIT license
  * Remember to include its license file

Inspired by

* Sponge's
[GameProfileManager](https://jd.spongepowered.org/7.0.0/org/spongepowered/api/profile/GameProfileManager.html)
[GameProfileCache](https://jd.spongepowered.org/7.0.0/org/spongepowered/api/profile/GameProfileCache.html)
* [SquirrelId](https://github.com/EngineHub/SquirrelID)
* [Mojang Authlib](https://github.com/Techcable/Authlib)
* [Mojang AccountsClient](https://github.com/JonMcPherson/AccountsClient/)
* [API proxy Interceptor](https://github.com/Shevchik/MojangAPIProxy)
