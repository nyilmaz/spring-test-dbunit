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

import com.github.springtestdbunit.annotation.*;
import com.github.springtestdbunit.assertion.DatabaseAssertion;
import com.github.springtestdbunit.dataset.DataSetLoader;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.dataset.IDataSet;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.lang.annotation.Annotation;
import java.sql.SQLException;
import java.util.*;

/**
 * Internal delegate class used to run tests with support for {@link DatabaseSetup &#064;DatabaseSetup},
 * {@link DatabaseTearDown &#064;DatabaseTearDown} and {@link ExpectedDatabase &#064;ExpectedDatabase} annotations.
 * 
 * @author Phillip Webb
 * @author Mario Zagar
 */
class DbUnitRunner {

	private static final Log logger = LogFactory.getLog(DbUnitTestExecutionListener.class);

	/**
	 * Called before a test method is executed to perform any database setup.
	 * @param testContext The test context
	 * @throws Exception
	 */
	public void beforeTestMethod(DbUnitTestContext testContext) throws Exception {
		Collection<DatabaseSetup> annotations = getAnnotations(testContext, DatabaseSetup.class);

      for(DatabaseSetup annotation : annotations) {
         setupOrTeardown(testContext, true, AnnotationAttributes.get(Arrays.asList(annotation.connections())));
      }
	}

	/**
	 * Called after a test method is executed to perform any database teardown and to check expected results.
	 * @param testContext The test context
	 * @throws Exception
	 */
	public void afterTestMethod(DbUnitTestContext testContext) throws Exception {
		try {
			verifyExpected(testContext, getAnnotations(testContext, ExpectedDatabase.class));
			Collection<DatabaseTearDown> annotations = getAnnotations(testContext, DatabaseTearDown.class);
			try {
				setupOrTeardown(testContext, false, AnnotationAttributes.get(annotations));
			} catch (RuntimeException e) {
				if (testContext.getTestException() == null) {
					throw e;
				}
				if (logger.isWarnEnabled()) {
					logger.warn("Unable to throw database cleanup exception due to existing test error", e);
				}
			}
		} finally {
			closeConnections(testContext);
		}
	}

   private void closeConnections(DbUnitTestContext testContext) throws SQLException{
      Map<String, IDatabaseConnection> connectionMap = testContext.getConnectionsMap();
      for(IDatabaseConnection connection : connectionMap.values()) {
         connection.close();
      }
   }

	private <T extends Annotation> Collection<T> getAnnotations(DbUnitTestContext testContext, Class<T> annotationType) {
		List<T> annotations = new ArrayList<T>();
		addAnnotationToList(annotations, AnnotationUtils.findAnnotation(testContext.getTestClass(), annotationType));
		addAnnotationToList(annotations, AnnotationUtils.findAnnotation(testContext.getTestMethod(), annotationType));
		return annotations;
	}

	private <T extends Annotation> void addAnnotationToList(List<T> annotations, T annotation) {
		if (annotation != null) {
			annotations.add(annotation);
		}
	}

	private void verifyExpected(DbUnitTestContext testContext, Collection<ExpectedDatabase> annotations)
			throws Exception {
		if (testContext.getTestException() != null) {
			if (logger.isDebugEnabled()) {
				logger.debug("Skipping @DatabaseTest expectation due to test exception "
						+ testContext.getTestException().getClass());
			}
			return;
		}

      for(ExpectedDatabase annotation : annotations) {
         String connectionName = annotation.connection();
         IDatabaseConnection connection = testContext.getConnectionsMap().get(connectionName);
         IDataSet actualDataSet = connection.createDataSet();
         IDataSet expectedDataSet = loadDataset(testContext, annotation.value());
         if (expectedDataSet != null) {
            if (logger.isDebugEnabled()) {
               logger.debug("Veriftying @DatabaseTest expectation using " + annotation.value());
            }
            DatabaseAssertion assertion = annotation.assertionMode().getDatabaseAssertion();
            assertion.assertEquals(expectedDataSet, actualDataSet);
         }

      }

	}

	private IDataSet loadDataset(DbUnitTestContext testContext, String dataSetLocation) throws Exception {
		DataSetLoader dataSetLoader = testContext.getDataSetLoader();
		if (StringUtils.hasLength(dataSetLocation)) {
			IDataSet dataSet = dataSetLoader.loadDataSet(testContext.getTestClass(), dataSetLocation);
			Assert.notNull(dataSet,
					"Unable to load dataset from \"" + dataSetLocation + "\" using " + dataSetLoader.getClass());
			return dataSet;
		}
		return null;
	}

   private void setupOrTeardown(DbUnitTestContext testContext, boolean isSetup,
                                Collection<AnnotationAttributes> annotations) throws Exception {
      Map<String, IDatabaseConnection> connectionsMap = testContext.getConnectionsMap();
      DatabaseOperation lastOperation = null;
      IDatabaseConnection connection = connectionsMap.values().iterator().next();

      for(AnnotationAttributes annotation : annotations) {
         DatabaseOperation operation = annotation.getType();
         String connectionName = annotation.getConnectionName();
         for(String dataSetLocation : annotation.getValue()) {

            org.dbunit.operation.DatabaseOperation dbUnitDatabaseOperation =
               getDbUnitDatabaseOperation(testContext, operation, lastOperation);
            IDataSet dataSet = loadDataset(testContext, dataSetLocation);
            if(dataSet != null) {
               if(logger.isDebugEnabled()) {
                  logger.debug("Executing " + (isSetup ? "Setup" : "Teardown") + " of @DatabaseTest using "
                                  + operation + " on " + dataSetLocation);
               }
               if(StringUtils.hasText(connectionName)) {
                  connection = connectionsMap.get(connectionName);
                  if(logger.isDebugEnabled()) {
                     logger.info("Loading dataset " + dataSetLocation + " to connection:" + connectionName);
                  }
               }
               dbUnitDatabaseOperation.execute(connection, dataSet);
               lastOperation = operation;
            }
         }
         lastOperation = null;
      }
   }

	private org.dbunit.operation.DatabaseOperation getDbUnitDatabaseOperation(DbUnitTestContext testContext,
			DatabaseOperation operation, DatabaseOperation lastOperation) {
		if ((operation == DatabaseOperation.CLEAN_INSERT) && (lastOperation == DatabaseOperation.CLEAN_INSERT)) {
			operation = DatabaseOperation.INSERT;
		}
		org.dbunit.operation.DatabaseOperation databaseOperation = testContext.getDatbaseOperationLookup().get(
				operation);
		Assert.state(databaseOperation != null, "The databse operation " + operation + " is not supported");
		return databaseOperation;
	}

	private static class AnnotationAttributes {

		private DatabaseOperation type;

		private String[] value;

      private String connectionName;

      public AnnotationAttributes(Annotation annotation) {
			Assert.state((annotation instanceof DatabaseConnectionSetup) || (annotation instanceof DatabaseTearDown),
					"Only DatabaseSetup and DatabaseTearDown annotations are supported");
			Map<String, Object> attributes = AnnotationUtils.getAnnotationAttributes(annotation);
			this.type = (DatabaseOperation) attributes.get("type");
			this.value = (String[]) attributes.get("value");
         this.connectionName = (String) attributes.get("connectionName");
		}

		public DatabaseOperation getType() {
			return this.type;
		}

		public String[] getValue() {
			return this.value;
		}

		public static <T extends Annotation> Collection<AnnotationAttributes> get(Collection<T> annotations) {
			List<AnnotationAttributes> annotationAttributes = new ArrayList<AnnotationAttributes>();
			for (T annotation : annotations) {
				annotationAttributes.add(new AnnotationAttributes(annotation));
			}
			return annotationAttributes;
		}

      public String getConnectionName() {
         return connectionName;
      }
   }
}
