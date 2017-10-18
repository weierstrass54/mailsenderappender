package ru.opentech.mailsender;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;

public class MailsenderTest {

    private static final Logger log = LoggerFactory.getLogger( MailsenderTest.class );

    @Before
    public void before() throws ClassNotFoundException, SQLException {
        clear();
    }

    @Test
    public void test() throws SQLException {
        log.error( "NASTY ERROR HERE!", new Exception( "Some nasty reason" ) );

        Connection connection = null;
        PreparedStatement statement = null;
        ResultSet rs = null;
        try {
            connection = DriverManager.getConnection( "jdbc:pgsql://192.168.128.233:5432/test", "postgres", "mysqld" );
            statement = connection.prepareStatement( "SELECT COUNT(*) FROM mailsender.head" );
            rs = statement.executeQuery();
            Assert.assertTrue( rs.next() );
            Assert.assertEquals( 1, rs.getLong( 1 ) );
        }
        finally {
            rs.close();
            statement.close();
            connection.close();
        }
    }

    @After
    public void after() throws ClassNotFoundException, SQLException {
        clear();
    }

    private void clear() throws ClassNotFoundException, SQLException {
        Class.forName( "com.impossibl.postgres.jdbc.PGDriver" );
        try(
            Connection connection = DriverManager.getConnection( "jdbc:pgsql://192.168.128.233:5432/test", "postgres", "mysqld" );
            PreparedStatement statementHead = connection.prepareStatement( "DELETE FROM mailsender.head" );
            PreparedStatement statementBody = connection.prepareStatement( "DELETE FROM mailsender.body" );
        ) {
            statementHead.executeUpdate();
            statementBody.executeUpdate();
        }
    }

}
