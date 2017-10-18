package ru.opentech.logback.mailsender;

import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Layout;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import ch.qos.logback.core.db.ConnectionSource;
import ch.qos.logback.core.pattern.PatternLayoutBase;

import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MailsenderAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {

    private static final String DEFAULT_SUBJECT_PATTERN = "%logger{20} - %m";
    private static final int DEFAULT_PRIORITY = 10;

    // соединения к СУБД (из пула или из драйвера)
    private ConnectionSource connectionSource;

    // шаблон писем
    private Layout<ILoggingEvent> layout;

    // шаблон темы
    private Layout<ILoggingEvent> subjectLayout;

    // источник письма (см. OtMailsender)
    private String source;
    // приоритет письма (см. OtMailSender)
    private int priority;

    // строка темы (шаблон или просто строка)
    private String subjectStr;

    // шаблон адресатов
    private List<PatternLayoutBase<ILoggingEvent>> toPatternLayoutList = new ArrayList<>();

    // адресант
    private String from;

    @Override
    public void start() {
        if( connectionSource == null ) {
            throw new IllegalStateException( "Не указан connectionSource для MailsenderAppender" );
        }
        if( from == null ) {
            throw new IllegalStateException( "Не указан адресант для MailsenderAppender" );
        }
        subjectLayout = buildSubjectLayout( subjectStr );
        if( priority == 0 ) {
            priority = DEFAULT_PRIORITY;
        }
        if( source == null ) {
            source = "UNKNOWN_SOURCE";
        }
        super.start();
    }

    @Override
    protected void append( ILoggingEvent event ) {
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
     * Создание шаблона темы письма из заданного в настройках
     * @param subjectStr шаблон темы письма в виде строки
     * @return шаблон темы для logback
     */
    private Layout<ILoggingEvent> buildSubjectLayout( String subjectStr ) {
        if( subjectStr == null ) {
            subjectStr = DEFAULT_SUBJECT_PATTERN;
        }
        PatternLayout patternLayout = new PatternLayout();
        patternLayout.setContext( getContext() );
        patternLayout.setPattern( subjectStr );
        patternLayout.setPostCompileProcessor( null );
        patternLayout.start();
        return patternLayout;
    }

    /**
     * Добавление в СУБД тела письма
     * @param connection соединение к СУБД
     * @param event событие лога
     * @return идентификатор тела письма
     * @throws SQLException в случае ошибки
     */
    private int insertMailBody( Connection connection, ILoggingEvent event ) throws SQLException {
        ResultSet resultSet = null;
        try(
            PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO mailsender.body( sender, subject, body ) VALUES ( ?, ?, ? ) RETURNING id"
            )
        ) {
            statement.setString( 1, from );
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
    private void insertMailHeader( Connection connection, int bodyId, ILoggingEvent event ) throws SQLException {
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
            for( InternetAddress recipient : parseAddress( event ) ) {
                statement.setString( 2, recipient.toString() );
                statement.executeUpdate();
            }
        }
    }

    /**
     * Получение адресатов из шаблона, указанного в настройках
     * @param event событие лога
     * @return список почтовых ящиков, на которые надо отправить письмо
     */
    private List<InternetAddress> parseAddress( ILoggingEvent event ) {
        List<InternetAddress> addresses = new ArrayList<>();
        for( PatternLayoutBase<ILoggingEvent> email : toPatternLayoutList ) {
            try {
                String address = email.doLayout( event );
                if( address == null || address.length() == 0 ) {
                    continue;
                }
                InternetAddress[] buffer = InternetAddress.parse( address, true );
                addresses.addAll( Arrays.asList( buffer ) );
            }
            catch( AddressException e ) {
                addError( "Не удалось распознать email адресата", e );
                return addresses;
            }
        }
        return addresses;
    }

    /**
     * Создание строки темы письма с использованием шаблона
     * @param event событие лога
     * @return тема в виде строки, построенной по шаблону
     */
    private String buildSubject( ILoggingEvent event ) {
        String subject = "Тема не указана";
        if( subjectLayout != null ) {
            subject = subjectLayout.doLayout( event );
            // вырезаем символы перевода строки
            int newLinePosition = subject != null ? subject.indexOf( '\n' ) : -1;
            if( newLinePosition > -1 ) {
                subject = subject.substring( 0, newLinePosition );
            }
        }
        return subject;
    }

    /**
     * Формирование тела письма из события лога
     * @param event событие лога
     * @return тело письма в виде строки
     */
    private String buildBody( ILoggingEvent event ) {
        StringBuilder buffer = new StringBuilder();

        String header = layout.getFileHeader();
        if( header != null ) {
            buffer.append( header );
        }
        String presentationHeader = layout.getPresentationHeader();
        if( presentationHeader != null ) {
            buffer.append( presentationHeader );
        }
        buffer.append( layout.doLayout( event ) );
        String presentationFooter = layout.getPresentationFooter();
        if( presentationFooter != null ) {
            buffer.append( presentationFooter );
        }
        String footer = layout.getFileFooter();
        if( footer != null ) {
            buffer.append( footer );
        }

        return buffer.toString();
    }

    public ConnectionSource getConnectionSource() {
        return connectionSource;
    }

    public void setConnectionSource( ConnectionSource connectionSource ) {
        this.connectionSource = connectionSource;
    }

    public Layout<ILoggingEvent> getLayout() {
        return layout;
    }

    public void setLayout( Layout<ILoggingEvent> layout ) {
        this.layout = layout;
    }

    public void setSource( String source ) {
        this.source = source;
    }

    public void setPriority( int priority ) {
        this.priority = priority;
    }

    public String getSubject() {
        return subjectStr;
    }

    public void setSubject( String subjectStr ) {
        this.subjectStr = subjectStr;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom( String from ) {
        this.from = from;
    }

    public void addTo( String to ) {
        if( to == null || to.length() == 0 ) {
            throw new IllegalArgumentException( "Свойство <to> пусто или не указано" );
        }
        PatternLayout patternLayout = buildToPatternLayout( to.trim() );
        patternLayout.setContext( context );
        patternLayout.start();
        toPatternLayoutList.add( patternLayout );
    }

    private PatternLayout buildToPatternLayout( String to ) {
        PatternLayout patternLayout = new PatternLayout();
        patternLayout.setPattern( to + "%nopex" );
        return patternLayout;
    }

}
