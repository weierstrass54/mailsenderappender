package ru.opentech.logback.mailsender;

import ch.qos.logback.core.UnsynchronizedAppenderBase;
import ch.qos.logback.core.db.ConnectionSource;

import javax.mail.internet.InternetAddress;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

abstract public class MailsenderAppenderBase<E> extends UnsynchronizedAppenderBase<E> {

    private static final int DEFAULT_PRIORITY = 10;

    // соединения к СУБД (из пула или из драйвера)
    private ConnectionSource connectionSource;

    // источник письма (см. OtMailsender)
    private String source;
    // приоритет письма (см. OtMailSender)
    private int priority;

    @Override
    public void start() {
        if( connectionSource == null ) {
            throw new IllegalStateException( "Не указан connectionSource для MailsenderAppender" );
        }
        if( priority == 0 ) {
            priority = DEFAULT_PRIORITY;
        }
        if( source == null ) {
            source = "UNKNOWN_SOURCE";
        }
        super.start();
    }

    @Override
    protected void append( E event ) {
        try(
            Connection connection = connectionSource.getConnection()
        ) {
            connection.setAutoCommit( false );

            int bodyId;
            // синхронизируемся здесь, чтобы разные потоки не перехватывали bodyId
            synchronized( this ) {
                bodyId = insertMailBody( connection, event );
            }
            insertMailHeader( connection, bodyId, event );

            connection.commit();
        }
        catch( SQLException e ) {
            addError( "Ошибка записи события в СУБД", e );
        }
    }

    /**
     * Добавление в СУБД тела письма
     * @param connection соединение к СУБД
     * @param event событие лога
     * @return идентификатор тела письма
     * @throws SQLException в случае ошибки
     */
    private int insertMailBody( Connection connection, E event ) throws SQLException {
        ResultSet resultSet = null;
        try(
            PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO mailsender.body( sender, subject, body ) VALUES ( ?, ?, ? ) RETURNING id"
            )
        ) {
            statement.setString( 1, getFrom() );
            statement.setString( 2, buildSubject( event ) );
            statement.setString( 3, buildBody( event ) );
            resultSet = statement.executeQuery();
            if( !resultSet.next() ) {
                throw new SQLException( "Не удалось добавить тело письма. Запрос вернул пустоту" );
            }
            return resultSet.getInt( 1 );
        }
        finally {
            if( resultSet != null ) {
                resultSet.close();
            }
        }
    }

    /**
     * Добавление заголовка письма в СУБД
     * @param connection соединение к СУБД
     * @param bodyId идентификатор тела письма
     * @param event событие лога
     * @throws SQLException в случае ошибки
     */
    private void insertMailHeader( Connection connection, int bodyId, E event ) throws SQLException {
        try(
            PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO mailsender.head( id_body, recipient, source, priority ) VALUES ( ?, ?, ?, ? )"
            )
        ) {
            statement.setInt( 1, bodyId );
            statement.setString( 3, source );
            statement.setInt( 4, priority );
            List<InternetAddress> addresses = parseAddress( event );
            if( addresses.isEmpty() ) {
                throw new SQLException( "Список почтовых ящиков пуст. Прерываю передачу." );
            }
            for( InternetAddress recipient : addresses ) {
                statement.setString( 2, recipient.toString() );
                statement.executeUpdate();
            }
        }
    }

    public ConnectionSource getConnectionSource() {
        return connectionSource;
    }

    public void setConnectionSource( ConnectionSource connectionSource ) {
        this.connectionSource = connectionSource;
    }

    public void setSource( String source ) {
        this.source = source;
    }

    public void setPriority( int priority ) {
        this.priority = priority;
    }

    public abstract String getFrom();

    /**
     * Создание строки темы письма
     * @param event событие лога
     * @return тема в виде строки
     */
    protected abstract String buildSubject( E event );

    /**
     * Формирование тела письма из события лога
     * @param event событие лога
     * @return тело письма в виде строки
     */
    protected abstract String buildBody( E event );

    /**
     * Получение адресатов из шаблона, указанного в настройках
     * @param event событие лога
     * @return список почтовых ящиков, на которые надо отправить письмо
     */
    protected abstract List<InternetAddress> parseAddress( E event );

}
