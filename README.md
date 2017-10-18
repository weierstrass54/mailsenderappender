# Mailsender Appender для logback

Appender для logback, адаптированный для отсылки писем через mailsender. Сочетает в себе DbAppender и SMTPAppender.

Требует наличия:

 * logback-classic
 * javax.mail.mail
 * драйвер JDBC
 * пул соединений JDBC (необязательно, но крайне рекомендуется)

Поддерживает инструменты настройки Appender, такие как фильтры, паттерны, шаблоны и т. д.

## Примеры настройки
### С пулом соединений
```xml
<appender name="EMAIL" class="ru.opentech.logback.mailsender.MailsenderAppender">
    <filter class="ch.qos.logback.classic.filter.ThresholdFilter">
        <level>ERROR</level>
    </filter>
    <connectionSource class="ch.qos.logback.core.db.DataSourceConnectionSource">
        <dataSource class="com.zaxxer.hikari.HikariDataSource">
            <driverClass>com.impossibl.postgres.jdbc.PGDriver</driverClass>
            <jdbcUrl>jdbc:pgsql://192.168.128.233:5432/test</jdbcUrl>
            <maxPoolSize>3</maxPoolSize>
            <username>postgres</username>
            <password>mysqld</password>
        </dataSource>
    </connectionSource>
    <to>v.kibalov@e2e4online.ru</to>
    <from>dc@e2e4online.ru</from>
    <subject>nasty error: %m</subject>
    <priority>10</priority>
    <source>MAILSENDER.TEST</source>
    <layout class="ch.qos.logback.classic.html.HTMLLayout"/>
</appender>
```

### Без пула соединений (Крайне не рекомендуется! Будет работать ОЧЕНЬ медленно)
```xml
<appender name="EMAIL" class="ru.opentech.logback.mailsender.MailsenderAppender">
    <filter class="ch.qos.logback.classic.filter.ThresholdFilter">
        <level>ERROR</level>
    </filter>
    <connectionSource class="ch.qos.logback.core.db.DataSourceConnectionSource">
        <driverClass>com.impossibl.postgres.jdbc.PGDriver</driverClass>
        <url>jdbc:pgsql://192.168.128.233:5432/test</url>
        <user>postgres</user>
        <password>mysqld</password>
    </connectionSource>
    <to>v.kibalov@e2e4online.ru</to>
    <from>dc@e2e4online.ru</from>
    <subject>nasty error: %m</subject>
    <priority>10</priority>
    <source>MAILSENDER.TEST</source>
    <layout class="ch.qos.logback.classic.html.HTMLLayout"/>
</appender>
```