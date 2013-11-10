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

import com.github.springtestdbunit.annotation.DatabaseSetup;
import com.github.springtestdbunit.annotation.DatabaseTearDown;
import com.github.springtestdbunit.annotation.DbUnitConfiguration;
import com.github.springtestdbunit.annotation.ExpectedDatabase;
import com.github.springtestdbunit.bean.DatabaseDataSourceConnectionFactoryBean;
import com.github.springtestdbunit.dataset.DataSetLoader;
import com.github.springtestdbunit.dataset.FlatXmlDataSetLoader;
import com.github.springtestdbunit.operation.DatabaseOperationLookup;
import com.github.springtestdbunit.operation.DefaultDatabaseOperationLookup;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dbunit.database.IDatabaseConnection;
import org.springframework.core.Conventions;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.AbstractTestExecutionListener;
import org.springframework.test.context.transaction.TransactionalTestExecutionListener;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.lang.reflect.Method;
import java.util.*;

/**
 * <code>TestExecutionListener</code> which provides support for {@link DatabaseSetup &#064;DatabaseSetup},
 * {@link DatabaseTearDown &#064;DatabaseTearDown} and {@link ExpectedDatabase &#064;ExpectedDatabase} annotations.
 * <p>
 * A bean named "<tt>dbUnitDatabaseConnection</tt>" or "<tt>dataSource</tt>" is expected in the
 * <tt>ApplicationContext</tt> associated with the test. This bean can contain either a {@link IDatabaseConnection} or a
 * {@link DataSource} . A custom bean name can also be specified using the
 * {@link DbUnitConfiguration#databaseConnections() &#064;DbUnitConfiguration} annotation.
 * <p>
 * Datasets are loaded using the {@link FlatXmlDataSetLoader} and DBUnit database operation lookups are performed using
 * the {@link DefaultDatabaseOperationLookup} unless otherwise {@link DbUnitConfiguration#dataSetLoader() configured}.
 * <p>
 * If you are running this listener in combination with the {@link TransactionalTestExecutionListener} then consider
 * using {@link TransactionDbUnitTestExecutionListener} instead.
 * 
 * @see TransactionDbUnitTestExecutionListener
 * 
 * @author Phillip Webb
 */
public class DbUnitTestExecutionListener extends AbstractTestExecutionListener {

	private static final Log logger = LogFactory.getLog(DbUnitTestExecutionListener.class);

	private static final String[] COMMON_DATABASE_CONNECTION_BEAN_NAMES = { "dbUnitDatabaseConnection", "dataSource" };

	protected static final String CONNECTION_ATTRIBUTE = Conventions.getQualifiedAttributeName(
			DbUnitTestExecutionListener.class, "connections");

	protected static final String DATA_SET_LOADER_ATTRIBUTE = Conventions.getQualifiedAttributeName(
			DbUnitTestExecutionListener.class, "dataSetLoader");

	protected static final String DATABASE_OPERATION_LOOKUP_ATTRIBUTE = Conventions.getQualifiedAttributeName(
			DbUnitTestExecutionListener.class, "databseOperationLookup");

	private static DbUnitRunner runner = new DbUnitRunner();

	@Override
	public void prepareTestInstance(TestContext testContext) throws Exception {

		if (logger.isDebugEnabled()) {
			logger.debug("Preparing test instance " + testContext.getTestClass() + " for DBUnit");
		}

		Set<String> databaseConnectionBeanNames = new HashSet<String>();
		Class<? extends DataSetLoader> dataSetLoaderClass = FlatXmlDataSetLoader.class;
		Class<? extends DatabaseOperationLookup> databaseOperationLookupClass = DefaultDatabaseOperationLookup.class;

		DbUnitConfiguration configuration = testContext.getTestClass().getAnnotation(DbUnitConfiguration.class);
		if (configuration != null) {
			if (logger.isDebugEnabled()) {
				logger.debug("Using @DbUnitConfiguration configuration");
			}
			databaseConnectionBeanNames = StringUtils.commaDelimitedListToSet(configuration.databaseConnections());
			dataSetLoaderClass = configuration.dataSetLoader();
			databaseOperationLookupClass = configuration.databaseOperationLookup();
		}

      if(databaseConnectionBeanNames.isEmpty()) {
         databaseConnectionBeanNames = StringUtils.commaDelimitedListToSet(getDatabaseConnectionUsingCommonBeanNames(testContext));
      }

		if (logger.isDebugEnabled()) {
			logger.debug("DBUnit tests will run using databaseConnections \"" + databaseConnectionBeanNames
					+ "\", datasets will be loaded using " + dataSetLoaderClass);
		}
		prepareDatabaseConnection(testContext, databaseConnectionBeanNames);
		prepareDataSetLoader(testContext, dataSetLoaderClass);
		prepareDatabaseOperationLookup(testContext, databaseOperationLookupClass);
	}

	private String getDatabaseConnectionUsingCommonBeanNames(TestContext testContext) {
		for (String beanName : COMMON_DATABASE_CONNECTION_BEAN_NAMES) {
			if (testContext.getApplicationContext().containsBean(beanName)) {
				return beanName;
			}
		}
		throw new IllegalStateException(
				"Unable to find a DB Unit database connection, missing one the following beans: "
						+ Arrays.asList(COMMON_DATABASE_CONNECTION_BEAN_NAMES));
	}

	private void prepareDatabaseConnection(TestContext testContext, Set<String> databaseConnectionBeanNames) throws Exception {
      Map<String, IDatabaseConnection> connectionMap = new HashMap<String, IDatabaseConnection>();
      for(String databaseConnectionBeanName : databaseConnectionBeanNames) {
         Object databaseConnection = testContext.getApplicationContext().getBean(databaseConnectionBeanName);
         if (databaseConnection instanceof DataSource) {
            databaseConnection = DatabaseDataSourceConnectionFactoryBean.newConnection((DataSource) databaseConnection);
         }
         Assert.isInstanceOf(IDatabaseConnection.class, databaseConnection);
         connectionMap.put(databaseConnectionBeanName, (IDatabaseConnection) databaseConnection);
      }


		testContext.setAttribute(CONNECTION_ATTRIBUTE, connectionMap);
	}

	private void prepareDataSetLoader(TestContext testContext, Class<? extends DataSetLoader> dataSetLoaderClass) {
		try {
			testContext.setAttribute(DATA_SET_LOADER_ATTRIBUTE, dataSetLoaderClass.newInstance());
		} catch (Exception e) {
			throw new IllegalArgumentException("Unable to create data set loader instance for " + dataSetLoaderClass, e);
		}
	}

	private void prepareDatabaseOperationLookup(TestContext testContext,
			Class<? extends DatabaseOperationLookup> databaseOperationLookupClass) {
		try {
			testContext.setAttribute(DATABASE_OPERATION_LOOKUP_ATTRIBUTE, databaseOperationLookupClass.newInstance());
		} catch (Exception e) {
			throw new IllegalArgumentException("Unable to create database operation lookup instance for "
					+ databaseOperationLookupClass, e);
		}
	}

	@Override
	public void beforeTestMethod(TestContext testContext) throws Exception {
		runner.beforeTestMethod(new DbUnitTestContextAdapter(testContext));
	}

	@Override
	public void afterTestMethod(TestContext testContext) throws Exception {
		runner.afterTestMethod(new DbUnitTestContextAdapter(testContext));
	}

	private static class DbUnitTestContextAdapter implements DbUnitTestContext {

		private TestContext testContext;

		public DbUnitTestContextAdapter(TestContext testContext) {
			this.testContext = testContext;
		}

		public Map<String, IDatabaseConnection> getConnectionsMap() {
			return (Map<String, IDatabaseConnection>) this.testContext.getAttribute(CONNECTION_ATTRIBUTE);
		}

		public DataSetLoader getDataSetLoader() {
			return (DataSetLoader) this.testContext.getAttribute(DATA_SET_LOADER_ATTRIBUTE);
		}

		public DatabaseOperationLookup getDatbaseOperationLookup() {
			return (DatabaseOperationLookup) this.testContext.getAttribute(DATABASE_OPERATION_LOOKUP_ATTRIBUTE);
		}

		public Class<?> getTestClass() {
			return this.testContext.getTestClass();
		}

		public Method getTestMethod() {
			return this.testContext.getTestMethod();
		}

		public Throwable getTestException() {
			return this.testContext.getTestException();
		}
	}
}
