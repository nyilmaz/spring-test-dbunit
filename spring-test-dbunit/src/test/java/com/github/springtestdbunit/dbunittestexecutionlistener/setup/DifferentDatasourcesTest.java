package com.github.springtestdbunit.dbunittestexecutionlistener.setup;

import com.github.springtestdbunit.DbUnitTestExecutionListener;
import com.github.springtestdbunit.annotation.DatabaseConnectionSetup;
import com.github.springtestdbunit.annotation.DatabaseSetup;
import com.github.springtestdbunit.annotation.DbUnitConfiguration;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * @author nyilmaz
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration("/META-INF/dbunit-context.xml")
@TestExecutionListeners(listeners = {DependencyInjectionTestExecutionListener.class, DbUnitTestExecutionListener.class})
@DbUnitConfiguration(databaseConnections = "dataSource1,dataSource2")
public class DifferentDatasourcesTest {

   private JdbcTemplate template1;

   private JdbcTemplate template2;

   @Autowired
   @Qualifier("dataSource1")
   public void setTemplate1(DataSource dataSource) {
      template1 = new JdbcTemplate(dataSource);
   }

   @Autowired
   @Qualifier("dataSource2")
   public void setTemplate2(DataSource dataSource) {
      template2 = new JdbcTemplate(dataSource);
   }



   @Test
   @DatabaseSetup(connections = {
                     @DatabaseConnectionSetup(connectionName = "dataSource1", value = "/META-INF/db/different/datasource/datasource1.xml"),
                     @DatabaseConnectionSetup(connectionName = "dataSource2", value = "/META-INF/db/different/datasource/datasource2.xml")
   })
   public void shouldWorkWithTwoDatasources() {
      assertNotNull(template1);
      assertNotNull(template2);

      List<Example1Bean> example1Beans = template1.query("SELECT * FROM example1 ORDER BY field1 ASC",
                                                           new RowMapper<Example1Bean>() {
                                                              public Example1Bean mapRow(ResultSet resultSet, int i) throws SQLException {
                                                                 Example1Bean example1Bean = new Example1Bean();
                                                                 example1Bean.setField1(resultSet.getString("field1"));
                                                                 example1Bean.setField2(resultSet.getString("field2"));
                                                                 return example1Bean;
                                                              }
                                                           });
      List<Example2Bean> example2Beans = template2.query("SELECT * FROM EXAMPLE2 ORDER BY value1 ASC",
                                                           new RowMapper<Example2Bean>() {
                                                              public Example2Bean mapRow(ResultSet resultSet, int i) throws SQLException {
                                                                 Example2Bean example2Bean = new Example2Bean();
                                                                 example2Bean.setValue1(resultSet.getInt("value1"));
                                                                 example2Bean.setValue2(resultSet.getInt("value2"));
                                                                 return example2Bean;
                                                              }
                                                           });

      assertEquals(2, example1Beans.size());
      assertEquals(3, example2Beans.size());

      assertEquals("JUnit", example1Beans.get(0).getField1());
      assertEquals("is useful", example1Beans.get(0).getField2());
      assertEquals("Spring", example1Beans.get(1).getField1());
      assertEquals("is awesome", example1Beans.get(1).getField2());

      assertEquals(new Integer(1), example2Beans.get(0).getValue1());
      assertEquals(new Integer(2), example2Beans.get(0).getValue2());
      assertEquals(new Integer(3), example2Beans.get(1).getValue1());
      assertEquals(new Integer(5), example2Beans.get(1).getValue2());
      assertEquals(new Integer(32), example2Beans.get(2).getValue1());
      assertEquals(new Integer(42), example2Beans.get(2).getValue2());

   }

   static class Example1Bean {

      Example1Bean() {
      }

      String field1;
      String field2;

      public String getField1() {
         return field1;
      }

      void setField1(String field1) {
         this.field1 = field1;
      }

      public String getField2() {
         return field2;
      }

      void setField2(String field2) {
         this.field2 = field2;
      }
   }

   public static class Example2Bean {

      public Example2Bean() {
      }

      Integer value1;
      Integer value2;

      public Integer getValue1() {
         return value1;
      }

      public void setValue1(Integer value1) {
         this.value1 = value1;
      }

      public Integer getValue2() {
         return value2;
      }

      public void setValue2(Integer value2) {
         this.value2 = value2;
      }
   }

}
