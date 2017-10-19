package ru.opentech.logback.mailsender;

import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Layout;
import ch.qos.logback.core.pattern.PatternLayoutBase;

import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MailsenderAppender extends MailsenderAppenderBase<ILoggingEvent> {

    private static final String DEFAULT_SUBJECT_PATTERN = "%logger{20} - %m";

    // шаблон писем
    private Layout<ILoggingEvent> layout;

    // шаблон темы
    private Layout<ILoggingEvent> subjectLayout;

    // строка темы (шаблон или просто строка)
    private String subjectStr;

    // шаблон адресатов
    private List<PatternLayoutBase<ILoggingEvent>> toPatternLayoutList = new ArrayList<>();

    // адресант
    private String from;

    @Override
    public void start() {
        if( from == null ) {
            throw new IllegalStateException( "Не указан адресант для MailsenderAppender" );
        }
        subjectLayout = buildSubjectLayout( subjectStr );
        super.start();
    }

    /**
     * Создание строки темы письма с использованием шаблона
     * @param event событие лога
     * @return тема в виде строки, построенной по шаблону
     */
    @Override
    protected String buildSubject( ILoggingEvent event ) {
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

    @Override
    protected String buildBody( ILoggingEvent event ) {
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

    @Override
    protected List<InternetAddress> parseAddress( ILoggingEvent event ) {
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

    public Layout<ILoggingEvent> getLayout() {
        return layout;
    }

    public void setLayout( Layout<ILoggingEvent> layout ) {
        this.layout = layout;
    }

    public String getSubject() {
        return subjectStr;
    }

    public void setSubject( String subjectStr ) {
        this.subjectStr = subjectStr;
    }

    public void setFrom( String from ) {
        this.from = from;
    }

    @Override
    public String getFrom() {
        return from;
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
