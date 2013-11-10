/*
 * Copyright 2010-2012 the original author or authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.springtestdbunit;

import com.github.springtestdbunit.DbUnitRule.DbUnitTestContextAdapter;
import com.github.springtestdbunit.dataset.DataSetLoader;
import com.github.springtestdbunit.dataset.FlatXmlDataSetLoader;
import com.github.springtestdbunit.operation.DatabaseOperationLookup;
import com.github.springtestdbunit.operation.DefaultDatabaseOperationLookup;
import com.github.springtestdbunit.testutils.NotSwallowedException;
import org.dbunit.database.IDatabaseConnection;
import org.junit.Test;
import org.junit.internal.runners.statements.Fail;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;

import javax.sql.DataSource;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class DbUnitRuleTest {

	@Test
	public void shouldGetTestMethod() throws Exception {
		Blank target = new Blank();
		Method method = target.getClass().getMethod("test");
		FrameworkMethod frameworkMethod = new FrameworkMethod(method);
		DbUnitTestContextAdapter dbUnitTestContextAdapter = new DbUnitRule().new DbUnitTestContextAdapter(
				frameworkMethod, target);
		assertSame(method, dbUnitTestContextAdapter.getTestMethod());
	}

	@Test
	public void shouldUseSetDataSource() throws Exception {
		DataSource dataSource = mock(DataSource.class);
		Connection connection = mock(Connection.class);
		when(dataSource.getConnection()).thenReturn(connection);
		Blank target = new Blank();
		FrameworkMethod method = new FrameworkMethod(target.getClass().getMethod("test"));
		DbUnitRule rule = new DbUnitRule();
      Map<String, DataSource> dataSourceMap = new HashMap<String, DataSource>();
      String dataSourceFieldName = "dataSourceFieldName";
      dataSourceMap.put(dataSourceFieldName, dataSource);
		rule.setDataSources(dataSourceMap);
		DbUnitTestContextAdapter dbUnitTestContextAdapter = rule.new DbUnitTestContextAdapter(method, target);
		dbUnitTestContextAdapter.getConnectionsMap().get(dataSourceFieldName).getConnection().createStatement();
		verify(connection).createStatement();
	}

	@Test
	public void shouldUseSetDatabaseConnection() throws Exception {
		IDatabaseConnection connection = mock(IDatabaseConnection.class);
		Blank target = new Blank();
		FrameworkMethod method = new FrameworkMethod(target.getClass().getMethod("test"));
      Map<String, IDatabaseConnection> connectionMap = new HashMap<String, IDatabaseConnection>();
      connectionMap.put("connection1", connection);
		DbUnitRule rule = new DbUnitRule();
		rule.setDatabaseConnections(connectionMap);
		DbUnitTestContextAdapter dbUnitTestContextAdapter = rule.new DbUnitTestContextAdapter(method, target);
		assertSame(connection, dbUnitTestContextAdapter.getConnectionsMap().get("connection1"));
	}

	@Test
	public void shouldUseSetDataSetLoader() throws Exception {
		DataSetLoader dataSetLoader = mock(DataSetLoader.class);
		Blank target = new Blank();
		FrameworkMethod method = new FrameworkMethod(target.getClass().getMethod("test"));
		DbUnitRule rule = new DbUnitRule();
		rule.setDataSetLoader(dataSetLoader);
		DbUnitTestContextAdapter dbUnitTestContextAdapter = rule.new DbUnitTestContextAdapter(method, target);
		assertSame(dataSetLoader, dbUnitTestContextAdapter.getDataSetLoader());
	}

	@Test
	public void shouldUseSetDatabseOperationLookup() throws Exception {
		DatabaseOperationLookup lookup = mock(DatabaseOperationLookup.class);
		Blank target = new Blank();
		FrameworkMethod method = new FrameworkMethod(target.getClass().getMethod("test"));
		DbUnitRule rule = new DbUnitRule();
		rule.setDatabaseOperationLookup(lookup);
		DbUnitTestContextAdapter dbUnitTestContextAdapter = rule.new DbUnitTestContextAdapter(method, target);
		assertSame(lookup, dbUnitTestContextAdapter.getDatbaseOperationLookup());
	}

	@Test
	public void shouldFindDataSourceFromTestCase() throws Exception {
		Connection connection = mock(Connection.class);
		WithDataSource target = new WithDataSource(connection);
		FrameworkMethod method = new FrameworkMethod(target.getClass().getMethod("test"));
		DbUnitTestContextAdapter dbUnitTestContextAdapter = new DbUnitRule().new DbUnitTestContextAdapter(method,
				target);
      Connection conn = dbUnitTestContextAdapter.getConnectionsMap().get("dataSource").getConnection();
      conn.createStatement();
		verify(connection).createStatement();
	}

	@Test
	public void shouldFindDatabaseConnectionFromTestCase() throws Exception {
		IDatabaseConnection connection = mock(IDatabaseConnection.class);
		WithDatabaseConnection target = new WithDatabaseConnection(connection);
		FrameworkMethod method = new FrameworkMethod(target.getClass().getMethod("test"));
		DbUnitTestContextAdapter dbUnitTestContextAdapter = new DbUnitRule().new DbUnitTestContextAdapter(method,
				target);
		assertSame(connection, dbUnitTestContextAdapter.getConnectionsMap().get("databaseConnection"));
	}

	@Test
	public void shouldFailIfNoConnection() throws Exception {
		Blank target = new Blank();
		FrameworkMethod method = new FrameworkMethod(target.getClass().getMethod("test"));
		DbUnitTestContextAdapter dbUnitTestContextAdapter = new DbUnitRule().new DbUnitTestContextAdapter(method,
				target);
		try {
			dbUnitTestContextAdapter.getConnectionsMap();
		} catch (IllegalStateException e) {
			assertEquals("Unable to locate database connection for DbUnitRule.  "
					+ "Ensure that a DataSource or IDatabaseConnection is available as "
					+ "a private member of your test", e.getMessage());
		}
	}

   @Test
   public void shouldFindMultipleDataSources() throws Exception{
      Connection connection1 = mock(Connection.class);
      Connection connection2 = mock(Connection.class);
      WithMultipleDataSource target = new WithMultipleDataSource(connection1, connection2);
      FrameworkMethod method = new FrameworkMethod(target.getClass().getMethod("test"));
      DbUnitTestContextAdapter dbUnitTestContextAdapter = new DbUnitRule().new DbUnitTestContextAdapter(method, target);
      Map<String, IDatabaseConnection> connectionMap = dbUnitTestContextAdapter.getConnectionsMap();
      assertTrue(connectionMap.containsKey("dataSource1"));
      assertTrue(connectionMap.containsKey("dataSource2"));
      connectionMap.get("dataSource1").getConnection().createStatement();
      connectionMap.get("dataSource2").getConnection().createStatement();
      verify(connection1).createStatement();
      verify(connection2).createStatement();
   }

   @Test
   public void shouldFindDataSourceAndConnection() throws Exception {
      Connection connection1 = mock(Connection.class);
      IDatabaseConnection connection2 = mock(IDatabaseConnection.class);
      WithDataSourceAndConnection target = new WithDataSourceAndConnection(connection2, connection1);
      FrameworkMethod method = new FrameworkMethod(target.getClass().getMethod("test"));
      DbUnitTestContextAdapter dbUnitTestContextAdapter = new DbUnitRule().new DbUnitTestContextAdapter(method, target);
      Map<String, IDatabaseConnection> connectionMap = dbUnitTestContextAdapter.getConnectionsMap();
      assertTrue(connectionMap.containsKey("connection"));
      assertTrue(connectionMap.containsKey("dataSource"));
      assertEquals(connection2, connectionMap.get("connection"));
      connectionMap.get("dataSource").getConnection().createStatement();
      verify(connection1).createStatement();
   }

	@Test
	public void shouldFindDataSetLoaderFromTestCase() throws Exception {
		WithDataSetLoader target = new WithDataSetLoader();
		FrameworkMethod method = new FrameworkMethod(target.getClass().getMethod("test"));
		DbUnitTestContextAdapter dbUnitTestContextAdapter = new DbUnitRule().new DbUnitTestContextAdapter(method,
				target);
		assertSame(target.loader, dbUnitTestContextAdapter.getDataSetLoader());
	}

	@Test
	public void shouldFindDatabaseOperationLookupFromTestCase() throws Exception {
		WithDatabaseOperationLookup target = new WithDatabaseOperationLookup();
		FrameworkMethod method = new FrameworkMethod(target.getClass().getMethod("test"));
		DbUnitTestContextAdapter dbUnitTestContextAdapter = new DbUnitRule().new DbUnitTestContextAdapter(method,
				target);
		assertSame(target.lookup, dbUnitTestContextAdapter.getDatbaseOperationLookup());
	}

	@Test
	public void shouldUseXmlDataSetLoaderIfNotSet() throws Exception {
		Blank target = new Blank();
		FrameworkMethod method = new FrameworkMethod(target.getClass().getMethod("test"));
		DbUnitTestContextAdapter dbUnitTestContextAdapter = new DbUnitRule().new DbUnitTestContextAdapter(method,
				target);
		DataSetLoader loader = dbUnitTestContextAdapter.getDataSetLoader();
		assertNotNull(loader);
		assertEquals(FlatXmlDataSetLoader.class, loader.getClass());
	}

	@Test
	public void shouldUseDefaultDatabaseOperationLookupIfNotSet() throws Exception {
		Blank target = new Blank();
		FrameworkMethod method = new FrameworkMethod(target.getClass().getMethod("test"));
		DbUnitTestContextAdapter dbUnitTestContextAdapter = new DbUnitRule().new DbUnitTestContextAdapter(method,
				target);
		DatabaseOperationLookup lookup = dbUnitTestContextAdapter.getDatbaseOperationLookup();
		assertNotNull(lookup);
		assertEquals(DefaultDatabaseOperationLookup.class, lookup.getClass());
	}

	// Issue : https://github.com/springtestdbunit/spring-test-dbunit/issues/26
	@Test
	public void shouldPropagateExceptionThrownInFailingTest() throws Throwable {
		NotSwallowedException cause = new NotSwallowedException();
		Statement base = new Fail(cause);
		DbUnitRule rule = new DbUnitRule();
		// only to satisfy apply signature
		Connection conn = mock(Connection.class);
		WithDataSource dummyTarget = new WithDataSource(conn);
		FrameworkMethod dummyMethod = new FrameworkMethod(dummyTarget.getClass().getMethod("test"));
		Statement decorated = rule.apply(base, dummyMethod, dummyTarget);
		try {
			decorated.evaluate();
			fail("rule swallowed a test exception");
		} catch (final NotSwallowedException actual) {
			assertSame("rule modified the test failure cause", cause, actual);
		}
	}

	static class Blank {
		public void test() {
		}
	}

	static class WithDataSource extends Blank {
		private DataSource dataSource;

		public WithDataSource(Connection connection) throws SQLException {
			this.dataSource = mock(DataSource.class);
			when(this.dataSource.getConnection()).thenReturn(connection);
		}
	}

	static class WithDatabaseConnection extends Blank {
		@SuppressWarnings("unused")
		private IDatabaseConnection databaseConnection;

		public WithDatabaseConnection(IDatabaseConnection databaseConnection) {
			this.databaseConnection = databaseConnection;
		}
	}

	static class WithMultipleDataSource extends Blank {
		private DataSource dataSource1;

		private DataSource dataSource2;

      public WithMultipleDataSource(Connection connection1, Connection connection2) throws SQLException{
         this.dataSource1 = mock(DataSource.class);
         this.dataSource2 = mock(DataSource.class);
         when(this.dataSource1.getConnection()).thenReturn(connection1);
         when(this.dataSource2.getConnection()).thenReturn(connection2);
      }
   }

	static class WithDataSetLoader extends Blank {
		private DataSetLoader loader = mock(DataSetLoader.class);
	}

	static class WithDatabaseOperationLookup extends Blank {
		private DatabaseOperationLookup lookup = mock(DatabaseOperationLookup.class);
	}

   static class WithDataSourceAndConnection extends Blank {
      private DataSource dataSource;
      @SuppressWarnings("unused")
      private IDatabaseConnection connection;

      WithDataSourceAndConnection(IDatabaseConnection connection1, Connection connection2) throws SQLException{
         this.connection = connection1;
         this.dataSource = mock(DataSource.class);
         when(this.dataSource.getConnection()).thenReturn(connection2);
      }
   }
}
